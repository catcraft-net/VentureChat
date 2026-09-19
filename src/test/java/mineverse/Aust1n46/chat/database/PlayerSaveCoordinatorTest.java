package mineverse.Aust1n46.chat.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlayerSaveCoordinatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void coalescesRapidUpdatesAndPendingStateWinsAQuickRejoin() throws Exception {
        BlockingRepository repository = new BlockingRepository();
        Path journal = temporaryFolder.newFile("recovery.json").toPath();
        PlayerSaveCoordinator coordinator = new PlayerSaveCoordinator(repository, journal);
        UUID uuid = UUID.randomUUID();

        coordinator.queue(state(uuid, 1L));
        assertTrue(repository.firstWriteStarted.await(2, TimeUnit.SECONDS));
        assertEquals(1L, coordinator.load(uuid).get(1, TimeUnit.SECONDS).orElseThrow().revision());
        coordinator.queue(state(uuid, 2L));
        coordinator.queue(state(uuid, 3L));

        assertEquals(3L, coordinator.load(uuid).get(1, TimeUnit.SECONDS).orElseThrow().revision());
        repository.releaseFirstWrite.countDown();
        assertTrue(coordinator.awaitIdle(Duration.ofSeconds(2)));
        coordinator.shutdown(Duration.ofSeconds(2));

        assertEquals(List.of(1L, 3L), repository.savedRevisions);
        assertFalse(Files.exists(journal));
    }

    @Test
    public void retriesThreeTimesThenWritesChecksummedRecoveryJournal() throws Exception {
        AlwaysFailingRepository repository = new AlwaysFailingRepository();
        Path journal = temporaryFolder.newFile("recovery.json").toPath();
        PlayerSaveCoordinator coordinator = new PlayerSaveCoordinator(repository, journal);
        PlayerStateSnapshot snapshot = state(UUID.randomUUID(), 8L);

        coordinator.queue(snapshot);
        assertTrue(coordinator.awaitIdle(Duration.ofSeconds(2)));
        coordinator.shutdown(Duration.ofSeconds(2));

        assertEquals(3, repository.attempts.get());
        assertEquals(List.of(snapshot), RecoveryJournal.read(journal));

        Files.writeString(journal, Files.readString(journal) + "tampered");
        boolean rejected = false;
        try {
            RecoveryJournal.read(journal);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test
    public void timedOutShutdownJournalsTheWriteCurrentlyInProgress() throws Exception {
        UninterruptibleRepository repository = new UninterruptibleRepository();
        Path journal = temporaryFolder.newFile("recovery-timeout.json").toPath();
        PlayerSaveCoordinator coordinator = new PlayerSaveCoordinator(repository, journal);
        PlayerStateSnapshot snapshot = state(UUID.randomUUID(), 9L);

        coordinator.queue(snapshot);
        assertTrue(repository.firstWriteStarted.await(2, TimeUnit.SECONDS));
        coordinator.shutdown(Duration.ofMillis(10));

        assertEquals(List.of(snapshot), RecoveryJournal.read(journal));
        repository.releaseFirstWrite.countDown();
    }

    private static PlayerStateSnapshot state(UUID uuid, long revision) {
        return new PlayerStateSnapshot(uuid, "Alice", "Staff", Set.of(), Set.of("Global", "Staff"), Map.of(),
                Set.of(), false, null, true, true, "Default", false, false, false, true, revision);
    }

    private static class BlockingRepository implements PlayerStateRepository {
        protected final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        protected final CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        protected final List<Long> savedRevisions = new ArrayList<>();

        @Override public void initialize() {}
        @Override public Optional<PlayerStateSnapshot> findByUuid(UUID uuid) { return Optional.empty(); }
        @Override public Optional<PlayerStateSnapshot> findByName(String name) { return Optional.empty(); }
        @Override public List<PlayerStateSnapshot> findByParty(UUID party) { return List.of(); }
        @Override public long count() { return 0; }
        @Override public void close() {}

        @Override
        public synchronized void save(PlayerStateSnapshot snapshot) throws Exception {
            if (savedRevisions.isEmpty()) {
                firstWriteStarted.countDown();
                releaseFirstWrite.await(2, TimeUnit.SECONDS);
            }
            savedRevisions.add(snapshot.revision());
        }
    }

    private static class AlwaysFailingRepository extends BlockingRepository {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public synchronized void save(PlayerStateSnapshot snapshot) {
            attempts.incrementAndGet();
            throw new IllegalStateException("disk unavailable");
        }
    }

    private static class UninterruptibleRepository extends BlockingRepository {
        @Override
        public synchronized void save(PlayerStateSnapshot snapshot) {
            firstWriteStarted.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    releaseFirstWrite.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            savedRevisions.add(snapshot.revision());
        }
    }
}
