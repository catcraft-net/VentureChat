package mineverse.Aust1n46.chat.filter;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.*;

/** Opt-in fixture: -Dchatsentry.test.jar=/absolute/path/to/the/original.jar. Never committed. */
public class ChatSentry567ExactJarTest {
    @Test public void originalDetectorHasNoEnforcementEffectsWithNullPlayerAndFalse() throws Exception {
        String supplied = System.getProperty("chatsentry.test.jar");
        Assume.assumeNotNull(supplied);
        Path jar = Path.of(supplied);
        assertEquals(ChatSentry567Detector.SUPPORTED_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
             var bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getVersion).thenReturn("26.2");
            Class<?> mainType = loader.loadClass("com.kixmc.chatsentry.main.Main");
            // Mockito creates an inert exact Main instance; every plugin method invocation is recorded.
            Object main = Mockito.mock(mainType);
            Class<?> type = loader.loadClass("mXcL4z");
            Object live = type.getConstructor(mainType).newInstance(main);
            assertThrows(IllegalStateException.class, () -> ChatSentry567Detector.snapshot(live, null));
            Class<?> similarity = loader.loadClass("x24Wf");
            Field similarityField = java.util.Arrays.stream(type.getDeclaredFields()).filter(f -> f.getType() == similarity).findFirst().orElseThrow();
            similarityField.setAccessible(true);
            similarityField.set(live, similarity.getConstructor(mainType).newInstance(main));
            type.getField("c").setFloat(live, 0.95f);
            type.getField("aP").set(live, "*");
            type.getField("aQ").set(live, "****");
            @SuppressWarnings("unchecked") ArrayList<String> rules = (ArrayList<String>) type.getField("r").get(live);
            rules.add("exact::example");
            rules.add("exactcontains::abcdef");
            rules.add("regex::xyz[ab]");
            rules.add("sample phrase");
            for (boolean censor : new boolean[]{false, true}) {
                type.getField("bu").setBoolean(live, censor);
                var detector = ChatSentry567Detector.snapshot(live, null);
                var greeting = detector.evaluate("friendly greeting");
                assertEquals(detector.status(), FilterDecision.CLEAN, greeting);
                assertEquals(FilterDecision.MATCH, detector.evaluate("EXAMPLE"));
                assertEquals(FilterDecision.MATCH, detector.evaluate("xxabcdefxx"));
                assertEquals(FilterDecision.MATCH, detector.evaluate("xyza"));
                assertEquals(FilterDecision.MATCH, detector.evaluate("sample phrase"));
                assertEquals(FilterDecision.CLEAN, detector.evaluate("examples"));
                // The private copy keeps the configured rules despite mutation of the live lists.
                rules.add("exact::friendly");
                assertEquals(FilterDecision.CLEAN, detector.evaluate("friendly"));
                rules.remove("exact::friendly");
                try (var pool = Executors.newFixedThreadPool(4)) {
                    List<java.util.concurrent.Future<FilterDecision>> results = new ArrayList<>();
                    for (int i = 0; i < 80; i++) results.add(pool.submit(() -> {
                        try (var threadBukkit = Mockito.mockStatic(Bukkit.class)) {
                            threadBukkit.when(Bukkit::getVersion).thenReturn("26.2");
                            FilterDecision verdict = detector.evaluate("example");
                            threadBukkit.verify(Bukkit::getVersion, Mockito.atLeastOnce());
                            threadBukkit.verifyNoMoreInteractions();
                            return verdict;
                        }
                    }));
                    for (var result : results) assertEquals(FilterDecision.MATCH, result.get(5, TimeUnit.SECONDS));
                }
                assertTrue(detector.status(), detector.status().startsWith("AVAILABLE"));
            }
            // No event dispatch, scheduled punishment, online-player lookup, notification or logger access.
            // The strict mock plus null player / unset enforcement helpers would fail if any are touched.
            bukkit.verify(Bukkit::getVersion, Mockito.atLeastOnce());
            bukkit.verifyNoMoreInteractions();
            Mockito.verifyNoInteractions(main);
        }
    }
}
