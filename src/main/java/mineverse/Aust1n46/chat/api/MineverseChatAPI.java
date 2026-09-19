package mineverse.Aust1n46.chat.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.database.PlayerData;
import mineverse.Aust1n46.chat.database.PlayerStateSnapshot;

/**
 * API class for looking up wrapped {@link MineverseChatPlayer} objects from
 * {@link Player}, {@link UUID}, or {@link String} user names.
 *
 * @author Aust1n46
 */
public final class MineverseChatAPI {
    public static final int MAX_CACHED_OFFLINE_PLAYERS = 1024;

    private static final ConcurrentHashMap<UUID, MineverseChatPlayer> playerMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, UUID> namesMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, MineverseChatPlayer> onlinePlayerMap = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedDeque<UUID> offlinePlayerOrder = new ConcurrentLinkedDeque<>();
    private static List<String> networkPlayerNames = new ArrayList<String>();

    public static List<String> getNetworkPlayerNames() {
        return networkPlayerNames;
    }

    public static void clearNetworkPlayerNames() {
        networkPlayerNames.clear();
    }

    public static void addNetworkPlayerName(String name) {
        networkPlayerNames.add(name);
    }

    public static void addNameToMap(MineverseChatPlayer mcp) {
        namesMap.put(mcp.getName(), mcp.getUUID());
    }

    public static void removeNameFromMap(String name) {
        namesMap.remove(name);
    }

    public static void clearNameMap() {
        namesMap.clear();
    }

    @SuppressWarnings("deprecation")
    public static void addMineverseChatPlayerToMap(MineverseChatPlayer mcp) {
        playerMap.put(mcp.getUUID(), mcp);
        MineverseChat.players.add(mcp);
    }

    @SuppressWarnings("deprecation")
    public static void clearMineverseChatPlayerMap() {
        playerMap.clear();
        offlinePlayerOrder.clear();
        MineverseChat.players.clear();
    }

    public static Collection<MineverseChatPlayer> getMineverseChatPlayers() {
        return playerMap.values();
    }

    @SuppressWarnings("deprecation")
    public static void addMineverseChatOnlinePlayerToMap(MineverseChatPlayer mcp) {
        offlinePlayerOrder.remove(mcp.getUUID());
        onlinePlayerMap.put(mcp.getUUID(), mcp);
        MineverseChat.onlinePlayers.add(mcp);
    }

    @SuppressWarnings("deprecation")
    public static void removeMineverseChatOnlinePlayerToMap(MineverseChatPlayer mcp) {
        onlinePlayerMap.remove(mcp.getUUID());
        MineverseChat.onlinePlayers.remove(mcp);
        cacheOfflineMineverseChatPlayer(mcp);
    }

    @SuppressWarnings("deprecation")
    public static void clearOnlineMineverseChatPlayerMap() {
        onlinePlayerMap.clear();
        MineverseChat.onlinePlayers.clear();
    }

    public static Collection<MineverseChatPlayer> getOnlineMineverseChatPlayers() {
        return onlinePlayerMap.values();
    }

    /**
     * Get a MineverseChatPlayer wrapper from a Bukkit Player instance.
     *
     * @param player {@link Player} object.
     * @return {@link MineverseChatPlayer}
     */
    public static MineverseChatPlayer getMineverseChatPlayer(Player player) {
        return getMineverseChatPlayer(player.getUniqueId());
    }

    /**
     * Get a MineverseChatPlayer wrapper from a UUID.
     * <p>
     * Players who have never changed anything have no data file, so they are not
     * loaded on startup. Their name is still known to the server, so a wrapper with
     * the values a new player starts with is created for them on demand.
     *
     * @param uuid {@link UUID}.
     * @return {@link MineverseChatPlayer}
     */
    public static MineverseChatPlayer getMineverseChatPlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        MineverseChatPlayer mcp = getCachedMineverseChatPlayer(uuid);
        if (mcp != null) {
            return mcp;
        }
        Optional<MineverseChatPlayer> stored = PlayerData.loadPlayerBlocking(uuid);
        if (stored.isPresent()) {
            return cacheLoadedOfflinePlayer(stored.get());
        }
        return createDefaultMineverseChatPlayer(uuid, Bukkit.getOfflinePlayer(uuid).getName());
    }

    /**
     * Get a MineverseChatPlayer wrapper from a user name.
     *
     * @param name {@link String}.
     * @return {@link MineverseChatPlayer}
     */
    public static MineverseChatPlayer getMineverseChatPlayer(String name) {
        UUID uuid = namesMap.get(name);
        if (uuid == null) {
            Optional<MineverseChatPlayer> stored = PlayerData.loadPlayerBlocking(name);
            if (stored.isPresent()) {
                return cacheLoadedOfflinePlayer(stored.get());
            }
        }
        if (uuid == null) {
            uuid = getCachedUUID(name);
        }
        return getMineverseChatPlayer(uuid);
    }

    /**
     * Look up a player's UUID from the server's offline player cache.
     *
     * @param name {@link String}.
     * @return the cached {@link UUID}, or null when the server has never seen the name
     */
    private static UUID getCachedUUID(String name) {
        if (name == null) {
            return null;
        }
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached == null ? null : cached.getUniqueId();
        } catch (NoSuchMethodError error) {
            // Paper only, Spigot has no cache-only lookup
            return null;
        }
    }

    /**
     * Create and register a wrapper for a player who has no stored data.
     *
     * @param uuid {@link UUID}.
     * @param name the player's name, null when the server does not know it
     * @return {@link MineverseChatPlayer}, or null when the name cannot be resolved
     */
    private static MineverseChatPlayer createDefaultMineverseChatPlayer(UUID uuid, String name) {
        if (name == null) {
            return null;
        }
        MineverseChatPlayer mcp = new MineverseChatPlayer(uuid, name);
        return cacheLoadedOfflinePlayer(mcp);
    }

    private static MineverseChatPlayer cacheLoadedOfflinePlayer(MineverseChatPlayer mcp) {
        MineverseChatPlayer existing = playerMap.putIfAbsent(mcp.getUUID(), mcp);
        if (existing != null) {
            return existing;
        }
        addMineverseChatPlayerToMap(mcp);
        addNameToMap(mcp);
        cacheOfflineMineverseChatPlayer(mcp);
        return mcp;
    }

    /**
     * Return a wrapper only when it is already online or in the bounded recent
     * offline cache. This method never performs disk access or creates a wrapper.
     */
    public static MineverseChatPlayer getCachedMineverseChatPlayer(UUID uuid) {
        MineverseChatPlayer player = playerMap.get(uuid);
        if (player != null && !onlinePlayerMap.containsKey(uuid)) {
            offlinePlayerOrder.remove(uuid);
            offlinePlayerOrder.addLast(uuid);
        }
        return player;
    }

    /**
     * Keep recently used offline wrappers bounded so long-running servers do not
     * slowly rebuild the old all-players-in-memory behaviour.
     */
    public static void cacheOfflineMineverseChatPlayer(MineverseChatPlayer player) {
        UUID uuid = player.getUUID();
        offlinePlayerOrder.remove(uuid);
        offlinePlayerOrder.addLast(uuid);
        while (offlinePlayerOrder.size() > MAX_CACHED_OFFLINE_PLAYERS) {
            UUID evictedUuid = offlinePlayerOrder.pollFirst();
            if (evictedUuid == null || onlinePlayerMap.containsKey(evictedUuid)) continue;
            MineverseChatPlayer evicted = playerMap.remove(evictedUuid);
            if (evicted != null) {
                if (evicted.wasModified()) {
                    PlayerData.savePlayerData(evicted);
                }
                namesMap.remove(evicted.getName(), evictedUuid);
                MineverseChat.players.remove(evicted);
            }
        }
    }

    /**
     * Asynchronously read one stored player record without loading every player.
     */
    public static CompletableFuture<Optional<PlayerStateSnapshot>> getPlayerStateAsync(UUID uuid) {
        return PlayerData.findByUuidAsync(uuid);
    }

    /**
     * Asynchronously find one stored player by their last known name.
     */
    public static CompletableFuture<Optional<PlayerStateSnapshot>> getPlayerStateAsync(String name) {
        return PlayerData.findByNameAsync(name);
    }

    /**
     * Asynchronously find the stored players in a party using the SQLite index.
     */
    public static CompletableFuture<List<PlayerStateSnapshot>> getPartyPlayerStatesAsync(UUID party) {
        return PlayerData.findByPartyAsync(party);
    }

    /**
     * Get a MineverseChatPlayer wrapper from a Bukkit Player instance. Only checks
     * current online players. Much more efficient!
     *
     * @param player {@link Player} object.
     * @return {@link MineverseChatPlayer}
     */
    public static MineverseChatPlayer getOnlineMineverseChatPlayer(Player player) {
        return getOnlineMineverseChatPlayer(player.getUniqueId());
    }

    /**
     * Get a MineverseChatPlayer wrapper from a UUID. Only checks current online
     * players. Much more efficient!
     *
     * @param uuid {@link UUID}.
     * @return {@link MineverseChatPlayer}
     */
    public static MineverseChatPlayer getOnlineMineverseChatPlayer(UUID uuid) {
        return onlinePlayerMap.get(uuid);
    }

    /**
     * Get a MineverseChatPlayer wrapper from a user name. Only checks current
     * online players. Much more efficient!
     *
     * @param name {@link String}.
     * @return {@link MineverseChatPlayer}
     */
    public static MineverseChatPlayer getOnlineMineverseChatPlayer(String name) {
        return getOnlineMineverseChatPlayer(namesMap.get(name));
    }

}
