package mineverse.Aust1n46.chat.logging;

import java.nio.file.Path;
import org.sqlite.ProgressHandler;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Separate, bounded, best-effort chat history. Never retains Bukkit objects. */
public final class ChatHistoryService implements AutoCloseable {
    public record Event(UUID id, long timestamp, String realm, String channel, UUID senderId,
                        String senderName, UUID recipientId, String message, boolean privateMessage) {
        public Event {
            Objects.requireNonNull(id); Objects.requireNonNull(senderId);
            bounded(realm,64,false); bounded(channel,64,false); bounded(senderName,64,false); bounded(message,8192,true);
            if (timestamp < 0) throw new IllegalArgumentException("Invalid timestamp");
        }
    }
    public record Settings(int queueCapacity, int batchSize, int retentionDays, int shutdownMillis,
                           Set<String> allowedChannels, boolean logPrivate) {
        public Settings {
            if(queueCapacity<1 || queueCapacity>100000 || batchSize<1 || batchSize>1000 || retentionDays<1 || retentionDays>3650 || shutdownMillis<1 || shutdownMillis>30000)
                throw new IllegalArgumentException("Invalid history bounds");
            allowedChannels=Set.copyOf(allowedChannels);
        }
        public static Settings defaults() { return new Settings(4096,100,30,2000,Set.of("Global"),false); }
    }
    /** Offset is bounded; reset pagination when changing filters. Time values are epoch milliseconds. */
    public record Query(String realm,String channel,String player,String text,long from,long to,int limit,int offset) {
        public Query {
            for(String v: new String[]{realm,channel,player,text}) if(v!=null) bounded(v,256,true);
            if(from<0 || to<from || limit<1 || limit>100 || offset<0 || offset>10000) throw new IllegalArgumentException("Invalid search bounds");
        }
    }
    public record Status(long accepted,long persisted,long dropped,long failures,int queued,boolean running,String lastFailure) {}
    private final Path path;
    private final Settings settings;
    private final ArrayBlockingQueue<Event> queue;
    private final AtomicLong accepted=new AtomicLong(), persisted=new AtomicLong(), dropped=new AtomicLong(), failures=new AtomicLong();
    private final AtomicInteger pending=new AtomicInteger();
    private final Object gate=new Object();
    private volatile boolean running, closed;
    private volatile String lastFailure="";
    private Thread worker;

    public ChatHistoryService(Path path,Settings settings) {
        this.path=Objects.requireNonNull(path).toAbsolutePath(); this.settings=Objects.requireNonNull(settings);
        queue=new ArrayBlockingQueue<>(settings.queueCapacity());
    }
    public void start() throws SQLException {
        synchronized(gate) {
            if(closed || worker!=null) throw new IllegalStateException("History service already started or closed");
            try(Connection c=connect(); Statement s=c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS history (seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT UNIQUE NOT NULL,ts INTEGER NOT NULL,realm TEXT NOT NULL,channel TEXT NOT NULL,sender_id TEXT NOT NULL,sender_name TEXT NOT NULL,recipient_id TEXT,message TEXT NOT NULL,private INTEGER NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS history_time ON history(ts,seq)");
                s.execute("CREATE INDEX IF NOT EXISTS history_conversation ON history(realm,channel,ts,seq)");
                s.execute("CREATE INDEX IF NOT EXISTS history_sender ON history(sender_id,ts)");
            }
            running=true; worker=new Thread(this::run,"VentureChat-history"); worker.setDaemon(true); worker.start();
        }
    }
    /** Caller creates one stable ID per event and reuses it on forwarding/retry. False means not accepted. */
    public boolean record(Event event) {
        Objects.requireNonNull(event);
        if(!settings.allowedChannels().contains(event.channel()) || ((event.privateMessage() || event.recipientId()!=null) && !settings.logPrivate())) return false;
        synchronized(gate) {
            if(!running || closed) return false;
            if(!queue.offer(event)) { dropped.incrementAndGet();return false; }
            accepted.incrementAndGet(); return true;
        }
    }
    private Connection connect() throws SQLException {
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+path);
        try(Statement s=c.createStatement()) {s.execute("PRAGMA busy_timeout=100");s.execute("PRAGMA synchronous=NORMAL");}
        catch(SQLException e) {c.close();throw e;} return c;
    }
    private void run() {
        List<Event> batch=new ArrayList<>(settings.batchSize()); int retries=0;long purgeAt=System.nanoTime();
        try {
            while(running || !queue.isEmpty() || !batch.isEmpty()) {
                if(Thread.currentThread().isInterrupted()) break;
                if(batch.isEmpty()) {
                    Event first=queue.poll(100,TimeUnit.MILLISECONDS);
                    if(first!=null) {batch.add(first);queue.drainTo(batch,settings.batchSize()-1);pending.set(batch.size());}
                }
                try {
                    if(!batch.isEmpty()) {write(batch);persisted.addAndGet(batch.size());batch.clear();pending.set(0);retries=0;lastFailure="";}
                    if(System.nanoTime()>=purgeAt) {
                        int removed=purgeExpired();
                        // A full batch means more expired rows may remain. Catch up promptly,
                        // but always return to message intake/write before another bounded purge.
                        long intervalMillis=removed==1000 ? 250 : 60000;
                        purgeAt=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(intervalMillis);
                    }
                } catch(SQLException e) {
                    failures.incrementAndGet();lastFailure="SQLite error "+e.getErrorCode();
                    Thread.sleep(Math.min(5000,100L << Math.min(retries++,5)));
                }
            }
        } catch(InterruptedException e) {Thread.currentThread().interrupt();}
        finally {
            synchronized(gate) {running=false;dropped.addAndGet(batch.size()+queue.size());batch.clear();queue.clear();pending.set(0);}
        }
    }
    private void write(List<Event> batch) throws SQLException {
        try(Connection c=connect()) {
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO history(id,ts,realm,channel,sender_id,sender_name,recipient_id,message,private) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING")) {
                for(Event e:batch) {p.setString(1,e.id().toString());p.setLong(2,e.timestamp());p.setString(3,e.realm());p.setString(4,e.channel());p.setString(5,e.senderId().toString());p.setString(6,e.senderName());p.setString(7,e.recipientId()==null?null:e.recipientId().toString());p.setString(8,e.message());p.setInt(9,e.privateMessage()?1:0);p.addBatch();}
                p.executeBatch();c.commit();
            } catch(SQLException e) {try {c.rollback();} catch(SQLException ignored) {} throw e;}
        }
    }
    /** At most 1000 expired rows per maintenance pass. Existing free database pages are reused. */
    public int purgeExpired() throws SQLException {
        try(Connection c=connect();PreparedStatement p=c.prepareStatement("DELETE FROM history WHERE seq IN (SELECT seq FROM history WHERE ts < ? ORDER BY ts LIMIT 1000)")) {
            p.setLong(1,System.currentTimeMillis()-TimeUnit.DAYS.toMillis(settings.retentionDays()));return p.executeUpdate();
        }
    }
    public List<Event> search(Query q) throws SQLException {
        StringBuilder sql=new StringBuilder("SELECT * FROM history WHERE ts>=? AND ts<=?");List<Object> args=new ArrayList<>();args.add(q.from());args.add(q.to());
        if(q.realm()!=null && !q.realm().isBlank()) {sql.append(" AND realm=?");args.add(q.realm());}
        if(q.channel()!=null && !q.channel().isBlank()) {sql.append(" AND channel=?");args.add(q.channel());}
        if(q.player()!=null && !q.player().isBlank()) {sql.append(" AND (sender_name=? COLLATE NOCASE OR sender_id=? OR recipient_id=?)");args.add(q.player());args.add(q.player());args.add(q.player());}
        if(q.text()!=null && !q.text().isBlank()) {sql.append(" AND message LIKE ? ESCAPE '\\'");args.add("%"+q.text().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");}
        sql.append(" ORDER BY ts DESC,seq DESC LIMIT ? OFFSET ?");args.add(q.limit());args.add(q.offset());return select(sql.toString(),args);
    }
    public List<Event> context(UUID id,int radius) throws SQLException {
        if(radius<1 || radius>50) throw new IllegalArgumentException("Invalid context radius");
        List<Event> target=select("SELECT * FROM history WHERE id=?",List.of(id.toString()));if(target.isEmpty())return List.of();
        Event e=target.getFirst();
        String scope="realm=? AND channel=? AND private=?";List<Object> args=new ArrayList<>(List.of(e.realm(),e.channel(),e.privateMessage()?1:0));
        if(e.privateMessage()) {scope+=" AND ((sender_id=? AND recipient_id IS ?) OR (sender_id IS ? AND recipient_id=?))";args.add(e.senderId().toString());args.add(e.recipientId()==null?null:e.recipientId().toString());args.add(e.recipientId()==null?null:e.recipientId().toString());args.add(e.senderId().toString());}
        var before=new ArrayList<>(args);before.add(e.timestamp());before.add(e.timestamp());before.add(id.toString());before.add(radius+1);
        var after=new ArrayList<>(args);after.add(e.timestamp());after.add(e.timestamp());after.add(id.toString());after.add(radius);
        List<Event> result=new ArrayList<>(select("SELECT * FROM history WHERE "+scope+" AND (ts<? OR (ts=? AND seq<=(SELECT seq FROM history WHERE id=?))) ORDER BY ts DESC,seq DESC LIMIT ?",before));
        Collections.reverse(result);result.addAll(select("SELECT * FROM history WHERE "+scope+" AND (ts>? OR (ts=? AND seq>(SELECT seq FROM history WHERE id=?))) ORDER BY ts,seq LIMIT ?",after));return List.copyOf(result);
    }
    private List<Event> select(String sql,List<Object> args) throws SQLException {
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(sql)) {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            ProgressHandler.setHandler(c,1000,new ProgressHandler() {
                @Override protected int progress() {return Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline ? 1 : 0;}
            });
            for(int i=0;i<args.size();i++)p.setObject(i+1,args.get(i));
            try(ResultSet r=p.executeQuery()) {List<Event> out=new ArrayList<>();while(r.next())out.add(new Event(UUID.fromString(r.getString("id")),r.getLong("ts"),r.getString("realm"),r.getString("channel"),UUID.fromString(r.getString("sender_id")),r.getString("sender_name"),r.getString("recipient_id")==null?null:UUID.fromString(r.getString("recipient_id")),r.getString("message"),r.getInt("private")!=0));return List.copyOf(out);}
        }
    }
    public Status status() {return new Status(accepted.get(),persisted.get(),dropped.get(),failures.get(),queue.size()+pending.get(),running,lastFailure);}
    @Override public void close() {
        Thread t;
        synchronized(gate) {if(closed)return;closed=true;running=false;t=worker;}
        if(t==null)return;
        try {t.join(settings.shutdownMillis());if(t.isAlive())t.interrupt();}
        catch(InterruptedException e) {t.interrupt();Thread.currentThread().interrupt();}
    }
    private static void bounded(String value,int max,boolean empty) {if(value==null || value.length()>max || (!empty && value.isBlank()) || value.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid history field");}
}
