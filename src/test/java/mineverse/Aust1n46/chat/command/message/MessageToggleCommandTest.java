package mineverse.Aust1n46.chat.command.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.clip.placeholderapi.PlaceholderAPI;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.localization.Localization;
import mineverse.Aust1n46.chat.utilities.Format;

/**
 * Covers the message toggle being reachable through the message command, for
 * example {@code /pm toggle}, as well as through {@code /messagetoggle}.
 */
public class MessageToggleCommandTest {
	private static final UUID SENDER_UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	private static MockedStatic<MineverseChat> mockedMineverseChat;
	private static MockedStatic<MineverseChatAPI> mockedMineverseChatAPI;
	private static MockedStatic<PlaceholderAPI> mockedPlaceholderAPI;
	private static MockedStatic<Localization> mockedLocalization;
	private static MockedStatic<Format> mockedFormat;

	private static MineverseChat plugin;
	private static FileConfiguration config;
	private static FileConfiguration localization;

	private Player sender;
	private MineverseChatPlayer senderMcp;
	private AtomicBoolean messageToggle;

	@BeforeClass
	public static void init() {
		mockedMineverseChat = Mockito.mockStatic(MineverseChat.class);
		mockedMineverseChatAPI = Mockito.mockStatic(MineverseChatAPI.class);
		mockedPlaceholderAPI = Mockito.mockStatic(PlaceholderAPI.class);
		mockedLocalization = Mockito.mockStatic(Localization.class);
		mockedFormat = Mockito.mockStatic(Format.class);

		plugin = Mockito.mock(MineverseChat.class);
		config = Mockito.mock(FileConfiguration.class);
		localization = Mockito.mock(FileConfiguration.class);

		when(MineverseChat.getInstance()).thenReturn(plugin);
		when(plugin.getConfig()).thenReturn(config);
		when(Localization.getLocalization()).thenReturn(localization);

		mockedFormat.when(() -> Format.FormatStringAll(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		mockedPlaceholderAPI.when(() -> PlaceholderAPI.setBracketPlaceholders(any(Player.class), anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
	}

	@AfterClass
	public static void close() {
		mockedFormat.close();
		mockedLocalization.close();
		mockedPlaceholderAPI.close();
		mockedMineverseChatAPI.close();
		mockedMineverseChat.close();
	}

	@Before
	public void setUp() {
		sender = Mockito.mock(Player.class);
		senderMcp = Mockito.mock(MineverseChatPlayer.class);
		messageToggle = new AtomicBoolean(true);

		when(senderMcp.getPlayer()).thenReturn(sender);
		when(senderMcp.getUUID()).thenReturn(SENDER_UUID);
		when(senderMcp.getName()).thenReturn("Sender");
		when(senderMcp.getMessageToggle()).thenAnswer(invocation -> messageToggle.get());
		doAnswer(invocation -> {
			messageToggle.set(invocation.getArgument(0));
			return null;
		}).when(senderMcp).setMessageToggle(anyBoolean());

		when(sender.hasPermission(MessageToggle.PERMISSION)).thenReturn(true);

		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(false);
		when(localization.getString("MessageToggleOn")).thenReturn("MESSAGE_TOGGLE_ON");
		when(localization.getString("MessageToggleOff")).thenReturn("MESSAGE_TOGGLE_OFF");
		when(localization.getString("CommandNoPermission")).thenReturn("COMMAND_NO_PERMISSION");
		when(localization.getString("CommandInvalidArguments")).thenReturn("COMMAND_INVALID_ARGUMENTS");
		when(localization.getString("PlayerOffline")).thenReturn("PLAYER_OFFLINE");

		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(sender)).thenReturn(senderMcp);
	}

	@After
	public void tearDown() {
		mockedMineverseChatAPI.reset();
	}

	@Test
	public void messageCommandToggleTurnsMessagesOff() {
		new Message().execute(sender, "pm", new String[] { "toggle" });

		assertFalse(messageToggle.get());
		verify(sender).sendMessage("MESSAGE_TOGGLE_OFF");
		mockedMineverseChat.verify(() -> MineverseChat.synchronize(senderMcp, true));
	}

	@Test
	public void messageCommandToggleTurnsMessagesBackOn() {
		messageToggle.set(false);

		new Message().execute(sender, "pm", new String[] { "toggle" });

		assertTrue(messageToggle.get());
		verify(sender).sendMessage("MESSAGE_TOGGLE_ON");
		mockedMineverseChat.verify(() -> MineverseChat.synchronize(senderMcp, true));
	}

	@Test
	public void messageCommandToggleIsCaseInsensitive() {
		new Message().execute(sender, "pm", new String[] { "ToGgLe" });

		assertFalse(messageToggle.get());
		verify(sender).sendMessage("MESSAGE_TOGGLE_OFF");
	}

	@Test
	public void messageCommandToggleRequiresPermission() {
		when(sender.hasPermission(MessageToggle.PERMISSION)).thenReturn(false);

		new Message().execute(sender, "pm", new String[] { "toggle" });

		assertTrue(messageToggle.get());
		verify(sender).sendMessage("COMMAND_NO_PERMISSION");
		mockedMineverseChat.verify(() -> MineverseChat.synchronize(any(MineverseChatPlayer.class), anyBoolean()), never());
	}

	@Test
	public void messageCommandToggleDoesNotLookUpAPlayerNamedToggle() {
		new Message().execute(sender, "pm", new String[] { "toggle" });

		mockedMineverseChatAPI.verify(() -> MineverseChatAPI.getOnlineMineverseChatPlayer("toggle"), never());
		verify(sender, never()).sendMessage("PLAYER_OFFLINE");
		verify(sender, never()).sendMessage("COMMAND_INVALID_ARGUMENTS");
	}

	@Test
	public void messageCommandToggleWorksWithBungeeMessagingEnabled() {
		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(true);

		new Message().execute(sender, "pm", new String[] { "toggle" });

		assertFalse(messageToggle.get());
		verify(sender).sendMessage("MESSAGE_TOGGLE_OFF");
		verify(sender, never()).sendMessage("COMMAND_INVALID_ARGUMENTS");
		verify(sender, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
	}

	@Test
	public void messagingAPlayerNamedToggleStillSendsAMessage() {
		MineverseChatPlayer targetMcp = Mockito.mock(MineverseChatPlayer.class);
		Player target = Mockito.mock(Player.class);
		when(targetMcp.getPlayer()).thenReturn(target);
		when(targetMcp.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
		when(targetMcp.getName()).thenReturn("toggle");
		when(targetMcp.getMessageToggle()).thenReturn(true);
		when(targetMcp.getIgnores()).thenReturn(Collections.emptySet());
		when(targetMcp.hasNotifications()).thenReturn(false);
		when(sender.canSee(target)).thenReturn(true);
		when(sender.hasPermission(anyString())).thenReturn(false);
		when(senderMcp.hasFilter()).thenReturn(false);
		when(config.getString("tellformatfrom")).thenReturn("to-target");
		when(config.getString("tellformatto")).thenReturn("echo");
		when(config.getString("tellformatspy")).thenReturn("spy");
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer("toggle")).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(MineverseChatAPI::getOnlineMineverseChatPlayers)
				.thenReturn(Collections.singletonList(senderMcp));

		new Message().execute(sender, "pm", new String[] { "toggle", "hello" });

		assertTrue(messageToggle.get());
		verify(target).sendMessage("to-target hello");
		verify(sender).sendMessage("echo hello");
	}

	@Test
	public void messageToggleCommandStillToggles() {
		new MessageToggle().execute(sender, "messagetoggle", new String[0]);

		assertFalse(messageToggle.get());
		verify(sender).sendMessage("MESSAGE_TOGGLE_OFF");
		mockedMineverseChat.verify(() -> MineverseChat.synchronize(senderMcp, true));
	}

	@Test
	public void messageCommandToggleIsSuggestedWhenTabCompleting() {
		assertEquals(Collections.singletonList("toggle"), new Message().tabComplete(sender, "pm", new String[] { "to" }));
	}
}
