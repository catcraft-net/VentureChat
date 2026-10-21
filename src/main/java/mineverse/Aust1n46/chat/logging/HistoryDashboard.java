package mineverse.Aust1n46.chat.logging;

import com.sun.net.httpserver.*;
import org.json.simple.JSONValue;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/** Small authenticated read-only HTTP service. Bind to loopback behind a TLS access proxy. */
public final class HistoryDashboard implements AutoCloseable {
    private final ChatHistoryService history;
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final byte[] credential;
    public HistoryDashboard(ChatHistoryService history,InetSocketAddress bind,String token) throws IOException {
        if(token==null || token.length()<32 || token.length()>256 || !token.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Dashboard token must be 32-256 URL-safe characters");
        this.history=Objects.requireNonNull(history);credential=("Bearer "+token).getBytes(StandardCharsets.UTF_8);
        server=HttpServer.create(bind,16);
        executor=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"VentureChat-dashboard");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);server.createContext("/",this::handle);
    }
    public void start() {server.start();}
    public int port() {return server.getAddress().getPort();}
    private void handle(HttpExchange x) {
        try {
            var h=x.getResponseHeaders();h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("Referrer-Policy","no-referrer");
            h.set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
            if(!"GET".equals(x.getRequestMethod())) {send(x,405,"text/plain","Method not allowed");return;}
            String path=x.getRequestURI().getPath();
            if(path.startsWith("/api/")) {
                String supplied=x.getRequestHeaders().getFirst("Authorization");
                if(supplied==null || supplied.length()>300 || !MessageDigest.isEqual(credential,supplied.getBytes(StandardCharsets.UTF_8))) {
                    h.set("WWW-Authenticate","Bearer");send(x,401,"application/json","{\"error\":\"Authentication required\"}");return;
                }
                Map<String,String> q=parse(x.getRequestURI().getRawQuery());Object data;
                switch(path) {
                    case "/api/messages" -> data=history.search(new ChatHistoryService.Query(q.get("realm"),q.get("channel"),q.get("player"),q.get("text"),number(q,"from",0),number(q,"to",Long.MAX_VALUE),integer(q,"limit",50),integer(q,"offset",0))).stream().map(HistoryDashboard::row).toList();
                    case "/api/context" -> data=history.context(UUID.fromString(q.getOrDefault("id","")),integer(q,"radius",20)).stream().map(HistoryDashboard::row).toList();
                    case "/api/status" -> {var s=history.status();data=Map.of("accepted",s.accepted(),"persisted",s.persisted(),"dropped",s.dropped(),"failures",s.failures(),"queued",s.queued(),"running",s.running(),"lastFailure",s.lastFailure());}
                    default -> {send(x,404,"text/plain","Not found");return;}
                }
                send(x,200,"application/json",JSONValue.toJSONString(data));return;
            }
            String file=switch(path) {case "/", "/index.html" -> "index.html";case "/app.js" -> "app.js";case "/style.css" -> "style.css";default -> null;};
            if(file==null) {send(x,404,"text/plain","Not found");return;}
            try(InputStream in=HistoryDashboard.class.getResourceAsStream("/dashboard/"+file)) {
                if(in==null) {send(x,404,"text/plain","Not found");return;}
                send(x,200,file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":"text/html",new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
        } catch(IllegalArgumentException e) {error(x,400,"Invalid search parameters");}
        catch(SQLException e) {error(x,503,"History temporarily unavailable");}
        catch(IOException e) { /* Disconnected clients cannot affect the chat writer. */ }
        catch(RuntimeException e) {error(x,500,"History request failed");}
        finally {x.close();}
    }
    private static Map<String,Object> row(ChatHistoryService.Event e) {
        var m=new LinkedHashMap<String,Object>();m.put("id",e.id().toString());m.put("timestamp",e.timestamp());m.put("realm",e.realm());m.put("channel",e.channel());m.put("senderId",e.senderId().toString());m.put("senderName",e.senderName());m.put("recipientId",e.recipientId()==null?null:e.recipientId().toString());m.put("message",e.message());m.put("private",e.privateMessage());return m;
    }
    private static Map<String,String> parse(String raw) {
        Map<String,String> q=new HashMap<>();if(raw==null)return q;
        if(raw.length()>4096)throw new IllegalArgumentException();
        for(String entry:raw.split("&")) {String[] pair=entry.split("=",2);String k=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);String v=pair.length==1?"":URLDecoder.decode(pair[1],StandardCharsets.UTF_8);if(q.put(k,v)!=null || q.size()>12)throw new IllegalArgumentException();}
        return q;
    }
    private static long number(Map<String,String> q,String key,long fallback) {return q.containsKey(key)?Long.parseLong(q.get(key)):fallback;}
    private static int integer(Map<String,String> q,String key,int fallback) {long n=number(q,key,fallback);if(n<Integer.MIN_VALUE || n>Integer.MAX_VALUE)throw new IllegalArgumentException();return (int)n;}
    private static void error(HttpExchange x,int status,String message) {try {send(x,status,"application/json",JSONValue.toJSONString(Map.of("error",message)));} catch(IOException ignored) {}}
    private static void send(HttpExchange x,int status,String type,String body) throws IOException {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
    @Override public void close() {server.stop(0);executor.shutdownNow();Arrays.fill(credential,(byte)0);}
}
