package mineverse.Aust1n46.chat.integrations;

import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.mockito.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.*;
import mineverse.Aust1n46.chat.database.PlayerStateSnapshot;

/** Uses the real EssentialsX mail service and event, with an in-memory IUser mailbox. */
public class EssentialsMailExactJarTest {
    @Test public void actualServiceBlocksCachedIgnoredMailAndAllowsUnignoreAndConsole() throws Exception {
        try(var f=new Fixture()) {
            f.cached(Set.of(f.sender)); f.send("blocked",0); assertTrue(f.inbox.isEmpty());
            f.cached(Set.of()); f.send("allowed",0); assertEquals(1,f.inbox.size());
            f.cached(Set.of(f.sender)); f.sendAs(null,"console",0); assertEquals(2,f.inbox.size());
            assertEquals("console",f.body(f.inbox.getFirst()));
        }
    }
    @Test public void offlineMailWaitsThenReplaysExactlyOnceWithExpiry() throws Exception {
        try(var f=new Fixture()) {
            var pending=new CompletableFuture<Optional<PlayerStateSnapshot>>();
            f.api.when(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId)).thenReturn(pending);
            long expires=System.currentTimeMillis()+60000;
            f.send("queued",expires); assertTrue(f.inbox.isEmpty());
            pending.complete(Optional.empty());
            assertEquals(1,f.inbox.size()); assertEquals("queued",f.body(f.inbox.getFirst()));
            assertEquals(expires,f.messageType.getMethod("getTimeExpire").invoke(f.inbox.getFirst()));
            f.api.verify(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId),times(1));
        }
    }
    @Test public void offlineStoredIgnoreAndLookupFailureNeverWriteMail() throws Exception {
        try(var f=new Fixture()) {
            var state=mock(PlayerStateSnapshot.class); when(state.ignores()).thenReturn(Set.of(f.sender));
            f.api.when(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId)).thenReturn(CompletableFuture.completedFuture(Optional.of(state)));
            f.send("blocked offline",0); assertTrue(f.inbox.isEmpty());
            f.api.when(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("test outage")));
            f.send("outage",0); assertTrue(f.inbox.isEmpty());
        }
    }
    @Test public void shutdownDropsPendingReplayAndOtherPluginCancellationStillWins() throws Exception {
        try(var f=new Fixture()) {
            var pending=new CompletableFuture<Optional<PlayerStateSnapshot>>();
            f.api.when(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId)).thenReturn(pending);
            f.send("pending",0); f.bridge.close();pending.complete(Optional.empty());assertTrue(f.inbox.isEmpty());
        }
        try(var f=new Fixture()) {
            f.api.when(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            f.cancelOther=true;f.send("cancelled",0);assertTrue(f.inbox.isEmpty());
        }
    }
    @Test public void asyncBulkPathChecksIgnoreWithoutReplaying() throws Exception {
        try(var f=new Fixture()) {
            f.cached(Set.of(f.sender));f.primary=false;f.send("blocked bulk",0);assertTrue(f.inbox.isEmpty());
            f.cached(Set.of());f.send("allowed bulk",0);assertEquals(1,f.inbox.size());assertEquals(2,f.events);
        }
    }

    @Test public void pendingOfflineChecksAreBoundedAndCapacityReturnsAfterCompletion() throws Exception {
        try(var f=new Fixture()) {
            var pending=new CompletableFuture<Optional<PlayerStateSnapshot>>();
            f.api.when(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId)).thenReturn(pending);
            for(int i=0;i<129;i++) f.send("pending "+i,0);
            assertTrue(f.inbox.isEmpty());
            f.api.verify(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId),times(128));
            var state=mock(PlayerStateSnapshot.class);when(state.ignores()).thenReturn(Set.of(f.sender));
            pending.complete(Optional.of(state));
            f.send("capacity returned",0);
            f.api.verify(()->MineverseChatAPI.getPlayerStateAsync(f.recipientId),times(129));
            assertTrue(f.inbox.isEmpty());
        }
    }

    static final class Fixture implements AutoCloseable {
        final UUID sender=UUID.randomUUID(),recipientId=UUID.randomUUID();
        final URLClassLoader loader;
        final MockedStatic<Bukkit> bukkit;
        final MockedStatic<MineverseChatAPI> api;
        final List<Object> inbox=new ArrayList<>();
        final Class<?> messageType,senderType;
        final Object recipient,service;
        final Method send;
        final EssentialsMailBridge bridge;
        boolean primary=true,cancelOther; int events;
        Fixture() throws Exception {
            String supplied=System.getProperty("essentials.test.jar");Assume.assumeNotNull(supplied);
            bukkit=mockStatic(Bukkit.class);api=mockStatic(MineverseChatAPI.class);
            loader=new URLClassLoader(new java.net.URL[]{Path.of(supplied).toUri().toURL()},getClass().getClassLoader());
            var manager=mock(PluginManager.class);var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);
            when(server.getServicesManager()).thenReturn(mock(ServicesManager.class));
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::isPrimaryThread).thenAnswer(i->primary);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(i->{boolean before=primary;primary=true;try{((Runnable)i.getArgument(1)).run();}finally{primary=before;}return null;})
                    .when(scheduler).runTask(any(Plugin.class),any(Runnable.class));
            var essType=loader.loadClass("com.earth2me.essentials.IEssentials");
            Object ess=mock(essType,i->i.getMethod().getName().equals("getServer")?server:Answers.RETURNS_DEFAULTS.answer(i));
            service=loader.loadClass("com.earth2me.essentials.MailServiceImpl").getConstructor(essType).newInstance(ess);
            var userType=loader.loadClass("net.ess3.api.IUser");
            recipient=mock(userType,i->switch(i.getMethod().getName()) {
                case "getUUID" -> recipientId;
                case "getMailMessages" -> new ArrayList<>(inbox);
                case "setMailList" -> {inbox.clear();inbox.addAll(i.getArgument(0));yield null;}
                default -> Answers.RETURNS_DEFAULTS.answer(i);
            });
            messageType=loader.loadClass("net.essentialsx.api.v2.services.mail.MailMessage");
            senderType=loader.loadClass("net.essentialsx.api.v2.services.mail.MailSender");
            send=service.getClass().getMethod("sendMail",userType,senderType,String.class,long.class);
            var plugin=mock(MineverseChat.class);when(plugin.isEnabled()).thenReturn(true);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var provider=mock(Plugin.class);when(provider.isEnabled()).thenReturn(true);
            var constructor=EssentialsMailBridge.class.getDeclaredConstructor(MineverseChat.class,Plugin.class,EssentialsMailBridge.Binding.class);
            constructor.setAccessible(true);bridge=constructor.newInstance(plugin,provider,new EssentialsMailBridge.Binding(loader,service));
            var handler=EssentialsMailBridge.class.getDeclaredMethod("onMail",Event.class);handler.setAccessible(true);
            doAnswer(i->{Event event=i.getArgument(0);events++;handler.invoke(bridge,event);if(cancelOther)((Cancellable)event).setCancelled(true);return null;})
                    .when(manager).callEvent(any(Event.class));
        }
        void cached(Set<UUID> ignores) {
            var player=mock(MineverseChatPlayer.class);when(player.getIgnores()).thenReturn(ignores);
            api.when(()->MineverseChatAPI.getCachedMineverseChatPlayer(recipientId)).thenReturn(player);
        }
        void send(String body,long expires) throws Exception {sendAs(sender,body,expires);}
        void sendAs(UUID id,String body,long expires) throws Exception {
            Object source=Proxy.newProxyInstance(loader,new Class<?>[]{senderType},(p,m,a)->m.getName().equals("getUUID")?id:"Sender");
            send.invoke(service,recipient,source,body,expires);
        }
        String body(Object message) throws Exception {return (String)messageType.getMethod("getMessage").invoke(message);}
        public void close() throws Exception {bridge.close();api.close();bukkit.close();loader.close();}
    }
}
