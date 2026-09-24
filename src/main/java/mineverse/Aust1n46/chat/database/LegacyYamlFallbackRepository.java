package mineverse.Aust1n46.chat.database;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Emergency compatibility store used only when SQLite migration cannot complete. */
final class LegacyYamlFallbackRepository implements PlayerStateRepository {
    private final Path dataFolder;
    private final Path playerDataFolder;
    private final String defaultChannel;
    private final Set<String> autojoinChannels;
    private final LegacyYamlStateReader reader;
    private final Map<UUID, PlayerStateSnapshot> states = new ConcurrentHashMap<>();

    LegacyYamlFallbackRepository(Path dataFolder, String defaultChannel, Set<String> autojoinChannels,
            Set<String> validChannels) {
        this.dataFolder = dataFolder;
        this.playerDataFolder = dataFolder.resolve("PlayerData");
        this.defaultChannel = defaultChannel;
        this.autojoinChannels = Set.copyOf(autojoinChannels);
        this.reader = new LegacyYamlStateReader(defaultChannel, autojoinChannels, validChannels);
    }

    @Override
    public void initialize() throws Exception {
        Files.createDirectories(playerDataFolder);
        Path legacy = dataFolder.resolve("Players.yml");
        if (Files.isRegularFile(legacy)) {
            for (PlayerStateSnapshot state : reader.readLegacyPlayersFile(legacy)) states.put(state.uuid(), state);
        }
        try (var paths = Files.list(playerDataFolder)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".yml")).toList()) {
                try {
                    PlayerStateSnapshot state = reader.readPlayerFile(path);
                    states.put(state.uuid(), state);
                } catch (Exception ignored) {
                    // Preserve unreadable files; migration reports them when SQLite is available again.
                }
            }
        }
    }

    @Override
    public Optional<PlayerStateSnapshot> findByUuid(UUID uuid) {
        return Optional.ofNullable(states.get(uuid));
    }

    @Override
    public Optional<PlayerStateSnapshot> findByName(String name) {
        return states.values().stream().filter(state -> state.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public List<PlayerStateSnapshot> findByParty(UUID party) {
        return states.values().stream().filter(state -> party.equals(state.party())).toList();
    }

    @Override
    public synchronized void save(PlayerStateSnapshot state) throws Exception {
        Path destination = playerDataFolder.resolve(state.uuid() + ".yml");
        if (state.isDefault(defaultChannel, autojoinChannels)) {
            states.remove(state.uuid());
            Files.deleteIfExists(destination);
            return;
        }
        PlayerStateSnapshot existing = states.get(state.uuid());
        if (existing != null && existing.revision() > state.revision()) return;
        states.put(state.uuid(), state);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", state.name());
        yaml.set("current", state.currentChannel());
        yaml.set("ignores", state.ignores().stream().map(UUID::toString).sorted().collect(Collectors.joining(",")));
        yaml.set("listen", state.listening().stream().sorted().collect(Collectors.joining(",")));
        ConfigurationSection mutes = yaml.createSection("mutes");
        state.mutes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ConfigurationSection mute = mutes.createSection(entry.getKey());
            mute.set("time", entry.getValue().expiresAt());
            mute.set("reason", entry.getValue().reason());
        });
        yaml.set("blockedcommands", state.blockedCommands().stream().sorted().collect(Collectors.joining(",")));
        yaml.set("host", state.host());
        yaml.set("party", state.party() == null ? "" : state.party().toString());
        yaml.set("filter", state.filter());
        yaml.set("notifications", state.notifications());
        yaml.set("spy", state.spy());
        yaml.set("commandspy", state.commandSpy());
        yaml.set("rangedspy", state.rangedSpy());
        yaml.set("messagetoggle", state.messageToggle());
        yaml.set("personalfilter", state.personalFilter());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        yaml.save(temporary.toFile());
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public long count() {
        return states.size();
    }

    @Override
    public void close() {}
}
