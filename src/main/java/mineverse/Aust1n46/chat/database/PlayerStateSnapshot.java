package mineverse.Aust1n46.chat.database;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable, Bukkit-free player data passed to storage workers. */
public record PlayerStateSnapshot(
        UUID uuid,
        String name,
        String currentChannel,
        Set<UUID> ignores,
        Set<String> listening,
        Map<String, MuteState> mutes,
        Set<String> blockedCommands,
        boolean host,
        UUID party,
        boolean filter,
        boolean notifications,
        String jsonFormat,
        boolean spy,
        boolean commandSpy,
        boolean rangedSpy,
        boolean messageToggle,
        boolean personalFilter,
        long revision) {

    /** Compatibility constructor for pre-4.1 integrations; personal filtering defaults on. */
    public PlayerStateSnapshot(UUID uuid, String name, String currentChannel, Set<UUID> ignores,
            Set<String> listening, Map<String, MuteState> mutes, Set<String> blockedCommands,
            boolean host, UUID party, boolean filter, boolean notifications, String jsonFormat,
            boolean spy, boolean commandSpy, boolean rangedSpy, boolean messageToggle, long revision) {
        this(uuid, name, currentChannel, ignores, listening, mutes, blockedCommands, host, party,
                filter, notifications, jsonFormat, spy, commandSpy, rangedSpy, messageToggle, true, revision);
    }

    public record MuteState(long expiresAt, String reason) {
        public MuteState {
            reason = reason == null ? "" : reason;
        }
    }

    public PlayerStateSnapshot {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currentChannel, "currentChannel");
        ignores = Set.copyOf(ignores);
        listening = Set.copyOf(listening);
        mutes = Map.copyOf(mutes);
        blockedCommands = Set.copyOf(blockedCommands);
        jsonFormat = jsonFormat == null ? "Default" : jsonFormat;
    }

    public static PlayerStateSnapshot defaults(UUID uuid, String name, String defaultChannel,
            Set<String> autojoinChannels, long revision) {
        Set<String> listening = autojoinChannels.isEmpty() ? Set.of(defaultChannel) : Set.copyOf(autojoinChannels);
        return new PlayerStateSnapshot(uuid, name, defaultChannel, Set.of(), listening, Map.of(), Set.of(),
                false, null, true, true, "Default", false, false, false, true, revision);
    }

    public boolean isDefault(String defaultChannel, Set<String> autojoinChannels) {
        Set<String> expectedListening = autojoinChannels.isEmpty() ? Set.of(defaultChannel) : autojoinChannels;
        return currentChannel.equals(defaultChannel)
                && ignores.isEmpty()
                && expectedListening.containsAll(listening)
                && mutes.isEmpty()
                && blockedCommands.isEmpty()
                && !host
                && party == null
                && filter
                && notifications
                && "Default".equals(jsonFormat)
                && !spy
                && !commandSpy
                && !rangedSpy
                && messageToggle
                && personalFilter;
    }
}
