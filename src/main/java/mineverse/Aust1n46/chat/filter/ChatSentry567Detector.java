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

    @Override public String status() {
        if (plugin != null && !plugin.isEnabled()) failure = "ChatSentry was disabled; restart to reconnect";
        return failure == null ? "AVAILABLE: ChatSentry 5.6.7 rule snapshot (restart to refresh rules)" : "UNAVAILABLE: " + failure;
    }
}
