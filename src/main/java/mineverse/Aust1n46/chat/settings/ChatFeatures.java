package mineverse.Aust1n46.chat.settings;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.filter.*;
import mineverse.Aust1n46.chat.logging.*;

/** Owns optional services and their lifecycle. Chat delivery never performs history I/O. */
public final class ChatFeatures implements AutoCloseable {
    private final MineverseChat plugin;
    private volatile PersonalFilterService filter;
    private ChatHistoryService history;
    private HistoryDashboard dashboard;
    private ChatSentryScope scope;
    private long reportedDrops, reportedFailures;
    private String realm;
    private boolean closed;

    public ChatFeatures(MineverseChat plugin) { this.plugin = plugin; }

    public void start() {
        var config = plugin.getConfig();
        realm = config.getString("chat-history.realm", "server");
        if (realm == null || realm.isBlank() || realm.length() > 64) realm = "server";
        if (config.getBoolean("chat-history.enabled", true)) {
            try {
                history = new ChatHistoryService(plugin.getDataFolder().toPath().resolve("chat-history.db"),
                        new ChatHistoryService.Settings(config.getInt("chat-history.queue-capacity",4096),
                                config.getInt("chat-history.batch-size",100),config.getInt("chat-history.retention-days",30),
                                config.getInt("chat-history.shutdown-millis",2000),
                                new HashSet<>(config.contains("chat-history.channels") ? config.getStringList("chat-history.channels") : java.util.List.of("Global")),
                                config.getBoolean("chat-history.log-private",false)));
                history.start();
                if (config.getBoolean("chat-history.dashboard.enabled",false)) {
                    Path credential = plugin.getDataFolder().toPath().resolve("dashboard-token.txt");
                    if (!Files.exists(credential)) {
                        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
                        Files.writeString(credential, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), java.nio.file.StandardOpenOption.CREATE_NEW);
                        try { Files.setPosixFilePermissions(credential, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
                        catch (UnsupportedOperationException ignored) { }
                    }
                    dashboard = new HistoryDashboard(history,new InetSocketAddress("127.0.0.1",config.getInt("chat-history.dashboard.port",8765)),Files.readString(credential).strip());
                    dashboard.start();
                    plugin.getLogger().info("Chat history dashboard: http://127.0.0.1:"+dashboard.port()+" (credential: dashboard-token.txt)");
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("Chat history/dashboard could not fully start: " + ex.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (history == null || closed) return;
            var status = history.status();
            if(status.dropped()>reportedDrops || status.failures()>reportedFailures) {
                plugin.getLogger().warning("Chat history health: dropped="+status.dropped()+", failures="+status.failures()+", queued="+status.queued()+". Chat delivery continues; history may have gaps.");
                reportedDrops=status.dropped();reportedFailures=status.failures();
            }
        },1200L,1200L);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int attempts;
            @Override public void run() {
                if(closed) {cancel();return;}
                var sentry=Bukkit.getPluginManager().getPlugin("ChatSentry");
                if(sentry!=null && sentry.isEnabled() && !ChatSentry567Detector.isReady(sentry) && ++attempts<200) return;
                cancel();
                if(!ChatSentry567Detector.isReady(sentry)) {
                    plugin.getLogger().warning("ChatSentry integration unavailable: supported rules did not finish loading; ordinary moderation remains active.");
                    if(config.getBoolean("personal-filter.enabled",true)) filter=new PersonalFilterService(
                            PersonalFilterService.unavailable("ChatSentry did not initialize; restart after correcting the provider"),
                            config.getStringList("personal-filter.additional-literals"),plugin.getLogger()::info);
                    return;
                }
                if(config.getBoolean("chatsentry-scope.enabled",true)) {
                    var policy=new VentureChatScopePolicy(privateChannels(plugin),
                            config.contains("chatsentry-scope.private-commands") ? config.getStringList("chatsentry-scope.private-commands") : java.util.List.of("message","vmessage","msg","tell","whisper","pm","reply","vreply","r"),
                            PartiesModeReader.connect(Bukkit.getPluginManager().getPlugin("Parties"),plugin.getLogger()::info));
                    scope=ChatSentryScope.install(sentry,policy,plugin.getLogger()::info);
                }
                if(config.getBoolean("personal-filter.enabled",true)) filter=new PersonalFilterService(ChatSentry567Detector.connect(sentry),
                        config.getStringList("personal-filter.additional-literals"),plugin.getLogger()::info);
            }
        }.runTaskTimer(plugin,1L,1L);
    }

    public String scopeStatus() { return scope==null ? "DISABLED" : scope.status(); }
    private static java.util.List<String> privateChannels(MineverseChat plugin) {
        return plugin.getConfig().contains("chatsentry-scope.private-channels") ? plugin.getConfig().getStringList("chatsentry-scope.private-channels") : java.util.List.of("Local","Group");
    }
    public static boolean legacyPrivateFilter(MineverseChat plugin) {
        return plugin.getConfig().getBoolean("personal-filter.legacy-outgoing-private-filter",false);
    }
    public static boolean legacyChannelFilter(MineverseChat plugin,String channel) {
        return legacyPrivateFilter(plugin) || privateChannels(plugin).stream().noneMatch(s->s.equalsIgnoreCase(channel));
    }
    public String filterStatus() { return filter == null ? "UNAVAILABLE: personal filter is disabled or starting" : filter.status(); }
    public FilterDecision evaluate(String message) { var current=filter; return current==null ? FilterDecision.UNAVAILABLE : current.evaluate(message); }
    public static FilterDecision evaluate(MineverseChat plugin,String message) {
        var features=plugin.getChatFeatures(); return features==null ? FilterDecision.UNAVAILABLE : features.evaluate(message);
    }
    public static boolean hide(FilterDecision decision,MineverseChatPlayer sender,MineverseChatPlayer recipient) {
        return recipient!=null && PersonalFilterService.shouldHide(decision,sender.getUUID(),recipient.getUUID(),recipient.hasPersonalFilter());
    }
    public static void record(MineverseChat plugin,MineverseChatPlayer sender,String channel,UUID recipient,String message,boolean privateMessage) {
        var features=plugin.getChatFeatures();
        if(features==null || features.history==null) return;
        try {
            features.history.record(new ChatHistoryService.Event(UUID.randomUUID(),System.currentTimeMillis(),features.realm,
                    channel,sender.getUUID(),sender.getName(),recipient,ChatColor.stripColor(message),privateMessage));
        } catch (IllegalArgumentException ex) {
            // Oversized/custom plugin messages cannot interrupt chat delivery.
        }
    }
    @Override public void close() {
        closed=true;
        ChatSettingsMenu.resetNameLookups();
        if(scope!=null) scope.close();
        if(dashboard!=null) dashboard.close();
        if(history!=null) {
            history.close();
            var status=history.status();
            if(status.dropped()>0) plugin.getLogger().warning("Chat history dropped "+status.dropped()+" messages due to queue/storage limits; inspect history health.");
        }
        filter=null;
    }
}
