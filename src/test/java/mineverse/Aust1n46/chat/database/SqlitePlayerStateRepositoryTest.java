package mineverse.Aust1n46.chat.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SqlitePlayerStateRepositoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripsOneCompactRowAndUsesIndexedLookups() throws Exception {
        Path database = temporaryFolder.newFile("players.db").toPath();
        UUID uuid = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        PlayerStateSnapshot snapshot = new PlayerStateSnapshot(
                uuid, "Alice", "Staff", Set.of(ignored), Set.of("Global", "Staff"),
                Map.of("Global", new PlayerStateSnapshot.MuteState(123L, "spam")),
                Set.of("/plugins"), true, party, false, false, "Default", true, true, true, false, 7L);

        try (SqlitePlayerStateRepository repository = new SqlitePlayerStateRepository(database, "Global", Set.of("Global"))) {
            repository.initialize();
            repository.save(snapshot);

            assertEquals(snapshot, repository.findByUuid(uuid).orElseThrow());
            assertEquals(uuid, repository.findByName("alice").orElseThrow().uuid());
            assertEquals(Set.of(snapshot), Set.copyOf(repository.findByParty(party)));
            assertEquals(1L, repository.count());
            assertTrue(repository.indexNames().contains("players_name_idx"));
            assertTrue(repository.indexNames().contains("players_party_idx"));
        }
    }

    @Test
    public void defaultStateDeletesItsRowAndOlderRevisionCannotOverwriteNewerState() throws Exception {
        Path database = temporaryFolder.newFile("players.db").toPath();
        UUID uuid = UUID.randomUUID();
        PlayerStateSnapshot newer = customized(uuid, "NewName", 5L);
        PlayerStateSnapshot older = customized(uuid, "OldName", 4L);

        try (SqlitePlayerStateRepository repository = new SqlitePlayerStateRepository(database, "Global", Set.of("Global"))) {
            repository.initialize();
            repository.save(newer);
            repository.save(older);
            assertEquals("NewName", repository.findByUuid(uuid).orElseThrow().name());

            repository.save(PlayerStateSnapshot.defaults(uuid, "NewName", "Global", Set.of("Global"), 6L));
            assertFalse(repository.findByUuid(uuid).isPresent());
            assertEquals(0L, repository.count());
        }
    }

    @Test
    public void collectionJsonIsCanonicalRegardlessOfInputOrder() {
        UUID uuid = UUID.randomUUID();
        PlayerStateSnapshot first = new PlayerStateSnapshot(uuid, "Alice", "Staff",
                Set.of(UUID.fromString("00000000-0000-0000-0000-000000000002"), UUID.fromString("00000000-0000-0000-0000-000000000001")),
                Set.of("Staff", "Global"), Map.of(), Set.of("b", "a"), false, null, true, true,
                "Default", false, false, false, true, 1L);
        PlayerStateSnapshot second = new PlayerStateSnapshot(uuid, "Alice", "Staff",
                Set.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000002")),
                Set.of("Global", "Staff"), Map.of(), Set.of("a", "b"), false, null, true, true,
                "Default", false, false, false, true, 1L);

        assertEquals(PlayerStateJsonCodec.canonicalJson(first), PlayerStateJsonCodec.canonicalJson(second));
    }

    @Test
    public void batchSaveWritesCustomizedPlayersAndOmitsDefaults() throws Exception {
        Path database = temporaryFolder.newFile("players.db").toPath();
        PlayerStateSnapshot first = customized(UUID.randomUUID(), "Alice", 1L);
        PlayerStateSnapshot second = customized(UUID.randomUUID(), "Bob", 1L);
        PlayerStateSnapshot defaults = PlayerStateSnapshot.defaults(
                UUID.randomUUID(), "Carol", "Global", Set.of("Global"), 1L);

        try (SqlitePlayerStateRepository repository = new SqlitePlayerStateRepository(database, "Global", Set.of("Global"))) {
            repository.initialize();
            repository.saveAll(List.of(first, defaults, second));

            assertEquals(2L, repository.count());
            assertEquals(first, repository.findByUuid(first.uuid()).orElseThrow());
            assertEquals(second, repository.findByUuid(second.uuid()).orElseThrow());
            assertFalse(repository.findByUuid(defaults.uuid()).isPresent());
        }
    }

    @Test
    public void permittedSubsetOfAutojoinChannelsIsStillDefaultState() {
        PlayerStateSnapshot state = new PlayerStateSnapshot(UUID.randomUUID(), "Alice", "Global",
                Set.of(), Set.of("Global"), Map.of(), Set.of(), false, null, true, true,
                "Default", false, false, false, true, 1L);

        assertTrue(state.isDefault("Global", Set.of("Global", "Staff")));
    }

    private static PlayerStateSnapshot customized(UUID uuid, String name, long revision) {
        return new PlayerStateSnapshot(uuid, name, "Staff", Set.of(), Set.of("Global", "Staff"), Map.of(), Set.of(),
                false, null, true, true, "Default", false, false, false, true, revision);
    }
}
