package mineverse.Aust1n46.chat.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class YamlToSqliteMigratorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void verifiesBackupAndDatabaseBeforeRemovingLooseFiles() throws Exception {
        Path dataFolder = temporaryFolder.newFolder("plugin").toPath();
        Path playerData = Files.createDirectories(dataFolder.resolve("PlayerData"));
        UUID customized = UUID.randomUUID();
        UUID defaults = UUID.randomUUID();
        Files.writeString(playerData.resolve(customized + ".yml"), yaml("Alice", "Staff", "Global,Staff", false));
        Files.writeString(playerData.resolve(defaults + ".yml"), yaml("Bob", "Global", "Global", true));
        Files.writeString(playerData.resolve("not-a-uuid.yml"), "name: Broken\n");
        Path database = dataFolder.resolve("venturechat.db");

        YamlToSqliteMigrator migrator = new YamlToSqliteMigrator(dataFolder, database, "Global",
                Set.of("Global"), Set.of("Global", "Staff"));
        MigrationResult result = migrator.migrateIfNeeded();

        assertEquals(MigrationResult.Status.MIGRATED, result.status());
        assertTrue(Files.isRegularFile(result.backup()));
        assertTrue(VerifiedZipBackup.verify(result.backup()));
        assertFalse(Files.exists(playerData.resolve(customized + ".yml")));
        assertFalse(Files.exists(playerData.resolve(defaults + ".yml")));
        assertFalse(Files.exists(playerData.resolve("not-a-uuid.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("migration-quarantine/not-a-uuid.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("migration-report.txt")));

        try (SqlitePlayerStateRepository repository = new SqlitePlayerStateRepository(database, "Global", Set.of("Global"))) {
            repository.initialize();
            assertEquals(1L, repository.count());
            assertEquals("Alice", repository.findByUuid(customized).orElseThrow().name());
            assertFalse(repository.findByUuid(defaults).isPresent());
        }
    }

    @Test
    public void wholeMigrationFailureLeavesYamlAndCanBeRetriedSafely() throws Exception {
        Path dataFolder = temporaryFolder.newFolder("plugin-retry").toPath();
        Path playerData = Files.createDirectories(dataFolder.resolve("PlayerData"));
        UUID uuid = UUID.randomUUID();
        Path yaml = playerData.resolve(uuid + ".yml");
        Files.writeString(yaml, yaml("Alice", "Staff", "Global,Staff", false));
        Path database = dataFolder.resolve("venturechat.db");

        YamlToSqliteMigrator failing = new YamlToSqliteMigrator(dataFolder, database, "Global",
                Set.of("Global"), Set.of("Global", "Staff"), point -> {
                    if (point == YamlToSqliteMigrator.FailurePoint.AFTER_DATABASE_VERIFICATION) {
                        throw new IllegalStateException("simulated interruption");
                    }
                });
        MigrationResult failed = failing.migrateIfNeeded();
        assertEquals(MigrationResult.Status.FAILED_USING_YAML, failed.status());
        assertTrue(Files.isRegularFile(yaml));
        assertFalse(Files.exists(database));
        assertTrue(VerifiedZipBackup.verify(failed.backup()));

        MigrationResult retried = new YamlToSqliteMigrator(dataFolder, database, "Global",
                Set.of("Global"), Set.of("Global", "Staff")).migrateIfNeeded();
        assertEquals(MigrationResult.Status.MIGRATED, retried.status());
        assertFalse(Files.exists(yaml));
        assertTrue(Files.isRegularFile(database));
    }

    private static String yaml(String name, String current, String listening, boolean defaultState) {
        return "name: " + name + "\n"
                + "current: " + current + "\n"
                + "ignores: ''\n"
                + "listen: '" + listening + "'\n"
                + "mutes: {}\n"
                + "blockedcommands: ''\n"
                + "host: false\n"
                + "party: ''\n"
                + "filter: true\n"
                + "notifications: true\n"
                + "spy: false\n"
                + "commandspy: false\n"
                + "rangedspy: false\n"
                + "messagetoggle: true\n";
    }
}
