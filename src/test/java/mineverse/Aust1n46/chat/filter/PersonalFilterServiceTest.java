package mineverse.Aust1n46.chat.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersonalFilterServiceTest {
    @Test public void unavailableStaysVisibleEvenWithLocalRules() {
        List<String> notices = new ArrayList<>();
        var filter = new PersonalFilterService(PersonalFilterService.unavailable("unsupported version"), List.of("example"), notices::add);
        assertEquals(FilterDecision.MATCH, filter.evaluate("an EXAMPLE"));
        assertEquals(FilterDecision.UNAVAILABLE, filter.evaluate("ordinary message"));
        assertEquals(1, notices.size());
        assertTrue(filter.status().contains("UNAVAILABLE"));
    }
    @Test public void preferencesAreAppliedAfterOneContentDecisionWithoutHidingSender() {
        UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID();
        assertTrue(PersonalFilterService.shouldHide(FilterDecision.MATCH, sender, recipient, true));
        assertFalse(PersonalFilterService.shouldHide(FilterDecision.MATCH, sender, sender, true));
        assertFalse(PersonalFilterService.shouldHide(FilterDecision.MATCH, sender, recipient, false));
        assertFalse(PersonalFilterService.shouldHide(FilterDecision.UNAVAILABLE, sender, recipient, true));
        assertFalse(PersonalFilterService.shouldHide(FilterDecision.CLEAN, sender, recipient, true));
    }
    @Test public void censorCombinesLiteralMatchesAndPreservesUnmatchedText() {
        var filter = new PersonalFilterService(PersonalFilterService.unavailable("missing"),
                List.of("example", "bad", "İ"), status -> {});
        CensorResult result = filter.censor("  EXAMPLE, bad! İ 😀  ");
        assertEquals("  *******, ***! * 😀  ", result.censored());
        assertEquals("  EXAMPLE, bad! İ 😀  ", result.original());
        assertEquals(FilterDecision.MATCH, result.decision());
        assertTrue(result.changed());
        assertEquals(FilterDecision.UNAVAILABLE, filter.censor("hello").decision());
        assertEquals("hello", filter.censor("hello").censored());
    }
    @Test public void resultRejectsLengthOrNonMaskChanges() {
        assertThrows(IllegalArgumentException.class, () -> new CensorResult("bad", "", FilterDecision.MATCH));
        assertThrows(IllegalArgumentException.class, () -> new CensorResult("bad", "cat", FilterDecision.MATCH));
    }
}
