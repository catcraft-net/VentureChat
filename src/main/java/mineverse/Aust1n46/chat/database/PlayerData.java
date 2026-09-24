package mineverse.Aust1n46.chat.database;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.Bukkit;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;
import mineverse.Aust1n46.chat.command.mute.MuteContainer;
import mineverse.Aust1n46.chat.utilities.Format;

/**
 * Player storage facade. Bukkit state is captured on the server thread and only
 * immutable snapshots are passed to the storage worker.
 */
public final class PlayerData {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PENDING_LOGIN_TTL = Duration.ofMinutes(5);
    private static final int MAX_PENDING_LOGINS = 4096;
    private static final PendingLoginCache LOGIN_STATES = new PendingLoginCache(MAX_PENDING_LOGINS);
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();

    private static volatile PlayerSaveCoordinator coordinator;
    private static volatile MigrationResult migrationResult;
    private static volatile String configuredDefaultChannel;
    private static volatile Set<String> configuredAutojoinChannels = Set.of();

    private PlayerData() {}

    public static synchronized MigrationResult initialize(MineverseChat plugin) throws Exception {
        if (coordinator != null) {
            return migrationResult;
        }
        String defaultChannel = ChatChannel.getDefaultChannel().getName();
        Set<String> autojoinChannels = autojoinChannels();
        configuredDefaultChannel = defaultChannel;
        configuredAutojoinChannels = Set.copyOf(autojoinChannels);
        Set<String> validChannels = new HashSet<>();
        for (ChatChannel channel : ChatChannel.getChatChannels()) {
            validChannels.add(channel.getName());
        }

        Path dataFolder = plugin.getDataFolder().toPath();
        Path database = dataFolder.resolve("venturechat.db");
        migrationResult = new YamlToSqliteMigrator(dataFolder, database, defaultChannel, autojoinChannels, validChannels)
                .migrateIfNeeded();

        PlayerStateRepository repository;
        if (migrationResult.status() == MigrationResult.Status.FAILED_USING_YAML) {
            repository = new LegacyYamlFallbackRepository(dataFolder, defaultChannel, autojoinChannels, validChannels);
            Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll(
                    "&8[&eVentureChat&8]&c - SQLite migration could not complete; safely using the original YAML data."));
            Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll(
                    "&8[&eVentureChat&8]&c - " + migrationResult.detail()));
        } else {
            repository = new SqlitePlayerStateRepository(database, defaultChannel, autojoinChannels);
        }
        repository.initialize();

        coordinator = new PlayerSaveCoordinator(repository, dataFolder.resolve("player-storage-recovery.json"));
        coordinator.replayRecovery();
        return migrationResult;
    }

    public static void prepareLogin(UUID uuid, String name) throws Exception {
        PlayerSaveCoordinator storage = requireCoordinator();
        PlayerStateSnapshot state = storage.load(uuid).get()
                .orElseGet(() -> PlayerStateSnapshot.defaults(uuid, name,
                        configuredDefaultChannel, configuredAutojoinChannels, 0L));
        long now = System.currentTimeMillis();
        if (!LOGIN_STATES.put(state, now)) {
            expirePreparedLogins(now);
            if (!LOGIN_STATES.put(state, now)) {
                throw new IllegalStateException("Too many player logins are currently pending");
            }
        }
    }

    public static MineverseChatPlayer consumeLogin(UUID uuid, String currentName) {
        PlayerStateSnapshot state = LOGIN_STATES.take(uuid).orElse(null);
        if (state == null) {
            state = PlayerStateSnapshot.defaults(uuid, currentName,
                    configuredDefaultChannel, configuredAutojoinChannels, 0L);
        }
        if (!state.name().equals(currentName)) {
            state = new PlayerStateSnapshot(state.uuid(), currentName, state.currentChannel(), state.ignores(),
                    state.listening(), state.mutes(), state.blockedCommands(), state.host(), state.party(),
                    state.filter(), state.notifications(), state.jsonFormat(), state.spy(), state.commandSpy(),
                    state.rangedSpy(), state.messageToggle(), state.personalFilter(), state.revision() + 1L);
        }
        return toPlayer(state);
    }

    public static void expirePreparedLogins() {
        expirePreparedLogins(System.currentTimeMillis());
    }

    static int expirePreparedLogins(long nowMillis) {
        return LOGIN_STATES.expireAtOrBefore(nowMillis - PENDING_LOGIN_TTL.toMillis());
    }

    public static void savePlayerData(MineverseChatPlayer player) {
        if (player == null || coordinator == null) {
            return;
        }
        DIRTY_PLAYERS.remove(player.getUUID());
        coordinator.queue(snapshot(player));
        player.setModified(false);
    }

    public static void markDirty(MineverseChatPlayer player) {
        if (coordinator != null && player != null) {
            DIRTY_PLAYERS.add(player.getUUID());
        }
    }

    /**
     * Queues only currently online players. Historical players are never scanned.
     */
    @Deprecated
    public static void savePlayerData() {
        flushDirtyPlayers();
    }

    public static void flushDirtyPlayers() {
        PlayerSaveCoordinator storage = coordinator;
        if (storage == null) return;
        flushDirtyPlayers(storage, DIRTY_PLAYERS, MineverseChatAPI::getCachedMineverseChatPlayer);
    }

    /** @deprecated use {@link #flushDirtyPlayers()} */
    @Deprecated
    public static void flushDirtyOnlinePlayers() {
        flushDirtyPlayers();
    }

    static void flushDirtyPlayers(PlayerSaveCoordinator storage, Set<UUID> dirtyPlayers,
            Function<UUID, MineverseChatPlayer> resolver) {
        for (UUID uuid : Set.copyOf(dirtyPlayers)) {
            if (!dirtyPlayers.remove(uuid)) continue;
            MineverseChatPlayer player = resolver.apply(uuid);
            if (player != null && player.wasModified()) {
                storage.queue(snapshot(player));
                player.setModified(false);
            }
        }
    }

    /**
     * Bulk startup loading was removed in 4.0. Players are loaded one at a time
     * during asynchronous pre-login.
     */
    @Deprecated
    public static void loadPlayerData() {
        // Intentionally empty.
    }

    /**
     * Legacy YAML is migrated by initialize() before players can join.
     */
    @Deprecated
    public static void loadLegacyPlayerData() {
        // Intentionally empty.
    }

    public static CompletableFuture<Optional<PlayerStateSnapshot>> findByUuidAsync(UUID uuid) {
        return requireCoordinator().load(uuid);
    }

    public static CompletableFuture<Optional<PlayerStateSnapshot>> findByNameAsync(String name) {
        return requireCoordinator().findByName(name);
    }

    public static CompletableFuture<java.util.List<PlayerStateSnapshot>> findByPartyAsync(UUID party) {
        return requireCoordinator().findByParty(party);
    }

    /**
     * Compatibility path for older synchronous API calls. It waits for one
     * background-worker lookup and never scans all stored players.
     */
    public static Optional<MineverseChatPlayer> loadPlayerBlocking(UUID uuid) {
        PlayerSaveCoordinator storage = coordinator;
        if (storage == null) return Optional.empty();
        try {
            return storage.load(uuid).join().map(PlayerData::toPlayer);
        } catch (CompletionException exception) {
            throw new IllegalStateException("Unable to load stored player " + uuid, exception.getCause());
        }
    }

    /**
     * Compatibility path for commands that address an offline player by name.
     */
    public static Optional<MineverseChatPlayer> loadPlayerBlocking(String name) {
        PlayerSaveCoordinator storage = coordinator;
        if (storage == null) return Optional.empty();
        try {
            return storage.findByName(name).join().map(PlayerData::toPlayer);
        } catch (CompletionException exception) {
            throw new IllegalStateException("Unable to load stored player " + name, exception.getCause());
        }
    }

    public static synchronized void shutdown() {
        PlayerSaveCoordinator storage = coordinator;
        if (storage == null) return;
        flushDirtyPlayers();
        for (MineverseChatPlayer player : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
            savePlayerData(player);
        }
        try {
            storage.shutdown(SHUTDOWN_TIMEOUT);
        } catch (Exception exception) {
            Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll(
                    "&8[&eVentureChat&8]&c - Player storage shutdown required recovery: " + exception.getMessage()));
        } finally {
            coordinator = null;
            LOGIN_STATES.clear();
            DIRTY_PLAYERS.clear();
        }
    }

    public static MigrationResult getMigrationResult() {
        return migrationResult;
    }

    static PlayerStateSnapshot snapshot(MineverseChatPlayer player) {
        Map<String, PlayerStateSnapshot.MuteState> mutes = new HashMap<>();
        for (MuteContainer mute : player.getMutes()) {
            mutes.put(mute.getChannel(), new PlayerStateSnapshot.MuteState(mute.getDuration(), mute.getReason()));
        }
        return new PlayerStateSnapshot(
                player.getUUID(),
                player.getName(),
                player.getCurrentChannel().getName(),
                new HashSet<>(player.getIgnores()),
                new HashSet<>(player.getListening()),
                mutes,
                new HashSet<>(player.getBlockedCommands()),
                player.isHost(),
                player.getParty(),
                player.hasFilter(),
                player.hasNotifications(),
                player.getJsonFormat(),
                player.isSpy(),
                player.hasCommandSpy(),
                player.getRangedSpy(),
                player.getMessageToggle(),
                player.hasPersonalFilter(),
                player.getStorageRevision());
    }

    static MineverseChatPlayer toPlayer(PlayerStateSnapshot state) {
        ChatChannel current = ChatChannel.isChannel(state.currentChannel())
                ? ChatChannel.getChannel(state.currentChannel())
                : ChatChannel.getDefaultChannel();
        Set<String> listening = new HashSet<>();
        for (String channel : state.listening()) {
            if (ChatChannel.isChannel(channel)) listening.add(ChatChannel.getChannel(channel).getName());
        }
        if (listening.isEmpty()) listening.addAll(autojoinChannels());

        HashMap<String, MuteContainer> mutes = new HashMap<>();
        state.mutes().forEach((channel, mute) -> {
            if (ChatChannel.isChannel(channel)) {
                mutes.put(channel, new MuteContainer(channel, mute.expiresAt(), mute.reason()));
            }
        });
        MineverseChatPlayer player = new MineverseChatPlayer(state.uuid(), state.name(), current,
                new HashSet<>(state.ignores()), listening, mutes, new HashSet<>(state.blockedCommands()),
                state.host(), state.party(), state.filter(), state.notifications(), state.jsonFormat(),
                state.spy(), state.commandSpy(), state.rangedSpy(), state.messageToggle());
        player.setPersonalFilter(state.personalFilter());
        player.setStorageRevision(state.revision());
        player.setModified(false);
        return player;
    }

    static boolean shouldStorePlayerData(MineverseChatPlayer player, boolean ignoredExistingFile) {
        return !isDefaultPlayerState(player);
    }

    static boolean isDefaultPlayerState(MineverseChatPlayer player) {
        if (player == null || player.getCurrentChannel() == null || player.getIgnores() == null
                || player.getMutes() == null || player.getBlockedCommands() == null || player.getListening() == null
                || ChatChannel.getDefaultChannel() == null) {
            return false;
        }
        return player.getCurrentChannel().getName().equals(ChatChannel.getDefaultChannel().getName())
                && player.getIgnores().isEmpty()
                && player.getMutes().isEmpty()
                && player.getBlockedCommands().isEmpty()
                && !player.isHost()
                && !player.hasParty()
                && player.hasPersonalFilter()
                && player.hasFilter()
                && player.hasNotifications()
                && !player.isSpy()
                && !player.hasCommandSpy()
                && !player.getRangedSpy()
                && player.getMessageToggle()
                && autojoinChannels().containsAll(player.getListening());
    }

    private static Set<String> autojoinChannels() {
        Set<String> channels = new HashSet<>();
        for (ChatChannel channel : ChatChannel.getAutojoinList()) {
            channels.add(channel.getName());
        }
        if (channels.isEmpty() && ChatChannel.getDefaultChannel() != null) {
            channels.add(ChatChannel.getDefaultChannel().getName());
        }
        return channels;
    }

    private static PlayerSaveCoordinator requireCoordinator() {
        PlayerSaveCoordinator storage = coordinator;
        if (storage == null) throw new IllegalStateException("Player storage has not been initialized");
        return storage;
    }
}
