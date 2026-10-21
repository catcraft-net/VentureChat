package mineverse.Aust1n46.chat.integrations;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;

/** Optional EssentialsX public-API bridge. No command parsing or shared ignore-list mutation. */
public final class EssentialsMailBridge implements AutoCloseable {
    private final MineverseChat plugin;
    private final Plugin essentials;
    private final Binding binding;
    private final Listener listener = new Listener() {};
    private final Semaphore capacity = new Semaphore(128);
    private final Set<CompletableFuture<Boolean>> active = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Mail> replay = new ThreadLocal<>();
    private final AtomicLong lastWarning = new AtomicLong();
    private final IgnoreLookup lookup;
    private volatile boolean closed;

    private EssentialsMailBridge(MineverseChat plugin, Plugin essentials, Binding binding) {
        this.plugin=plugin; this.essentials=essentials; this.binding=binding;
        this.lookup=new IgnoreLookup(EssentialsMailBridge::cachedIgnores,
                id->MineverseChatAPI.getPlayerStateAsync(id).thenApply(state->state.map(s->s.ignores()).orElse(Set.of())),
                this::onMain);
    }

    public static EssentialsMailBridge install(MineverseChat plugin) {
        Plugin essentials=plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (essentials==null || !essentials.isEnabled()) return null;
        try {
            Binding binding=new Binding(essentials.getClass().getClassLoader(), essentials.getClass().getMethod("getMail").invoke(essentials));
            EssentialsMailBridge bridge=new EssentialsMailBridge(plugin,essentials,binding);
            plugin.getServer().getPluginManager().registerEvent(binding.eventType,bridge.listener,EventPriority.HIGHEST,
                    (listener,event)->bridge.onMail(event),plugin,true);
            plugin.getLogger().info("EssentialsX mail now respects VentureChat ignores.");
            return bridge;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("EssentialsX mail ignore integration unavailable: " + ex.getClass().getSimpleName());
            return null;
        }
    }

    private static Set<UUID> cachedIgnores(UUID recipient) {
        var player=MineverseChatAPI.getCachedMineverseChatPlayer(recipient);
        return player==null ? null : Set.copyOf(player.getIgnores());
    }

    private void onMain(Runnable action) {
        if (closed || !plugin.isEnabled() || !essentials.isEnabled()) throw new IllegalStateException("Mail integration stopped");
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin,action);
    }

    private void onMail(Event event) {
        if (((Cancellable)event).isCancelled()) return;
        try {
            Mail mail=binding.read(event);
            // Essentials console/legacy mail has no player identity to compare.
            if (mail.sender==null) return;
            if (closed) { ((Cancellable)event).setCancelled(true); return; }
            if (Bukkit.isPrimaryThread()) {
                Mail permit=replay.get();
                if (mail.sameDelivery(permit)) {
                    replay.remove(); // One delivery only; nested sends must run their own check.
                    Set<UUID> current=cachedIgnores(mail.recipientId);
                    if (current!=null && current.contains(mail.sender)) ((Cancellable)event).setCancelled(true);
                    return;
                }
                Set<UUID> current=cachedIgnores(mail.recipientId);
                if (current!=null) {
                    if (current.contains(mail.sender)) ((Cancellable)event).setCancelled(true);
                    return;
                }
                // The event cannot be suspended. Cancel it before any mailbox write, then
                // submit allowed mail through the public service after the background lookup.
                ((Cancellable)event).setCancelled(true);
                CompletableFuture<Boolean> decision=decide(mail);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                decision.whenComplete((blocked,error)->{
                    try {
                        onMain(()->{
                            if (error!=null || System.nanoTime()>deadline) { warn(); return; }
                            if (!blocked && (mail.expires==0 || mail.expires>System.currentTimeMillis())) {
                                replay.set(mail);
                                try { binding.send(mail); }
                                catch (ReflectiveOperationException | RuntimeException ex) { warn(); }
                                finally { replay.remove(); }
                            }
                        });
                    } catch (RuntimeException stopped) { /* Never replay after plugin shutdown. */ }
                });
            } else {
                // Essentials bulk mail already runs asynchronously. Waiting here keeps its
                // sequential batch bounded, without blocking the server or replaying the event.
                try {
                    if (decide(mail).get(5,TimeUnit.SECONDS)) ((Cancellable)event).setCancelled(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); ((Cancellable)event).setCancelled(true); warn();
                } catch (ExecutionException | TimeoutException ex) {
                    ((Cancellable)event).setCancelled(true); warn();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            ((Cancellable)event).setCancelled(true); warn();
        }
    }

    private CompletableFuture<Boolean> decide(Mail mail) {
        if (!capacity.tryAcquire()) return CompletableFuture.failedFuture(new IllegalStateException("Mail checks busy"));
        CompletableFuture<Boolean> result=new CompletableFuture<>();
        active.add(result);
        // A timeout does not release capacity while its actual storage request is pending.
        result.whenComplete((value,error)->{active.remove(result);capacity.release();});
        try { onMain(()->{
            if (closed) { result.completeExceptionally(new IllegalStateException("Stopped")); return; }
            lookup.blocked(mail.recipientId,mail.sender).whenComplete((value,error)->{
                if (error!=null) result.completeExceptionally(error); else result.complete(value);
            });
        }); } catch (RuntimeException ex) { result.completeExceptionally(ex); }
        return result;
    }

    private void warn() {
        long now=System.currentTimeMillis(), previous=lastWarning.get();
        if (now-previous>=60000 && lastWarning.compareAndSet(previous,now))
            plugin.getLogger().warning("EssentialsX mail could not verify a VentureChat ignore list; affected mail was not delivered. Check player storage/integration health and retry.");
    }

    @Override public void close() {
        closed=true; HandlerList.unregisterAll(listener);
        active.forEach(future->future.completeExceptionally(new IllegalStateException("Mail integration stopped")));
        replay.remove();
    }

    static final class IgnoreLookup {
        private final Function<UUID,Set<UUID>> cached;
        private final Function<UUID,CompletableFuture<Set<UUID>>> stored;
        private final Consumer<Runnable> main;
        IgnoreLookup(Function<UUID,Set<UUID>> cached, Function<UUID,CompletableFuture<Set<UUID>>> stored, Consumer<Runnable> main) {
            this.cached=cached;this.stored=stored;this.main=main;
        }
        CompletableFuture<Boolean> blocked(UUID recipient,UUID sender) {
            Set<UUID> current=cached.apply(recipient);
            if(current!=null) return CompletableFuture.completedFuture(current.contains(sender));
            CompletableFuture<Boolean> result=new CompletableFuture<>();
            try { stored.apply(recipient).whenComplete((saved,error)->{
                try { main.accept(()->{
                    if(error!=null) {result.completeExceptionally(error);return;}
                    Set<UUID> latest=cached.apply(recipient);
                    result.complete((latest!=null ? latest : saved).contains(sender));
                }); } catch(RuntimeException ex) {result.completeExceptionally(ex);}
            }); } catch(RuntimeException ex) {result.completeExceptionally(ex);}
            return result;
        }
    }

    record Mail(Object recipient, UUID recipientId, UUID sender, String senderName, String body, long expires) {
        boolean sameDelivery(Mail other) {
            return other!=null && recipient==other.recipient && Objects.equals(sender,other.sender)
                    && Objects.equals(body,other.body) && expires==other.expires;
        }
    }

    static final class Binding {
        final Class<? extends Event> eventType;
        final Class<?> senderType;
        final Object service;
        final Method recipient, message, uuid, senderId, senderName, body, expires, send;
        Binding(ClassLoader loader,Object service) throws ReflectiveOperationException {
            this.service=Objects.requireNonNull(service);
            eventType=Class.forName("net.essentialsx.api.v2.events.UserMailEvent",true,loader).asSubclass(Event.class);
            if(!Cancellable.class.isAssignableFrom(eventType)) throw new IllegalArgumentException("Non-cancellable mail API");
            Class<?> user=Class.forName("net.ess3.api.IUser",true,loader);
            Class<?> mail=Class.forName("net.essentialsx.api.v2.services.mail.MailMessage",true,loader);
            senderType=Class.forName("net.essentialsx.api.v2.services.mail.MailSender",true,loader);
            recipient=eventType.getMethod("getRecipient");message=eventType.getMethod("getMessage");uuid=user.getMethod("getUUID");
            senderId=mail.getMethod("getSenderUUID");senderName=mail.getMethod("getSenderUsername");body=mail.getMethod("getMessage");expires=mail.getMethod("getTimeExpire");
            send=Class.forName("net.essentialsx.api.v2.services.mail.MailService",true,loader).getMethod("sendMail",user,senderType,String.class,long.class);
        }
        Mail read(Event event) throws ReflectiveOperationException {
            Object target=recipient.invoke(event), content=message.invoke(event);
            return new Mail(target,(UUID)uuid.invoke(target),(UUID)senderId.invoke(content),(String)senderName.invoke(content),(String)body.invoke(content),(long)expires.invoke(content));
        }
        void send(Mail mail) throws ReflectiveOperationException {
            Object sender=Proxy.newProxyInstance(senderType.getClassLoader(),new Class<?>[]{senderType},(proxy,method,args)->switch(method.getName()) {
                case "getUUID" -> mail.sender; case "getName","toString" -> mail.senderName;
                case "hashCode" -> mail.sender.hashCode(); case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
            send.invoke(service,mail.recipient,sender,mail.body,mail.expires);
        }
    }
}
