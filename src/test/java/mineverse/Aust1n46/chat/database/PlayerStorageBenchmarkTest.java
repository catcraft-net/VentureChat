package mineverse.Aust1n46.chat.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Run explicitly with -Dventurechat.benchmark=true. */
public class PlayerStorageBenchmarkTest {
    private static final int PLAYER_COUNT = 50_000;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void fiftyThousandPlayerDatabaseStartsWithoutBulkLoading() throws Exception {
        Assume.assumeTrue("benchmark is opt-in", Boolean.getBoolean("venturechat.benchmark"));
        Path database = temporaryFolder.newFile("players.db").toPath();
        List<PlayerStateSnapshot> states = new ArrayList<>(PLAYER_COUNT);
        UUID lookupUuid = null;
        for (int index = 0; index < PLAYER_COUNT; index++) {
            UUID uuid = new UUID(0L, index + 1L);
            if (index == PLAYER_COUNT - 1) lookupUuid = uuid;
            states.add(new PlayerStateSnapshot(uuid, "Player" + index, "Staff", Set.of(),
                    Set.of("Global", "Staff"), Map.of(), Set.of(), false, null, true, true,
                    "Default", false, false, false, true, 1L));
        }

        long importStart = System.nanoTime();
        try (SqlitePlayerStateRepository repository = repository(database)) {
            repository.initialize();
            repository.saveAll(states);
            repository.checkpoint();
        }
        long importMillis = elapsedMillis(importStart);

        long startupStart = System.nanoTime();
        try (SqlitePlayerStateRepository repository = repository(database)) {
            repository.initialize();
        }
        long startupMillis = elapsedMillis(startupStart);

        long lookupStart = System.nanoTime();
        try (SqlitePlayerStateRepository repository = repository(database)) {
            assertEquals("Player49999", repository.findByUuid(lookupUuid).orElseThrow().name());
            assertEquals(PLAYER_COUNT, repository.count());
        }
        long lookupMillis = elapsedMillis(lookupStart);

        long bytes = Files.size(database);
        System.out.printf("VENTURECHAT_BENCHMARK players=%d import_ms=%d startup_ms=%d lookup_and_count_ms=%d db_bytes=%d%n",
                PLAYER_COUNT, importMillis, startupMillis, lookupMillis, bytes);
        assertTrue("compact database should stay below 64 MiB", bytes < 64L * 1024L * 1024L);
    }

    private static SqlitePlayerStateRepository repository(Path database) {
        return new SqlitePlayerStateRepository(database, "Global", Set.of("Global"));
    }

    private static long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
