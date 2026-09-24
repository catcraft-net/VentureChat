package mineverse.Aust1n46.chat.filter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

/** Exact-build event-local word-filter scope; never changes live permissions or module flags. */
public final class ChatSentryScope implements AutoCloseable {
    private record Replacement(HandlerList handlers, RegisteredListener original, RegisteredListener wrapper) { }
    private final List<Replacement> replacements = new ArrayList<>();
    private volatile String status = "DISABLED";
    private volatile boolean failed;

    public String status() { return status; }

    /** Install on the server thread after ChatSentry enable. Remove CatChatScope before selecting this integration. */
    public static ChatSentryScope install(Plugin chatsentry, Predicate<Event> privateEvent, Consumer<String> diagnostics) {
        ChatSentryScope scope = new ChatSentryScope();
        try {
            if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("scope installation requires server thread");
            ChatSentry567Detector.verifyArtifact(chatsentry);
            Plugin old = Bukkit.getPluginManager().getPlugin("CatChatScope");
            if (old != null && old.isEnabled()) throw new IllegalStateException("remove CatChatScope and restart before enabling integrated scope");
            scope.prepare(AsyncPlayerChatEvent.getHandlerList(), chatsentry, "vWy4XG", AsyncPlayerChatEvent.class, privateEvent, diagnostics);
            scope.prepare(PlayerCommandPreprocessEvent.getHandlerList(), chatsentry, "grzxGT", PlayerCommandPreprocessEvent.class, privateEvent, diagnostics);
            for (Replacement replacement : scope.replacements) {
                replacement.handlers.unregister(replacement.original);
                replacement.handlers.register(replacement.wrapper);
            }
            scope.status = "AVAILABLE: event-local ChatSentry word-filter scope";
        } catch (Exception | LinkageError ex) {
            scope.close();
            scope.status = "UNAVAILABLE: integrated ChatSentry scope (" + ex.getClass().getSimpleName() + "): " + ex.getMessage();
        }
        diagnostics.accept(scope.status);
        return scope;
    }

    private void prepare(HandlerList handlers, Plugin plugin, String expectedClass, Class<? extends Event> eventType,
                         Predicate<Event> privateEvent, Consumer<String> diagnostics) throws Exception {
        List<RegisteredListener> matching = Arrays.stream(handlers.getRegisteredListeners())
                .filter(r -> r.getPlugin() == plugin && r.getListener().getClass().getName().equals(expectedClass)).toList();
        if (matching.size() != 1) throw new IllegalStateException("expected one " + expectedClass + " listener");
        RegisteredListener original = matching.getFirst();
        Invocation invocation = new Invocation(original.getListener(), eventType);
        RegisteredListener wrapper = new RegisteredListener(original.getListener(), (listener, event) -> {
            if (failed) { original.callEvent(event); return; }
            boolean scoped;
            try { scoped = privateEvent.test(event); }
            catch (RuntimeException ex) {
                failed = true;
                status = "UNAVAILABLE: private-route resolution failed; original moderation retained";
                diagnostics.accept(status);
                scoped = false;
            }
            if (!scoped) { original.callEvent(event); return; }
            try { invocation.call(event); }
            catch (InvocationTargetException ex) {
                // Never replay a partly processed event: another module may already have acted.
                failed = true;
                status = "UNAVAILABLE: scoped ChatSentry handler failed; review server log and restart";
                diagnostics.accept(status);
                throw new EventException(ex.getCause());
            } catch (ReflectiveOperationException ex) {
                failed = true;
                status = "UNAVAILABLE: scoped ChatSentry setup failed; original moderation retained";
                diagnostics.accept(status);
                original.callEvent(event);
            }
        }, original.getPriority(), plugin, original.isIgnoringCancelled());
        replacements.add(new Replacement(handlers, original, wrapper));
    }

    @Override public void close() {
        for (Replacement replacement : replacements) {
            if (Arrays.asList(replacement.handlers.getRegisteredListeners()).contains(replacement.wrapper)) {
                replacement.handlers.unregister(replacement.wrapper);
                if (replacement.original.getPlugin().isEnabled()) replacement.handlers.register(replacement.original);
            }
        }
        replacements.clear();
        status = "DISABLED";
    }

    /** Package-visible to test the actual listener classes without registering plugins on a server. */
    static final class Invocation {
        private final Object main;
        private final Object liveSettings;
        private final Constructor<?> settingsConstructor;
        private final Constructor<?> listenerConstructor;
        private final Field[] settingsFields;
        private final Field wordEnabled;
        private final Method handler;

        Invocation(Object listener, Class<? extends Event> eventType) throws Exception {
            Class<?> listenerType = listener.getClass();
            String required = eventType == AsyncPlayerChatEvent.class ? "vWy4XG" : "grzxGT";
            if (!listenerType.getName().equals(required)) throw new IllegalArgumentException("unsupported listener");
            ClassLoader loader = listenerType.getClassLoader();
            Class<?> mainType = Class.forName("com.kixmc.chatsentry.main.Main", false, loader);
            Class<?> settingsType = Class.forName("J5tn6Q", false, loader);
            Field mainField = Arrays.stream(listenerType.getDeclaredFields()).filter(f -> f.getType() == mainType).findFirst().orElseThrow();
            Field settingsField = Arrays.stream(listenerType.getDeclaredFields()).filter(f -> f.getType() == settingsType).findFirst().orElseThrow();
            mainField.setAccessible(true);
            settingsField.setAccessible(true);
            main = mainField.get(listener);
            liveSettings = settingsField.get(listener);
            settingsFields = Arrays.stream(settingsType.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);
            for (Field field : settingsFields) field.setAccessible(true);
            wordEnabled = Arrays.stream(settingsFields).filter(f -> f.getName().equals("w") && f.getType() == boolean.class).findFirst().orElseThrow();
            settingsConstructor = settingsType.getConstructor(mainType);
            listenerConstructor = listenerType.getConstructor(mainType, settingsType);
            handler = listenerType.getMethod("a", eventType);
        }

        void call(Event event) throws ReflectiveOperationException {
            Object localSettings = settingsConstructor.newInstance(main);
            // Keep every other module's real state, flags and helper objects, including spam counters.
            for (Field field : settingsFields) field.set(localSettings, field.get(liveSettings));
            wordEnabled.setBoolean(localSettings, false);
            Object localListener = listenerConstructor.newInstance(main, localSettings);
            handler.invoke(localListener, event);
        }
    }
}
