package mineverse.Aust1n46.chat.logging;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import java.sql.*;
import java.net.*;
import java.net.http.*;
import static org.junit.Assert.*;

public class ChatHistoryTest {
 @Rule public TemporaryFolder tmp = new TemporaryFolder();
 private ChatHistoryService.Event event(String channel, String text, boolean priv) {
  return new ChatHistoryService.Event(UUID.randomUUID(), System.currentTimeMillis(), "Blue", channel, UUID.randomUUID(), "Alice", null, text, priv);
 }
 private ChatHistoryService.Query query(String text) { return new ChatHistoryService.Query(null,null,null,text,0,Long.MAX_VALUE,100,0); }
 private void flushed(ChatHistoryService s) throws Exception { long end=System.nanoTime()+3_000_000_000L; while(s.status().persisted()<s.status().accepted() && System.nanoTime()<end) Thread.sleep(10); }
 @Test public void policyLiteralSearchDedupAndContext() throws Exception {
  try(var s=new ChatHistoryService(tmp.newFile().toPath(), ChatHistoryService.Settings.defaults())) {
   s.start(); assertFalse(s.record(event("Group","secret",false))); assertFalse(s.record(event("Global","secret",true)));
   assertFalse(s.record(new ChatHistoryService.Event(UUID.randomUUID(),System.currentTimeMillis(),"Blue","Global",UUID.randomUUID(),"Alice",UUID.randomUUID(),"recipient-bearing private content",false)));
   var e=event("Global","100%_ ' OR 1=1 -- <script>",false);
   assertTrue(s.record(e)); assertTrue(s.record(e)); assertTrue(s.record(event("Global","ordinary",false))); flushed(s);
   assertEquals(2,s.search(query(null)).size()); assertEquals(1,s.search(query("%_")).size()); assertEquals(0,s.search(query("' OR 1=1 -- nonsense")).size());
   assertEquals(2,s.context(e.id(),20).size());
  }
 }
 @Test public void malformedAndBounds() throws Exception {
  assertThrows(IllegalArgumentException.class, () -> new ChatHistoryService.Query(null,null,null,null,5,1,10,0));
  assertThrows(IllegalArgumentException.class, () -> new ChatHistoryService.Query(null,null,null,null,0,10,101,0));
  assertThrows(IllegalArgumentException.class, () -> event("Global", "a".repeat(8193),false));
 }
 @Test public void queueBoundAndDeadlineDuringDatabaseOutage() throws Exception {
  var path=tmp.newFile().toPath();
  var settings=new ChatHistoryService.Settings(2,1,30,150,Set.of("Global"),false);
  var s=new ChatHistoryService(path,settings); s.start();
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var st=c.createStatement()) {
   st.execute("BEGIN IMMEDIATE");
   for(int i=0;i<100;i++)s.record(event("Global","hello",false));
   Thread.sleep(350); assertTrue(s.status().dropped()>0); assertTrue(s.status().queued()<=3);
   long start=System.nanoTime();s.close(); assertTrue((System.nanoTime()-start)/1_000_000<1000);
   assertFalse(s.record(event("Global","after close",false)));st.execute("ROLLBACK");
  } finally {s.close();}
 }
 @Test public void retriesRecoverWithoutDuplicatesAndRetention() throws Exception {
  var path=tmp.newFile().toPath();
  try(var s=new ChatHistoryService(path,new ChatHistoryService.Settings(20,5,1,1000,Set.of("Global"),false))) {
   s.start();
   try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var st=c.createStatement()) {
    st.execute("BEGIN IMMEDIATE"); var e=event("Global","retry",false);s.record(e);s.record(e);
    Thread.sleep(500); assertTrue(s.status().failures()>0);st.execute("ROLLBACK");
   }
   flushed(s); assertEquals(1,s.search(query(null)).size());
   s.record(new ChatHistoryService.Event(UUID.randomUUID(),System.currentTimeMillis()-172800000,"Blue","Global",UUID.randomUUID(),"Alice",null,"expired",false));
   flushed(s);s.purgeExpired(); assertEquals(1,s.search(query(null)).size());
  }
 }
 @Test public void concurrentBurstAndRestartPreserveAcceptedEvents() throws Exception {
  var path=tmp.newFile().toPath();var s=new ChatHistoryService(path,new ChatHistoryService.Settings(100,20,30,3000,Set.of("Global"),false));s.start();
  var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
  try {
   var jobs=new ArrayList<java.util.concurrent.Future<?>>();
   for(int t=0;t<4;t++)jobs.add(pool.submit(()->{for(int i=0;i<500;i++)s.record(event("Global","burst",false));}));
   for(var job:jobs)job.get();flushed(s);s.close();
   assertEquals(2000,s.status().accepted()+s.status().dropped());
   assertEquals(s.status().accepted(),s.status().persisted());
   try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var st=c.createStatement();var rs=st.executeQuery("SELECT count(*) FROM history")) {rs.next();assertEquals(s.status().accepted(),rs.getLong(1));}
   try(var reopened=new ChatHistoryService(path,ChatHistoryService.Settings.defaults())) {reopened.start();assertFalse(reopened.search(query("burst")).isEmpty());}
  } finally {pool.shutdownNow();s.close();}
 }
 @Test public void retentionDeletesOnlyOneBoundedBatch() throws Exception {
  var path=tmp.newFile().toPath();
  try(var s=new ChatHistoryService(path,ChatHistoryService.Settings.defaults())) {
   s.start();Thread.sleep(150);
   try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var p=c.prepareStatement("INSERT INTO history(id,ts,realm,channel,sender_id,sender_name,message,private) VALUES(?,0,'Blue','Global',?,'Alice','old',0)")) {
    c.setAutoCommit(false);for(int i=0;i<1500;i++){p.setString(1,UUID.randomUUID().toString());p.setString(2,UUID.randomUUID().toString());p.addBatch();}p.executeBatch();c.commit();
   }
   assertEquals(1000,s.purgeExpired());assertEquals(500,s.purgeExpired());assertEquals(0,s.purgeExpired());
  }
 }
 @Test public void scheduledRetentionCatchesUpAcrossBatchesWithoutNewMessages() throws Exception {
  var path=tmp.newFile().toPath();
  try(var initial=new ChatHistoryService(path,ChatHistoryService.Settings.defaults())) {initial.start();}
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var p=c.prepareStatement("INSERT INTO history(id,ts,realm,channel,sender_id,sender_name,message,private) VALUES(?,0,'Blue','Global',?,'Alice','old',0)")) {
   c.setAutoCommit(false);for(int i=0;i<2501;i++){p.setString(1,UUID.randomUUID().toString());p.setString(2,UUID.randomUUID().toString());p.addBatch();}p.executeBatch();c.commit();
  }
  try(var s=new ChatHistoryService(path,ChatHistoryService.Settings.defaults())) {
   s.start();long remaining=2501;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
   while(remaining>0 && System.nanoTime()<deadline) {
    Thread.sleep(40);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var st=c.createStatement();var rs=st.executeQuery("SELECT count(*) FROM history WHERE ts=0")) {rs.next();remaining=rs.getLong(1);}
   }
   assertEquals("Maintenance must reschedule while expired backlog remains",0,remaining);
   assertTrue(s.record(event("Global","new message after catchup",false)));flushed(s);
   assertEquals(1,s.search(query(null)).size());assertEquals(0,s.status().failures());
  }
 }
 @Test public void privateContextExcludesOtherConversations() throws Exception {
  try(var s=new ChatHistoryService(tmp.newFile().toPath(),new ChatHistoryService.Settings(20,10,30,1000,Set.of("PM"),true))) {
   s.start();var alice=UUID.randomUUID();var bob=UUID.randomUUID();var carol=UUID.randomUUID();
   var e=new ChatHistoryService.Event(UUID.randomUUID(),System.currentTimeMillis(),"Blue","PM",alice,"Alice",bob,"a to b",true);
   s.record(e);s.record(new ChatHistoryService.Event(UUID.randomUUID(),System.currentTimeMillis()+1,"Blue","PM",bob,"Bob",alice,"b to a",true));
   s.record(new ChatHistoryService.Event(UUID.randomUUID(),System.currentTimeMillis()+2,"Blue","PM",alice,"Alice",carol,"a to c",true));flushed(s);
   assertEquals(2,s.context(e.id(),20).size());
  }
 }
 @Test public void dashboardRequiresAuthRejectsBadInputsAndServesSafeAssets() throws Exception {
  try(var s=new ChatHistoryService(tmp.newFile().toPath(),ChatHistoryService.Settings.defaults())) {
   s.start();s.record(event("Global","<img src=x onerror=alert(1)>",false));flushed(s);
   try(var d=new HistoryDashboard(s,new InetSocketAddress("127.0.0.1",0),"x".repeat(40))) {
    d.start();var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+d.port();
    assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(base+"/api/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    var good=HttpRequest.newBuilder(URI.create(base+"/api/messages")).header("Authorization","Bearer "+"x".repeat(40)).GET().build();
    var r=client.send(good,HttpResponse.BodyHandlers.ofString());assertEquals(200,r.statusCode());assertTrue(r.body().contains("onerror"));
    var bad=HttpRequest.newBuilder(URI.create(base+"/api/messages?limit=nope")).header("Authorization","Bearer "+"x".repeat(40)).GET().build();
    assertEquals(400,client.send(bad,HttpResponse.BodyHandlers.ofString()).statusCode());
    var js=client.send(HttpRequest.newBuilder(URI.create(base+"/app.js")).GET().build(),HttpResponse.BodyHandlers.ofString());
    assertEquals(200,js.statusCode());assertFalse(js.body().contains("innerHTML"));assertTrue(js.body().contains("textContent"));
   }
  }
 }
}
