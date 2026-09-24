package mineverse.Aust1n46.chat.filter;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.*;

public class ChatSentryScopeExactJarTest {
    private static Field field(Class<?> type, String name, Class<?> fieldType) {
        Field field = Arrays.stream(type.getDeclaredFields()).filter(f -> f.getName().equals(name) && f.getType() == fieldType).findFirst().orElseThrow();
        field.setAccessible(true);
        return field;
    }
    @Test public void exactChatAndCommandListenersPreservePublicEnforcementAndOtherPrivateModules() throws Exception {
        String supplied = System.getProperty("chatsentry.test.jar");
        Assume.assumeNotNull(supplied);
        Path jar = Path.of(supplied);
        assertEquals(ChatSentry567Detector.SUPPORTED_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mainType = loader.loadClass("com.kixmc.chatsentry.main.Main");
            Plugin main = (Plugin) Mockito.mock(mainType);
            Mockito.when(main.getConfig()).thenReturn(new YamlConfiguration());
            Class<?> settingsType = loader.loadClass("J5tn6Q");
            Object settings = settingsType.getConstructor(mainType).newInstance(main);
            field(settingsType, "f", boolean.class).setBoolean(settings, true);
            field(settingsType, "g", boolean.class).setBoolean(settings, true);
            field(settingsType, "w", boolean.class).setBoolean(settings, true);
            AtomicInteger wordChecks = new AtomicInteger(), otherChecks = new AtomicInteger();
            Class<?> wordType = loader.loadClass("mXcL4z");
            Object wordDetector = Mockito.mock(wordType, invocation -> {
                if (invocation.getMethod().getParameterCount() == 4) {
                    assertEquals(true, invocation.getArgument(2));
                    wordChecks.incrementAndGet();
                    return "";
                }
                return Mockito.RETURNS_DEFAULTS.answer(invocation);
            });
            field(settingsType, "a", wordType).set(settings, wordDetector);
            Class<?> otherType = loader.loadClass("j63fFM");
            Object otherModule = Mockito.mock(otherType, invocation -> {
                if (invocation.getMethod().getParameterCount() == 3) {
                    otherChecks.incrementAndGet();
                    return invocation.getArgument(1);
                }
                return Mockito.RETURNS_DEFAULTS.answer(invocation);
            });
            field(settingsType, "a", otherType).set(settings, otherModule);
            field(settingsType, "x", boolean.class).setBoolean(settings, true);
            Player sender = Mockito.mock(Player.class);
            for (Class<? extends Event> eventType : List.of(AsyncPlayerChatEvent.class, PlayerCommandPreprocessEvent.class)) {
                String listenerName = eventType == AsyncPlayerChatEvent.class ? "vWy4XG" : "grzxGT";
                Class<?> listenerType = loader.loadClass(listenerName);
                Object listener = listenerType.getConstructor(mainType, settingsType).newInstance(main, settings);
                var privateInvocation = new ChatSentryScope.Invocation(listener, eventType);
                Event privateEvent = event(eventType, sender);
                int wordsBefore = wordChecks.get(), otherBefore = otherChecks.get();
                privateInvocation.call(privateEvent);
                assertFalse(((org.bukkit.event.Cancellable)privateEvent).isCancelled());
                assertEquals(wordsBefore, wordChecks.get());
                assertEquals(otherBefore + 1, otherChecks.get());
                assertTrue(field(settingsType, "w", boolean.class).getBoolean(settings));
                Event publicEvent = event(eventType, sender);
                listenerType.getMethod("a", eventType).invoke(listener, publicEvent);
                assertTrue(((org.bukkit.event.Cancellable)publicEvent).isCancelled());
                assertEquals(wordsBefore + 1, wordChecks.get());
                try (var pool = Executors.newFixedThreadPool(4)) {
                    List<java.util.concurrent.Future<Boolean>> tasks = new ArrayList<>();
                    for (int i = 0; i < 80; i++) {
                        boolean scoped = (i & 1) == 0;
                        tasks.add(pool.submit(() -> {
                            Event concurrent = event(eventType, sender);
                            if (scoped) privateInvocation.call(concurrent);
                            else listenerType.getMethod("a", eventType).invoke(listener, concurrent);
                            return ((org.bukkit.event.Cancellable) concurrent).isCancelled() != scoped;
                        }));
                    }
                    for (var task : tasks) assertTrue(task.get(5, TimeUnit.SECONDS));
                }
                assertTrue(field(settingsType, "w", boolean.class).getBoolean(settings));
            }
            assertEquals(82, wordChecks.get());
            assertEquals(164, otherChecks.get());
            // Exercise actual HandlerList installation, wrapper dispatch and restoration too.
            Mockito.when(main.isEnabled()).thenReturn(true);
            Mockito.when(main.getDescription()).thenReturn(new org.bukkit.plugin.PluginDescriptionFile("ChatSentry", "5.6.7", mainType.getName()));
            List<org.bukkit.plugin.RegisteredListener> registered = new ArrayList<>();
            List<org.bukkit.event.HandlerList> handlerLists = List.of(AsyncPlayerChatEvent.getHandlerList(), PlayerCommandPreprocessEvent.getHandlerList());
            try (var bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
                bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
                bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(Mockito.mock(org.bukkit.plugin.PluginManager.class));
                for (int i = 0; i < 2; i++) {
                    Class<? extends Event> eventType = i == 0 ? AsyncPlayerChatEvent.class : PlayerCommandPreprocessEvent.class;
                    Class<?> listenerType = loader.loadClass(i == 0 ? "vWy4XG" : "grzxGT");
                    var actualListener = (org.bukkit.event.Listener) listenerType.getConstructor(mainType, settingsType).newInstance(main, settings);
                    var method = listenerType.getMethod("a", eventType);
                    var original = new org.bukkit.plugin.RegisteredListener(actualListener, (ignored, event) -> {
                        try { method.invoke(actualListener, event); }
                        catch (ReflectiveOperationException ex) { throw new org.bukkit.event.EventException(ex); }
                    }, i == 0 ? org.bukkit.event.EventPriority.LOW : org.bukkit.event.EventPriority.LOWEST, main, false);
                    registered.add(original);
                    handlerLists.get(i).register(original);
                }
                List<String> diagnostics = new ArrayList<>();
                try (var scope = ChatSentryScope.install(main, event -> event instanceof AsyncPlayerChatEvent, diagnostics::add)) {
                    assertTrue(scope.status(), scope.status().startsWith("AVAILABLE"));
                    for (int i = 0; i < 2; i++) {
                        var wrapper = Arrays.stream(handlerLists.get(i).getRegisteredListeners()).filter(r -> r.getPlugin() == main).findFirst().orElseThrow();
                        assertNotSame(registered.get(i), wrapper);
                        Event event = event(i == 0 ? AsyncPlayerChatEvent.class : PlayerCommandPreprocessEvent.class, sender);
                        wrapper.callEvent(event);
                        assertEquals(i != 0, ((org.bukkit.event.Cancellable) event).isCancelled());
                    }
                }
                for (int i = 0; i < 2; i++) assertTrue(Arrays.asList(handlerLists.get(i).getRegisteredListeners()).contains(registered.get(i)));
                assertEquals(1, diagnostics.size());
            } finally {
                for (int i = 0; i < registered.size(); i++) handlerLists.get(i).unregister(registered.get(i));
            }
            assertTrue(Mockito.mockingDetails(sender).getInvocations().stream()
                    .allMatch(i -> i.getMethod().getName().equals("hasPermission")));
        }
    }
    private static Event event(Class<? extends Event> type, Player sender) {
        if (type == AsyncPlayerChatEvent.class) return new AsyncPlayerChatEvent(true, sender, "example", new HashSet<>());
        return new PlayerCommandPreprocessEvent(sender, "/msg player example", new HashSet<>());
    }
}
