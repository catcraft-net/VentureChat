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
}
