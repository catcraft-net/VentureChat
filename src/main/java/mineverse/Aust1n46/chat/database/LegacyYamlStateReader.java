package mineverse.Aust1n46.chat.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class LegacyYamlStateReader {
    private final String defaultChannel;
    private final Set<String> autojoinChannels;
    private final Set<String> validChannels;

    LegacyYamlStateReader(String defaultChannel, Set<String> autojoinChannels, Set<String> validChannels) {
        this.defaultChannel = defaultChannel;
        this.autojoinChannels = Set.copyOf(autojoinChannels);
        this.validChannels = Set.copyOf(validChannels);
    }

    PlayerStateSnapshot readPlayerFile(Path file) throws Exception {
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(".yml")) {
            throw new IllegalArgumentException("Not a YAML file: " + fileName);
        }
        UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 4));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return read(uuid, yaml, Math.max(1L, Files.getLastModifiedTime(file).toMillis()));
    }

    List<PlayerStateSnapshot> readLegacyPlayersFile(Path file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            throw new IllegalArgumentException("Players.yml has no players section");
        }
        List<PlayerStateSnapshot> result = new ArrayList<>();
        long revision = Math.max(1L, Files.getLastModifiedTime(file).toMillis());
        for (String uuidValue : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(uuidValue);
            if (section == null) continue;
            result.add(read(UUID.fromString(uuidValue), section, revision));
        }
        return result;
    }

    private PlayerStateSnapshot read(UUID uuid, ConfigurationSection yaml, long revision) {
        String name = yaml.getString("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing player name for " + uuid);
        }
        String current = yaml.getString("current", defaultChannel);
        if (!validChannels.contains(current)) current = defaultChannel;

        Set<UUID> ignores = new HashSet<>();
        for (String value : split(yaml.getString("ignores", ""))) {
            ignores.add(UUID.fromString(value));
        }

        Set<String> listening = new HashSet<>();
        for (String value : split(yaml.getString("listen", ""))) {
            if (validChannels.contains(value)) listening.add(value);
        }
        if (listening.isEmpty()) listening.addAll(autojoinChannels.isEmpty() ? Set.of(defaultChannel) : autojoinChannels);

        Map<String, PlayerStateSnapshot.MuteState> mutes = new HashMap<>();
        ConfigurationSection muteSection = yaml.getConfigurationSection("mutes");
        if (muteSection != null) {
            for (String channel : muteSection.getKeys(false)) {
                if (!validChannels.contains(channel)) continue;
                ConfigurationSection mute = muteSection.getConfigurationSection(channel);
                if (mute != null) {
                    mutes.put(channel, new PlayerStateSnapshot.MuteState(
                            mute.getLong("time", 0L), mute.getString("reason", "")));
                }
            }
        } else {
            for (String encoded : split(yaml.getString("mutes", ""))) {
                String[] parts = encoded.split(":", 2);
                if (parts.length == 2 && validChannels.contains(parts[0]) && !"null".equals(parts[1])) {
                    mutes.put(parts[0], new PlayerStateSnapshot.MuteState(Long.parseLong(parts[1]), ""));
                }
            }
        }

        Set<String> blockedCommands = new HashSet<>(split(yaml.getString("blockedcommands", "")));
        String partyValue = yaml.getString("party", "");
        UUID party = partyValue.isBlank() ? null : UUID.fromString(partyValue);
        return new PlayerStateSnapshot(uuid, name, current, ignores, listening, mutes, blockedCommands,
                yaml.getBoolean("host", false), party, yaml.getBoolean("filter", true),
                yaml.getBoolean("notifications", true), "Default", yaml.getBoolean("spy", false),
                yaml.getBoolean("commandspy", false), yaml.getBoolean("rangedspy", false),
                yaml.getBoolean("messagetoggle", true), revision);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }
}
