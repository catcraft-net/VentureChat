package mineverse.Aust1n46.chat.filter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Optional actual-state reader for the inspected Parties API; never guesses from command toggles. */
public final class PartiesModeReader implements Predicate<Player> {
    private final Plugin plugin;
    private final Object manager;
    private final Method cacheGetter;
    private final Method chatMode;
    private final Method partyId;
    private final Consumer<String> diagnostics;
    private volatile boolean failed;

    private PartiesModeReader(Plugin plugin, Object manager, Method cacheGetter, Method chatMode, Method partyId, Consumer<String> diagnostics) {
        this.plugin = plugin; this.manager = manager; this.cacheGetter = cacheGetter;
        this.chatMode = chatMode; this.partyId = partyId; this.diagnostics = diagnostics;
    }
    public static Predicate<Player> connect(Plugin plugin, Consumer<String> diagnostics) {
        if (plugin == null || !plugin.isEnabled()) return player -> false;
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("com.alessiodp.parties.api.Parties", true, loader);
            Object api = apiClass.getMethod("getApi").invoke(null);
            // getPartyPlayer may hit the database when uncached. Read the actual online cache instead.
            Field owner = api.getClass().getDeclaredField("plugin");
            owner.setAccessible(true);
            Object core = owner.get(api);
            Object manager = core.getClass().getMethod("getPlayerManager").invoke(core);
            Class<?> playerType = Class.forName("com.alessiodp.parties.common.players.objects.PartyPlayerImpl", false, loader);
            Method cacheGetter = manager.getClass().getMethod("getCachePlayers");
            Method mode = playerType.getMethod("isChatParty"), partyId = playerType.getMethod("getPartyId");
            diagnostics.accept("Parties actual chat-mode reader available; this Parties version has no per-recipient delivery hook, so personal filtering does not cover its messages");
            return new PartiesModeReader(plugin, manager, cacheGetter, mode, partyId, diagnostics);
        } catch (Exception | LinkageError ex) {
            diagnostics.accept("UNAVAILABLE: Parties mode reader; native party routes retain normal ChatSentry moderation (" + ex.getClass().getSimpleName() + ")");
            return player -> false;
        }
    }
    @Override public boolean test(Player player) {
        if (failed || !plugin.isEnabled()) return false;
        try {
            Map<?, ?> cache = (Map<?, ?>) cacheGetter.invoke(manager);
            Object state = cache.get(player.getUniqueId());
            return state != null && partyId.invoke(state) instanceof UUID && Boolean.TRUE.equals(chatMode.invoke(state));
        } catch (Exception | LinkageError ex) {
            failed = true;
            diagnostics.accept("UNAVAILABLE: Parties mode reader failed; restart to reconnect");
            return false;
        }
    }
}
