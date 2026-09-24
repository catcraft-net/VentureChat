package mineverse.Aust1n46.chat.filter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Narrow adapter for the reviewed, exact ChatSentry build. No third-party code is bundled. */
public final class ChatSentry567Detector implements PersonalFilterService.Detector {
    public static final String SUPPORTED_SHA256 = "67a5a5766360255c8c563d688d5ce5b2bac90387ff4745cff796e15582cce92b";
    private final Object snapshot;
    private final Method evaluate;
    private final Object context;
    private final Plugin plugin;
    private volatile String failure;

    private ChatSentry567Detector(Object snapshot, Method evaluate, Object context, Plugin plugin) {
        this.snapshot = snapshot;
        this.evaluate = evaluate;
        this.context = context;
        this.plugin = plugin;
    }

    public static PersonalFilterService.Detector connect(Plugin plugin) {
        try {
            verifyArtifact(plugin);
            return snapshot(liveDetector(plugin), plugin);
        } catch (Exception | LinkageError ex) {
            return PersonalFilterService.unavailable("ChatSentry 5.6.7 adapter: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "; restart after correcting plugin/configuration");
        }
    }

    /** ChatSentry defers configuration/module initialization until the server starts ticking. */
    public static boolean isReady(Plugin plugin) {
        if (plugin == null || !plugin.isEnabled() || !"com.kixmc.chatsentry.main.Main".equals(plugin.getClass().getName())) return false;
        try { return initialized(liveDetector(plugin)); }
        catch (Exception | LinkageError ex) { return false; }
    }

    private static Object liveDetector(Plugin plugin) throws Exception {
        Class<?> detectorType = Class.forName("mXcL4z", true, plugin.getClass().getClassLoader());
        // JVM methods in this build share a name and arguments but differ in return type.
        Method getter = Arrays.stream(plugin.getClass().getMethods())
                .filter(m -> m.getName().equals("a") && m.getParameterCount() == 0 && m.getReturnType() == detectorType)
                .findFirst().orElseThrow(() -> new ReflectiveOperationException("detector getter absent"));
        return getter.invoke(plugin);
    }

    private static boolean initialized(Object detector) throws Exception {
        if (detector == null) return false;
        // x24Wf is assigned only at the end of mXcL4z.g(), after all rule/config fields load.
        // An empty rule list is legitimate, so it cannot be used as a readiness signal.
        Field utility = Arrays.stream(detector.getClass().getDeclaredFields())
                .filter(f -> f.getType().getName().equals("x24Wf")).findFirst().orElseThrow();
        utility.setAccessible(true);
        return utility.get(detector) != null;
    }

    public static void verifyArtifact(Plugin plugin) throws Exception {
        if (plugin == null || !plugin.isEnabled() || !"5.6.7".equals(plugin.getDescription().getVersion())
                || !"com.kixmc.chatsentry.main.Main".equals(plugin.getClass().getName()))
            throw new IllegalArgumentException("unsupported or disabled ChatSentry");
        Path jar = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var input = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            if (!SUPPORTED_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalArgumentException("unreviewed ChatSentry artifact");
        }
    }

    /** Must run after ChatSentry has finished enabling, on the server thread. */
    static ChatSentry567Detector snapshot(Object live, Plugin plugin) throws Exception {
        if (live == null || !live.getClass().getName().equals("mXcL4z")) throw new IllegalArgumentException("missing detector");
        if (!initialized(live)) throw new IllegalStateException("ChatSentry modules have not finished initializing");
        Class<?> type = live.getClass();
        Class<?> main = Class.forName("com.kixmc.chatsentry.main.Main", false, type.getClassLoader());
        Field owner = Arrays.stream(type.getDeclaredFields()).filter(f -> f.getType() == main).findFirst().orElseThrow();
        owner.setAccessible(true);
        Object copy = type.getConstructor(main).newInstance(owner.get(live));
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(live);
            if (value instanceof ArrayList<?> list) value = new ArrayList<>(list);
            else if (value instanceof HashMap<?, ?> map) value = new HashMap<>(map);
            field.set(copy, value);
        }
        // Never enter the native censor helper: it recurses, trims text and may return empty.
        // Only this detached detector copy is changed; the live global setting is untouched.
        type.getField("bu").setBoolean(copy, false);
        Class<?> contextType = Class.forName("xuNkC8", true, type.getClassLoader());
        Object chat = Arrays.stream(contextType.getEnumConstants()).filter(v -> v.toString().equals("CHAT")).findFirst().orElseThrow();
        Method method = type.getMethod("a", Player.class, String.class, boolean.class, contextType);
        if (method.getReturnType() != String.class) throw new IllegalArgumentException("unsupported result type");
        return new ChatSentry567Detector(copy, method, chat, plugin);
    }

    @Override public synchronized FilterDecision evaluate(String message) {
        if (failure != null) return FilterDecision.UNAVAILABLE;
        if (plugin != null && !plugin.isEnabled()) {
            failure = "ChatSentry was disabled; restart to reconnect";
            return FilterDecision.UNAVAILABLE;
        }
        try {
            // Null player and false enforcement are deliberate. Non-null means a match,
            // including an unchanged string when censoring is disabled.
            return evaluate.invoke(snapshot, null, message, false, context) == null ? FilterDecision.CLEAN : FilterDecision.MATCH;
        } catch (Exception | LinkageError ex) {
            Throwable cause = ex instanceof java.lang.reflect.InvocationTargetException invocation ? invocation.getCause() : ex;
            failure = "detector invocation failed (" + cause.getClass().getSimpleName() + "); restart to reconnect";
            return FilterDecision.UNAVAILABLE;
        }
    }

    private static final int MAX_CENSOR_LENGTH = 2048;
    private static final int MAX_CENSOR_PROBES = 128;

    /** Infer bounded matching intervals using the reviewed detector as the sole rule oracle. */
    @Override public synchronized CensorResult censor(String message) {
        if (message == null) throw new NullPointerException("message");
        if (message.length() > MAX_CENSOR_LENGTH)
            return new CensorResult(message, message, FilterDecision.UNAVAILABLE);
        int[] probes = {0};
        FilterDecision initial = evaluate(message);
        probes[0]++;
        if (initial != FilterDecision.MATCH) return new CensorResult(message, message, initial);
        char[] masked = message.toCharArray();
        var words = new ArrayList<int[]>();
        for (int i = 0; i < message.length();) {
            if (Character.isWhitespace(message.charAt(i))) { i++; continue; }
            int start = i++;
            while (i < message.length() && !Character.isWhitespace(message.charAt(i))) i++;
            words.add(new int[]{start, i});
        }
        var pending = new java.util.ArrayDeque<int[]>();
        if (!words.isEmpty()) pending.add(new int[]{0, words.size()});
        boolean first = true;
        try {
            while (!pending.isEmpty()) {
                int[] range = pending.removeFirst();
                int start = range[0], end = range[1];
                if (!first && probe(wordSlice(message, words, start, end), probes) != FilterDecision.MATCH) continue;
                first = false;
                // Only trim complete words: character cuts can invent an exact match inside
                // an innocent word, or leave part of a fuzzy-matched word visible.
                int low = start, high = end;
                while (low + 1 < high) {
                    int mid = (low + high) >>> 1;
                    if (probe(wordSlice(message, words, mid, end), probes) == FilterDecision.MATCH) low = mid;
                    else high = mid;
                }
                int matchStart = low;
                low = matchStart; high = end;
                while (low + 1 < high) {
                    int mid = (low + high) >>> 1;
                    if (probe(wordSlice(message, words, matchStart, mid), probes) == FilterDecision.MATCH) high = mid;
                    else low = mid;
                }
                int matchEnd = high;
                for (int word = matchStart; word < matchEnd; word++) {
                    int from = words.get(word)[0], to = words.get(word)[1];
                    int firstLetter = from, lastLetter = to;
                    while (firstLetter < to && !Character.isLetterOrDigit(message.codePointAt(firstLetter)))
                        firstLetter += Character.charCount(message.codePointAt(firstLetter));
                    while (lastLetter > firstLetter && !Character.isLetterOrDigit(message.codePointBefore(lastLetter)))
                        lastLetter -= Character.charCount(message.codePointBefore(lastLetter));
                    // Keep outer punctuation/emoji, but a symbols-only matched word is masked too.
                    if (firstLetter < lastLetter) { from = firstLetter; to = lastLetter; }
                    Arrays.fill(masked, from, to, '*');
                }
                if (start < matchStart) pending.addLast(new int[]{start, matchStart});
                if (matchEnd < end) pending.addLast(new int[]{matchEnd, end});
            }
            return new CensorResult(message, new String(masked), FilterDecision.MATCH);
        } catch (ProbeUnavailable ex) {
            // Never present a partial mask as full protection or erase the whole message.
            return new CensorResult(message, message, FilterDecision.UNAVAILABLE);
        }
    }

    private static String wordSlice(String message, ArrayList<int[]> words, int from, int to) {
        return message.substring(words.get(from)[0], words.get(to - 1)[1]);
    }

    private FilterDecision probe(String message, int[] probes) {
        if (++probes[0] > MAX_CENSOR_PROBES) throw new ProbeUnavailable();
        FilterDecision decision = evaluate(message);
        if (decision == FilterDecision.UNAVAILABLE) throw new ProbeUnavailable();
        return decision;
    }

    private static final class ProbeUnavailable extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @Override public String status() {
        if (plugin != null && !plugin.isEnabled()) failure = "ChatSentry was disabled; restart to reconnect";
        return failure == null ? "AVAILABLE: ChatSentry 5.6.7 rule snapshot (restart to refresh rules)" : "UNAVAILABLE: " + failure;
    }
}
