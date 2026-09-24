package mineverse.Aust1n46.chat.filter;

import java.util.Objects;

/** Immutable, position-preserving content result, reusable across eligible recipients. */
public record CensorResult(String original, String censored, FilterDecision decision) {
    public CensorResult {
        Objects.requireNonNull(original);
        Objects.requireNonNull(censored);
        Objects.requireNonNull(decision);
        if (original.length() != censored.length()) throw new IllegalArgumentException("Censoring must preserve text positions");
        for (int i = 0; i < original.length(); i++) {
            if (original.charAt(i) != censored.charAt(i) && censored.charAt(i) != '*')
                throw new IllegalArgumentException("Censoring may only replace characters with asterisks");
        }
    }
    public boolean changed() { return !original.equals(censored); }
}
