package mineverse.Aust1n46.chat.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.channel.ChatChannel;

/**
 * Players who have never changed anything have no data file and are not loaded on
 * startup, but staff commands still need to find them by name.
 */
public class MineverseChatAPIUnstoredPlayerTest {
	private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

	private static MockedStatic<MineverseChat> mockedMineverseChat;
	private static MockedStatic<ChatChannel> mockedChatChannel;
	private static MockedStatic<Bukkit> mockedBukkit;

	@BeforeClass
	public static void init() {
		// ChatChannel reads the plugin when it initialises, which the player wrappers touch
		mockedMineverseChat = Mockito.mockStatic(MineverseChat.class);
		MineverseChat plugin = Mockito.mock(MineverseChat.class);
		when(MineverseChat.getInstance()).thenReturn(plugin);
		when(plugin.getConfig()).thenReturn(Mockito.mock(FileConfiguration.class));
		mockedChatChannel = Mockito.mockStatic(ChatChannel.class);
		ChatChannel defaultChannel = Mockito.mock(ChatChannel.class);
		when(defaultChannel.getName()).thenReturn("Global");
		mockedChatChannel.when(ChatChannel::getDefaultChannel).thenReturn(defaultChannel);
		mockedBukkit = Mockito.mockStatic(Bukkit.class);
	}

	@AfterClass
	public static void close() {
		mockedBukkit.close();
		mockedChatChannel.close();
		mockedMineverseChat.close();
	}

	@After
	public void tearDown() {
		MineverseChatAPI.clearMineverseChatPlayerMap();
		MineverseChatAPI.clearNameMap();
	}

	@Test
	public void cachedNameResolvesToADefaultPlayer() {
		OfflinePlayer cached = Mockito.mock(OfflinePlayer.class);
		when(cached.getUniqueId()).thenReturn(PLAYER_UUID);
		when(Bukkit.getOfflinePlayerIfCached("Unstored")).thenReturn(cached);
		OfflinePlayer offline = Mockito.mock(OfflinePlayer.class);
		when(offline.getName()).thenReturn("Unstored");
		when(Bukkit.getOfflinePlayer(PLAYER_UUID)).thenReturn(offline);

		MineverseChatPlayer mcp = MineverseChatAPI.getMineverseChatPlayer("Unstored");

		assertEquals("Unstored", mcp.getName());
		assertEquals(PLAYER_UUID, mcp.getUUID());
		assertSame(mcp, MineverseChatAPI.getMineverseChatPlayer(PLAYER_UUID));
	}

	@Test
	public void unknownNameStillReturnsNull() {
		when(Bukkit.getOfflinePlayerIfCached(anyString())).thenReturn(null);

		assertNull(MineverseChatAPI.getMineverseChatPlayer("NeverSeen"));
	}

	@Test
	public void uuidWithoutACachedNameStillReturnsNull() {
		OfflinePlayer offline = Mockito.mock(OfflinePlayer.class);
		when(offline.getName()).thenReturn(null);
		when(Bukkit.getOfflinePlayer(PLAYER_UUID)).thenReturn(offline);

		assertNull(MineverseChatAPI.getMineverseChatPlayer(PLAYER_UUID));
	}
}
