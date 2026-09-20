package mineverse.Aust1n46.chat.channel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.logging.Logger;
import mineverse.Aust1n46.chat.MineverseChat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.After;
import org.mockito.MockedStatic;
import org.junit.Test;

public class ObsoleteIntegrationChannelTest {
    private MineverseChat plugin;
    private YamlConfiguration config;
    private Object originalPlugin;

    @Before public void setup() throws Exception {
        plugin = mock(MineverseChat.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("channel-cleanup-test"));
        try (MockedStatic<MineverseChat> singleton = mockStatic(MineverseChat.class)) {
            singleton.when(MineverseChat::getInstance).thenReturn(plugin);
            Field instance = ChatChannel.class.getDeclaredField("plugin");
            instance.setAccessible(true);
            originalPlugin = instance.get(null);
            instance.set(null, plugin);
        }
        channel("Global", true);
    }

    @After public void restorePlugin() throws Exception {
        Field instance = ChatChannel.class.getDeclaredField("plugin");
        instance.setAccessible(true);
        instance.set(null, originalPlugin);
    }

    private void channel(String name, boolean isDefault) {
        config.set("channels." + name + ".default", isDefault);
        config.set("channels." + name + ".alias", name.toLowerCase() + "alias");
    }

    @Test public void rejectsConfiguredFormerlyPrivateChannelsBeforePublishingRoutes() {
        ChatChannel.initialize();
        ChatChannel originalGlobal = ChatChannel.getDefaultChannel();
        for (String name : new String[] {"Town", "nAtIoN", "Faction"}) {
            channel(name, true);
            config.set("enable_towny_channel", true);
            config.set("enable_factions_channel", true);
            IllegalStateException failure = assertThrows(IllegalStateException.class, ChatChannel::initialize);
            assertTrue(failure.getMessage().contains(name));
            assertTrue(failure.getMessage().contains("Rename or delete"));
            assertSame(originalGlobal, ChatChannel.getDefaultChannel());
            assertNull(ChatChannel.getChannel(name));
            config.set("channels." + name, null);
        }
    }

    @Test public void retainsOrdinaryNamedChannelsWhenOldIntegrationFlagWasDisabled() {
        channel("Town", false);
        channel("Faction", false);
        ChatChannel.initialize();
        assertNotNull(ChatChannel.getChannel("Town"));
        assertNotNull(ChatChannel.getChannel("Faction"));
    }

    @Test public void obsoleteFlagWithoutMatchingChannelDoesNotBlockStartup() {
        config.set("enable_towny_channel", true);
        config.set("enable_factions_channel", true);
        ChatChannel.initialize();
        assertEquals("Global", ChatChannel.getDefaultChannel().getName());
    }
}
