package mineverse.Aust1n46.chat.config;

import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

/** The shipped defaults and reference config must both remain loadable on upgrade. */
public class BundledConfigurationTest {
    @Test public void defaultConfigurationLoads() throws Exception { verify("config.yml"); }
    @Test public void referenceConfigurationLoads() throws Exception { verify("example_config_always_up_to_date!.yml"); }

    private void verify(String resource) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(resource, input);
            var config = new YamlConfiguration();
            config.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("{venturechat_channel_prefix} {vault_prefix}{player_displayname}&e:", config.getString("channels.Local.format"));
            assertTrue(config.getBoolean("personal-filter.enabled"));
            assertTrue(config.isList("personal-filter.additional-literals"));
            assertEquals(java.util.List.of("Global"), config.getStringList("chat-history.channels"));
            assertFalse(config.getBoolean("chat-history.log-private"));
            assertFalse(config.getBoolean("chat-history.dashboard.enabled"));
        }
    }
}
