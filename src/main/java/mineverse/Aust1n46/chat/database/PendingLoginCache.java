package mineverse.Aust1n46.chat.database;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Small, time-limited handoff between asynchronous pre-login and join. */
final class PendingLoginCache {
    private record Entry(PlayerStateSnapshot snapshot, long createdAtMillis) {}

    private final int maximumEntries;
    private final Map<UUID, Entry> entries = new HashMap<>();

    PendingLoginCache(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        this.maximumEntries = maximumEntries;
    }

    synchronized boolean put(PlayerStateSnapshot snapshot, long createdAtMillis) {
        if (!entries.containsKey(snapshot.uuid()) && entries.size() >= maximumEntries) {
            return false;
        }
        entries.put(snapshot.uuid(), new Entry(snapshot, createdAtMillis));
        return true;
    }

    synchronized Optional<PlayerStateSnapshot> take(UUID uuid) {
        Entry entry = entries.remove(uuid);
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
    }

    synchronized int expireAtOrBefore(long cutoffMillis) {
        int sizeBefore = entries.size();
        entries.entrySet().removeIf(entry -> entry.getValue().createdAtMillis() <= cutoffMillis);
        return sizeBefore - entries.size();
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized void clear() {
        entries.clear();
    }
}
