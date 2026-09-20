package mineverse.Aust1n46.chat.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.Test;

public class PendingLoginCacheTest {
    @Test
    public void takingPreparedLoginRemovesIt() {
        PendingLoginCache cache = new PendingLoginCache(4);
        PlayerStateSnapshot snapshot = state(UUID.randomUUID(), "Joining");

        assertTrue(cache.put(snapshot, 100L));
        assertEquals(snapshot, cache.take(snapshot.uuid()).orElseThrow());
        assertEquals(0, cache.size());
    }

    @Test
    public void expiryRemovesOnlyAbandonedLogins() {
        PendingLoginCache cache = new PendingLoginCache(4);
        PlayerStateSnapshot expired = state(UUID.randomUUID(), "Expired");
        PlayerStateSnapshot recent = state(UUID.randomUUID(), "Recent");
        cache.put(expired, 100L);
        cache.put(recent, 900L);

        assertEquals(1, cache.expireAtOrBefore(500L));
        assertFalse(cache.take(expired.uuid()).isPresent());
        assertEquals(recent, cache.take(recent.uuid()).orElseThrow());
    }

    @Test
    public void capacityRejectsNewLoginsButAllowsSamePlayerToRefresh() {
        PendingLoginCache cache = new PendingLoginCache(2);
        PlayerStateSnapshot first = state(UUID.randomUUID(), "First");
        PlayerStateSnapshot second = state(UUID.randomUUID(), "Second");
        PlayerStateSnapshot rejected = state(UUID.randomUUID(), "Rejected");

        assertTrue(cache.put(first, 100L));
        assertTrue(cache.put(second, 100L));
        assertFalse(cache.put(rejected, 100L));
        assertTrue(cache.put(state(first.uuid(), "FirstAgain"), 200L));
        assertEquals(2, cache.size());
        assertEquals("FirstAgain", cache.take(first.uuid()).orElseThrow().name());
    }

    private static PlayerStateSnapshot state(UUID uuid, String name) {
        return PlayerStateSnapshot.defaults(uuid, name, "Global", Set.of("Global"), 1L);
    }
}
