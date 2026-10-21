package mineverse.Aust1n46.chat.integrations;

import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EssentialsMailBridgeTest {
    @Test public void ignoreDirectionAndUnignoreUseRecipientsCurrentState() throws Exception {
        UUID sender=UUID.randomUUID(), recipient=UUID.randomUUID();
        Map<UUID,Set<UUID>> cached=new HashMap<>();
        cached.put(recipient,Set.of(sender));
        var gate=new EssentialsMailBridge.IgnoreLookup(cached::get, id->{throw new AssertionError("No disk read for cached recipient");},Runnable::run);
        assertTrue(gate.blocked(recipient,sender).get());
        cached.put(recipient,Set.of());
        assertFalse(gate.blocked(recipient,sender).get());
        cached.put(sender,Set.of(recipient));
        assertFalse(gate.blocked(recipient,sender).get());
    }
    @Test public void offlineReadIsAsyncAndRechecksCurrentStateBeforeAllowing() throws Exception {
        UUID sender=UUID.randomUUID(), recipient=UUID.randomUUID();
        Map<UUID,Set<UUID>> cached=new HashMap<>();
        var pending=new CompletableFuture<Set<UUID>>();
        var gate=new EssentialsMailBridge.IgnoreLookup(cached::get,id->pending,Runnable::run);
        var decision=gate.blocked(recipient,sender);
        assertFalse(decision.isDone());
        cached.put(recipient,Set.of(sender));
        pending.complete(Set.of());
        assertTrue(decision.get());
    }
    @Test public void offlineStoredIgnoreBlocksAndReadFailureDoesNotAllow() throws Exception {
        UUID sender=UUID.randomUUID(), recipient=UUID.randomUUID();
        var gate=new EssentialsMailBridge.IgnoreLookup(id->null,id->CompletableFuture.completedFuture(Set.of(sender)),Runnable::run);
        assertTrue(gate.blocked(recipient,sender).get());
        var broken=new EssentialsMailBridge.IgnoreLookup(id->null,id->CompletableFuture.failedFuture(new IllegalStateException("storage unavailable")),Runnable::run);
        assertThrows(ExecutionException.class,()->broken.blocked(recipient,sender).get());
    }
}
