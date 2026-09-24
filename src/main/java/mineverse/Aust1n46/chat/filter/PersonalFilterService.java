package mineverse.Aust1n46.chat.filter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Evaluate once, then apply only after the normal delivery eligibility checks. */
public final class PersonalFilterService {
    public interface Detector {
        FilterDecision evaluate(String message);
        String status();
        default CensorResult censor(String message) { return new CensorResult(message, message, evaluate(message)); }
    }
    private final Detector detector;
    private final List<String> literalRules;
    private final Consumer<String> diagnostic;
    private volatile String lastReported;

    public PersonalFilterService(Detector detector, List<String> literalRules, Consumer<String> diagnostic) {
        this.detector = Objects.requireNonNull(detector);
        this.literalRules = literalRules.stream().filter(Objects::nonNull).map(String::strip)
                .filter(s -> !s.isEmpty()).distinct().toList();
        this.diagnostic = Objects.requireNonNull(diagnostic);
        reportStatus();
    }

    public FilterDecision evaluate(String message) {
        if (message == null) return FilterDecision.UNAVAILABLE;
        FilterDecision decision = detector.evaluate(message);
        reportStatus();
        if (decision == FilterDecision.MATCH) return decision;
        for (String rule : literalRules)
            for (int start = 0; start <= message.length() - rule.length(); start++)
                if (message.regionMatches(true, start, rule, 0, rule.length())) return FilterDecision.MATCH;
        // A local non-match never implies the configured strict detector was available.
        return decision;
    }

    public CensorResult censor(String message) {
        Objects.requireNonNull(message);
        CensorResult result = detector.censor(message);
        reportStatus();
        char[] masked = result.censored().toCharArray();
        boolean literalMatch = false;
        // regionMatches avoids lowercasing the message, which can change UTF-16 offsets.
        for (String rule : literalRules) {
            for (int start = 0; start <= message.length() - rule.length(); start++) {
                if (message.regionMatches(true, start, rule, 0, rule.length())) {
                    literalMatch = true;
                    java.util.Arrays.fill(masked, start, start + rule.length(), '*');
                }
            }
        }
        return new CensorResult(message, new String(masked), literalMatch ? FilterDecision.MATCH : result.decision());
    }

    public String status() { return detector.status() + (literalRules.isEmpty() ? "" : "; additional literal rules: " + literalRules.size()); }

    private void reportStatus() {
        String status = status();
        if (!status.equals(lastReported)) {
            synchronized (this) {
                if (!status.equals(lastReported)) { lastReported = status; diagnostic.accept(status); }
            }
        }
    }

    public static boolean shouldHide(FilterDecision decision, UUID sender, UUID recipient, boolean enabled) {
        return enabled && decision == FilterDecision.MATCH && !Objects.equals(sender, recipient);
    }

    public static Detector unavailable(String reason) {
        return new Detector() {
            public FilterDecision evaluate(String message) { return FilterDecision.UNAVAILABLE; }
            public String status() { return "UNAVAILABLE: " + reason; }
        };
    }
}
