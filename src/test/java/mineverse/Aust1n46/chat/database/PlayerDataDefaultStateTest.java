package mineverse.Aust1n46.chat.database;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;
import mineverse.Aust1n46.chat.command.mute.MuteContainer;

/**
 * A player who has not changed anything should not get a data file written for
 * them, because it would only ever hold defaults.
 */
public class PlayerDataDefaultStateTest {
	private static MockedStatic<MineverseChat> mockedMineverseChat;
	private static MockedStatic<ChatChannel> mockedChatChannel;

	private ChatChannel defaultChannel;
	private ChatChannel autojoinChannel;
	private MineverseChatPlayer mcp;

	@BeforeClass
	public static void init() {
		// ChatChannel and PlayerData both read the plugin on class initialisation
		mockedMineverseChat = Mockito.mockStatic(MineverseChat.class);
		MineverseChat plugin = Mockito.mock(MineverseChat.class);
		when(MineverseChat.getInstance()).thenReturn(plugin);
		when(plugin.getConfig()).thenReturn(mock(FileConfiguration.class));
		when(plugin.getDataFolder()).thenReturn(new File(System.getProperty("java.io.tmpdir"), "venturechat-data-test"));
		mockedChatChannel = Mockito.mockStatic(ChatChannel.class);
	}

	@AfterClass
	public static void close() {
		mockedChatChannel.close();
		mockedMineverseChat.close();
	}

	@Before
	public void setUp() {
		defaultChannel = Mockito.mock(ChatChannel.class);
		when(defaultChannel.getName()).thenReturn("Global");
		autojoinChannel = Mockito.mock(ChatChannel.class);
		when(autojoinChannel.getName()).thenReturn("Group");
		mockedChatChannel.when(ChatChannel::getDefaultChannel).thenReturn(defaultChannel);
		mockedChatChannel.when(ChatChannel::getAutojoinList).thenReturn(Arrays.asList(defaultChannel, autojoinChannel));

		mcp = Mockito.mock(MineverseChatPlayer.class);
		when(mcp.getCurrentChannel()).thenReturn(defaultChannel);
		when(mcp.getIgnores()).thenReturn(Collections.emptySet());
		when(mcp.getMutes()).thenReturn(Collections.<MuteContainer>emptyList());
		when(mcp.getBlockedCommands()).thenReturn(Collections.emptySet());
		when(mcp.getListening()).thenReturn(new HashSet<String>(Arrays.asList("Global")));
		when(mcp.isHost()).thenReturn(false);
		when(mcp.hasParty()).thenReturn(false);
		when(mcp.hasFilter()).thenReturn(true);
		when(mcp.hasNotifications()).thenReturn(true);
		when(mcp.isSpy()).thenReturn(false);
		when(mcp.hasCommandSpy()).thenReturn(false);
		when(mcp.getRangedSpy()).thenReturn(false);
		when(mcp.getMessageToggle()).thenReturn(true);
		when(mcp.getBungeeToggle()).thenReturn(true);
	}

	@Test
	public void untouchedPlayerIsNotStored() {
		assertTrue(PlayerData.isDefaultPlayerState(mcp));
		assertFalse(PlayerData.shouldStorePlayerData(mcp, false));
	}

	@Test
	public void existingDefaultRecordIsDeletedInsteadOfRewritten() {
		assertFalse(PlayerData.shouldStorePlayerData(mcp, true));
	}

	@Test
	public void listeningToEveryAutojoinChannelIsStillDefault() {
		when(mcp.getListening()).thenReturn(new HashSet<String>(Arrays.asList("Global", "Group")));

		assertTrue(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void extraListenedChannelIsStored() {
		when(mcp.getListening()).thenReturn(new HashSet<String>(Arrays.asList("Global", "Staff")));

		assertFalse(PlayerData.isDefaultPlayerState(mcp));
		assertTrue(PlayerData.shouldStorePlayerData(mcp, false));
	}

	@Test
	public void changedChannelIsStored() {
		ChatChannel other = Mockito.mock(ChatChannel.class);
		when(other.getName()).thenReturn("Staff");
		when(mcp.getCurrentChannel()).thenReturn(other);

		assertFalse(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void ignoredPlayersAreStored() {
		when(mcp.getIgnores()).thenReturn(Collections.singleton(UUID.randomUUID()));

		assertFalse(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void blockedMessagesAreStored() {
		when(mcp.getMessageToggle()).thenReturn(false);

		assertFalse(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void mutedPlayersAreStored() {
		when(mcp.getMutes()).thenReturn(Collections.singletonList(new MuteContainer("Global", System.currentTimeMillis())));

		assertFalse(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void changedNotificationsAndSpySettingsAreStored() {
		when(mcp.hasNotifications()).thenReturn(false);
		assertFalse(PlayerData.isDefaultPlayerState(mcp));

		when(mcp.hasNotifications()).thenReturn(true);
		when(mcp.isSpy()).thenReturn(true);
		assertFalse(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void missingDefaultChannelIsNotTreatedAsDefault() {
		mockedChatChannel.when(ChatChannel::getDefaultChannel).thenReturn(null);

		assertFalse(PlayerData.isDefaultPlayerState(mcp));
	}

	@Test
	public void dirtyOfflinePlayerIsQueuedWithoutScanningAllPlayers() {
		UUID uuid = UUID.randomUUID();
		when(mcp.getUUID()).thenReturn(uuid);
		when(mcp.getName()).thenReturn("OfflinePlayer");
		when(mcp.getParty()).thenReturn(null);
		when(mcp.getJsonFormat()).thenReturn("Default");
		when(mcp.getStorageRevision()).thenReturn(4L);
		when(mcp.wasModified()).thenReturn(true);
		PlayerSaveCoordinator storage = mock(PlayerSaveCoordinator.class);
		Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
		dirtyPlayers.add(uuid);

		PlayerData.flushDirtyPlayers(storage, dirtyPlayers, ignored -> mcp);

		verify(storage).queue(Mockito.argThat(snapshot -> snapshot.uuid().equals(uuid)
				&& snapshot.revision() == 4L));
		verify(mcp).setModified(false);
		assertTrue(dirtyPlayers.isEmpty());
	}
}
