package mineverse.Aust1n46.chat.database;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One ordered background writer with per-player coalescing and recovery. */
public final class PlayerSaveCoordinator {
    private final PlayerStateRepository repository;
    private final Path recoveryJournal;
    private final ConcurrentHashMap<UUID, PlayerStateSnapshot> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerStateSnapshot> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerStateSnapshot> failed = new ConcurrentHashMap<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VentureChat-PlayerStorage");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean accepting = true;

    public PlayerSaveCoordinator(PlayerStateRepository repository, Path recoveryJournal) {
        this.repository = repository;
        this.recoveryJournal = recoveryJournal;
    }

    public void replayRecovery() throws Exception {
        List<PlayerStateSnapshot> recovered = RecoveryJournal.read(recoveryJournal);
        for (PlayerStateSnapshot state : recovered) {
            repository.save(state);
        }
        RecoveryJournal.clear(recoveryJournal);
    }

    public void queue(PlayerStateSnapshot state) {
        if (!accepting) {
            failed.merge(state.uuid(), state, PlayerSaveCoordinator::newer);
            return;
        }
        pending.merge(state.uuid(), state, PlayerSaveCoordinator::newer);
        scheduleDrain();
    }

    public CompletableFuture<Optional<PlayerStateSnapshot>> load(UUID uuid) {
        PlayerStateSnapshot latest = latest(uuid);
        if (latest != null) {
            return CompletableFuture.completedFuture(Optional.of(latest));
        }
        return CompletableFuture.supplyAsync(() -> {
            PlayerStateSnapshot latePending = latest(uuid);
            if (latePending != null) {
                return Optional.of(latePending);
            }
            try {
                return repository.findByUuid(uuid);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load player " + uuid, exception);
            }
        }, worker);
    }

    public CompletableFuture<Optional<PlayerStateSnapshot>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findByName(name);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load player " + name, exception);
            }
        }, worker);
    }

    public CompletableFuture<List<PlayerStateSnapshot>> findByParty(UUID party) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findByParty(party);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load party " + party, exception);
            }
        }, worker);
    }

    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (pending.isEmpty() && inFlight.isEmpty() && !drainScheduled.get()) {
                return true;
            }
            Thread.sleep(5L);
        }
        return pending.isEmpty() && inFlight.isEmpty() && !drainScheduled.get();
    }

    public void shutdown(Duration timeout) throws Exception {
        accepting = false;
        if (!pending.isEmpty()) {
            scheduleDrain();
        }
        worker.shutdown();
        if (!worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            worker.shutdownNow();
            worker.awaitTermination(250L, TimeUnit.MILLISECONDS);
        }
        List<PlayerStateSnapshot> unsaved = new ArrayList<>();
        pending.forEach((uuid, state) -> mergeInto(unsaved, state));
        inFlight.forEach((uuid, state) -> mergeInto(unsaved, state));
        failed.forEach((uuid, state) -> mergeInto(unsaved, state));
        RecoveryJournal.write(recoveryJournal, unsaved);
        repository.close();
    }

    private void scheduleDrain() {
        if (drainScheduled.compareAndSet(false, true)) {
            worker.execute(this::drain);
        }
    }

    private void drain() {
        try {
            while (true) {
                PlayerStateSnapshot state = takeOne();
                if (state == null) {
                    return;
                }
                inFlight.put(state.uuid(), state);
                boolean saved = false;
                try {
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        try {
                            repository.save(state);
                            saved = true;
                            break;
                        } catch (Exception ignored) {
                            // The latest immutable state is retained below after the final attempt.
                        }
                    }
                    if (saved) {
                        failed.computeIfPresent(state.uuid(), (uuid, previous) ->
                                previous.revision() <= state.revision() ? null : previous);
                    } else {
                        failed.merge(state.uuid(), state, PlayerSaveCoordinator::newer);
                    }
                } finally {
                    inFlight.remove(state.uuid(), state);
                }
            }
        } finally {
            drainScheduled.set(false);
            if (!pending.isEmpty() && accepting) {
                scheduleDrain();
            }
        }
    }

    private PlayerStateSnapshot takeOne() {
        for (var entry : pending.entrySet()) {
            if (pending.remove(entry.getKey(), entry.getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static PlayerStateSnapshot newer(PlayerStateSnapshot left, PlayerStateSnapshot right) {
        if (left == null) return right;
        if (right == null) return left;
        return right.revision() >= left.revision() ? right : left;
    }

    private PlayerStateSnapshot latest(UUID uuid) {
        return newer(newer(pending.get(uuid), inFlight.get(uuid)), failed.get(uuid));
    }

    private static void mergeInto(List<PlayerStateSnapshot> states, PlayerStateSnapshot candidate) {
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).uuid().equals(candidate.uuid())) {
                states.set(index, newer(states.get(index), candidate));
                return;
            }
        }
        states.add(candidate);
    }
}
