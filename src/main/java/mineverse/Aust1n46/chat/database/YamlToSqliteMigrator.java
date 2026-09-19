package mineverse.Aust1n46.chat.database;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class YamlToSqliteMigrator {
    public enum FailurePoint { AFTER_DATABASE_VERIFICATION }

    @FunctionalInterface
    interface FaultInjector {
        void hit(FailurePoint point) throws Exception;
    }

    private record Skipped(Path source, String error) {}

    private final Path dataFolder;
    private final Path database;
    private final String defaultChannel;
    private final Set<String> autojoinChannels;
    private final LegacyYamlStateReader reader;
    private final FaultInjector faultInjector;

    public YamlToSqliteMigrator(Path dataFolder, Path database, String defaultChannel,
            Set<String> autojoinChannels, Set<String> validChannels) {
        this(dataFolder, database, defaultChannel, autojoinChannels, validChannels, point -> {});
    }

    YamlToSqliteMigrator(Path dataFolder, Path database, String defaultChannel,
            Set<String> autojoinChannels, Set<String> validChannels, FaultInjector faultInjector) {
        this.dataFolder = dataFolder.toAbsolutePath();
        this.database = database.toAbsolutePath();
        this.defaultChannel = defaultChannel;
        this.autojoinChannels = Set.copyOf(autojoinChannels);
        this.reader = new LegacyYamlStateReader(defaultChannel, autojoinChannels, validChannels);
        this.faultInjector = faultInjector;
    }

    public MigrationResult migrateIfNeeded() {
        if (Files.isRegularFile(database)) {
            return new MigrationResult(MigrationResult.Status.ALREADY_PRESENT, null, 0, 0, "SQLite database already exists");
        }

        Path backup = null;
        Path temporaryDatabase = database.resolveSibling(database.getFileName() + ".migrating");
        List<Path> sources;
        try {
            Files.createDirectories(dataFolder);
            sources = collectSources();
            if (sources.isEmpty()) {
                try (SqlitePlayerStateRepository repository = repository(database)) {
                    repository.initialize();
                }
                return new MigrationResult(MigrationResult.Status.CREATED_EMPTY, null, 0, 0, "Created empty SQLite database");
            }

            backup = VerifiedZipBackup.create(dataFolder, sources);
            cleanupTemporary(temporaryDatabase);

            List<PlayerStateSnapshot> migrated = new ArrayList<>();
            List<Skipped> skipped = new ArrayList<>();
            Path legacyPlayers = dataFolder.resolve("Players.yml").toAbsolutePath();
            for (Path source : sources) {
                try {
                    if (source.equals(legacyPlayers)) {
                        migrated.addAll(reader.readLegacyPlayersFile(source));
                    } else {
                        migrated.add(reader.readPlayerFile(source));
                    }
                } catch (Exception exception) {
                    skipped.add(new Skipped(source, exception.getMessage()));
                }
            }

            List<PlayerStateSnapshot> stored = migrated.stream()
                    .filter(state -> !state.isDefault(defaultChannel, autojoinChannels))
                    .sorted(Comparator.comparing(state -> state.uuid().toString()))
                    .toList();
            try (SqlitePlayerStateRepository repository = repository(temporaryDatabase)) {
                repository.initialize();
                repository.saveAll(migrated);
                if (repository.count() != stored.size()) {
                    throw new IllegalStateException("Migrated player count did not match");
                }
                if (!repository.canonicalChecksum().equals(checksum(stored))) {
                    throw new IllegalStateException("Migrated player checksum did not match");
                }
                repository.checkpoint();
            }

            faultInjector.hit(FailurePoint.AFTER_DATABASE_VERIFICATION);
            promote(temporaryDatabase, database);

            try (SqlitePlayerStateRepository repository = repository(database)) {
                repository.initialize();
                if (repository.count() != stored.size() || !repository.canonicalChecksum().equals(checksum(stored))) {
                    throw new IllegalStateException("Promoted database verification failed");
                }
            }
            if (!VerifiedZipBackup.verify(backup)) {
                throw new IllegalStateException("Backup verification failed before cleanup");
            }

            quarantine(skipped);
            for (Path source : sources) {
                Files.deleteIfExists(source);
            }
            Path playerData = dataFolder.resolve("PlayerData");
            if (Files.isDirectory(playerData)) {
                try (var entries = Files.list(playerData)) {
                    if (entries.findAny().isEmpty()) Files.deleteIfExists(playerData);
                }
            }
            writeReport(backup, migrated.size(), stored.size(), skipped);
            cleanupTemporary(temporaryDatabase);
            return new MigrationResult(MigrationResult.Status.MIGRATED, backup, stored.size(), skipped.size(),
                    "YAML migration completed and verified");
        } catch (Exception exception) {
            try {
                cleanupTemporary(temporaryDatabase);
            } catch (Exception ignored) {}
            return new MigrationResult(MigrationResult.Status.FAILED_USING_YAML, backup, 0, 0,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private SqlitePlayerStateRepository repository(Path path) {
        return new SqlitePlayerStateRepository(path, defaultChannel, autojoinChannels);
    }

    private List<Path> collectSources() throws Exception {
        List<Path> sources = new ArrayList<>();
        Path legacy = dataFolder.resolve("Players.yml");
        if (Files.isRegularFile(legacy)) sources.add(legacy.toAbsolutePath());
        Path playerData = dataFolder.resolve("PlayerData");
        if (Files.isDirectory(playerData)) {
            try (var paths = Files.list(playerData)) {
                paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".yml"))
                        .map(Path::toAbsolutePath).sorted().forEach(sources::add);
            }
        }
        return sources;
    }

    private void quarantine(List<Skipped> skipped) throws Exception {
        if (skipped.isEmpty()) return;
        Path quarantine = Files.createDirectories(dataFolder.resolve("migration-quarantine"));
        for (Skipped failure : skipped) {
            Path destination = quarantine.resolve(failure.source().getFileName());
            Files.copy(failure.source(), destination, StandardCopyOption.REPLACE_EXISTING);
            if (Files.mismatch(failure.source(), destination) != -1L) {
                throw new IllegalStateException("Quarantine copy verification failed for " + failure.source());
            }
        }
    }

    private void writeReport(Path backup, int parsed, int stored, List<Skipped> skipped) throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("VentureChat 4.0.0 player data migration\n");
        report.append("Backup: ").append(backup).append('\n');
        report.append("Parsed players: ").append(parsed).append('\n');
        report.append("Stored non-default players: ").append(stored).append('\n');
        report.append("Skipped files: ").append(skipped.size()).append('\n');
        for (Skipped failure : skipped) {
            report.append("- ").append(failure.source().getFileName()).append(": ").append(failure.error()).append('\n');
        }
        Files.writeString(dataFolder.resolve("migration-report.txt"), report, StandardCharsets.UTF_8);
    }

    private static String checksum(List<PlayerStateSnapshot> states) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (PlayerStateSnapshot state : states) {
            digest.update(PlayerStateJsonCodec.canonicalJson(state).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void promote(Path source, Path destination) throws Exception {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void cleanupTemporary(Path temporaryDatabase) throws Exception {
        Files.deleteIfExists(temporaryDatabase);
        Files.deleteIfExists(Path.of(temporaryDatabase + "-wal"));
        Files.deleteIfExists(Path.of(temporaryDatabase + "-shm"));
    }
}
