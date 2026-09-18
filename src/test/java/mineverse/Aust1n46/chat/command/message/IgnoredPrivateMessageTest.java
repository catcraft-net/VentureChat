package mineverse.Aust1n46.chat.command.message;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.PluginManager;
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
import mineverse.Aust1n46.chat.listeners.ChatListener;
import mineverse.Aust1n46.chat.localization.Localization;
import mineverse.Aust1n46.chat.utilities.Format;

/**
 * Regression tests for private messages sent to a player who ignores the sender.
 */
public class IgnoredPrivateMessageTest {
	private static final UUID SENDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID TARGET_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static MockedStatic<MineverseChat> mockedMineverseChat;
	private static MockedStatic<MineverseChatAPI> mockedMineverseChatAPI;
	private static MockedStatic<PlaceholderAPI> mockedPlaceholderAPI;
	private static MockedStatic<Localization> mockedLocalization;
	private static MockedStatic<Format> mockedFormat;
	private static MockedStatic<Bukkit> mockedBukkit;

	private static MineverseChat plugin;
	private static FileConfiguration config;
	private static FileConfiguration localization;
	private static PluginManager pluginManager;

	private Player sender;
	private Player target;
	private Player spy;
	private MineverseChatPlayer senderMcp;
	private MineverseChatPlayer targetMcp;
	private MineverseChatPlayer spyMcp;

	@BeforeClass
	public static void init() {
		mockedMineverseChat = Mockito.mockStatic(MineverseChat.class);
		mockedMineverseChatAPI = Mockito.mockStatic(MineverseChatAPI.class);
		mockedPlaceholderAPI = Mockito.mockStatic(PlaceholderAPI.class);
		mockedLocalization = Mockito.mockStatic(Localization.class);
		mockedFormat = Mockito.mockStatic(Format.class);
		mockedBukkit = Mockito.mockStatic(Bukkit.class);

		plugin = Mockito.mock(MineverseChat.class);
		config = Mockito.mock(FileConfiguration.class);
		localization = Mockito.mock(FileConfiguration.class);
		pluginManager = Mockito.mock(PluginManager.class);

		when(MineverseChat.getInstance()).thenReturn(plugin);
		when(plugin.getConfig()).thenReturn(config);
		when(Localization.getLocalization()).thenReturn(localization);
		when(Bukkit.getPluginManager()).thenReturn(pluginManager);
		when(pluginManager.isPluginEnabled(anyString())).thenReturn(false);

		mockedPlaceholderAPI.when(() -> PlaceholderAPI.setBracketPlaceholders(any(Player.class), anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		mockedFormat.when(() -> Format.FormatStringAll(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@AfterClass
	public static void close() {
		mockedBukkit.close();
		mockedFormat.close();
		mockedLocalization.close();
		mockedPlaceholderAPI.close();
		mockedMineverseChatAPI.close();
		mockedMineverseChat.close();
	}

	@Before
	public void setUp() {
		sender = Mockito.mock(Player.class);
		target = Mockito.mock(Player.class);
		spy = Mockito.mock(Player.class);
		senderMcp = Mockito.mock(MineverseChatPlayer.class);
		targetMcp = Mockito.mock(MineverseChatPlayer.class);
		spyMcp = Mockito.mock(MineverseChatPlayer.class);

		when(senderMcp.getPlayer()).thenReturn(sender);
		when(senderMcp.getUUID()).thenReturn(SENDER_UUID);
		when(senderMcp.getName()).thenReturn("Sender");
		when(sender.canSee(target)).thenReturn(true);
		when(sender.hasPermission(anyString())).thenReturn(false);
		when(senderMcp.hasFilter()).thenReturn(false);

		when(targetMcp.getPlayer()).thenReturn(target);
		when(targetMcp.getUUID()).thenReturn(TARGET_UUID);
		when(targetMcp.getName()).thenReturn("Target");
		when(targetMcp.getIgnores()).thenReturn(Collections.singleton(SENDER_UUID));
		when(targetMcp.getMessageToggle()).thenReturn(false);
		when(targetMcp.isOnline()).thenReturn(true);

		when(spyMcp.getPlayer()).thenReturn(spy);
		when(spyMcp.getName()).thenReturn("Spy");
		when(spyMcp.isSpy()).thenReturn(true);

		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(false);
		when(config.getString("tellformatfrom")).thenReturn("from");
		when(config.getString("tellformatto")).thenReturn("to");
		when(config.getString("tellformatspy")).thenReturn("spy");
		when(config.getString("replyformatfrom")).thenReturn("reply-from");
		when(config.getString("replyformatto")).thenReturn("reply-to");
		when(config.getString("replyformatspy")).thenReturn("reply-spy");

		when(localization.getString("IgnoringMessage")).thenReturn("IGNORING {player}");
		when(localization.getString("EnterPrivateConversation")).thenReturn("ENTER {player_receiver}");

		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(sender)).thenReturn(senderMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer("Target")).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(TARGET_UUID)).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getMineverseChatPlayer(TARGET_UUID)).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(MineverseChatAPI::getOnlineMineverseChatPlayers)
				.thenReturn(Arrays.asList(senderMcp, targetMcp, spyMcp));
	}

	@Test
	public void messageEchoesSuccessOnlyToSender() {
		new Message().execute(sender, "msg", new String[] { "Target", "hello" });

		verify(sender).sendMessage("to hello");
		verify(target, never()).sendMessage(anyString());
		verify(spy, never()).sendMessage(anyString());
		verify(senderMcp).setReplyPlayer(TARGET_UUID);
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
		mockedFormat.verify(() -> Format.playMessageSound(targetMcp), never());
	}

	@Test
	public void replyEchoesSuccessOnlyToSender() {
		when(senderMcp.hasReplyPlayer()).thenReturn(true);
		when(senderMcp.getReplyPlayer()).thenReturn(TARGET_UUID);

		new Reply().execute(sender, "reply", new String[] { "hello" });

		verify(sender).sendMessage("reply-to hello");
		verify(target, never()).sendMessage(anyString());
		verify(spy, never()).sendMessage(anyString());
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
		mockedFormat.verify(() -> Format.playMessageSound(targetMcp), never());
	}

	@Test
	public void conversationEchoesSuccessOnlyToSender() {
		when(senderMcp.hasConversation()).thenReturn(true);
		when(senderMcp.getConversation()).thenReturn(TARGET_UUID);

		AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
		when(event.getPlayer()).thenReturn(sender);
		when(event.getMessage()).thenReturn("hello");
		when(event.getRecipients()).thenReturn(Collections.emptySet());

		new ChatListener().handleTrueAsyncPlayerChatEvent(event);

		verify(sender).sendMessage("to hello");
		verify(target, never()).sendMessage(anyString());
		verify(spy, never()).sendMessage(anyString());
		verify(senderMcp).setReplyPlayer(TARGET_UUID);
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
		mockedFormat.verify(() -> Format.playMessageSound(targetMcp), never());
	}

	@Test
	public void enteringConversationDoesNotNotifySpies() {
		when(senderMcp.hasConversation()).thenReturn(false);

		new Message().execute(sender, "msg", new String[] { "Target" });

		verify(senderMcp).setConversation(TARGET_UUID);
		verify(sender).sendMessage("ENTER Target");
		verify(target, never()).sendMessage(anyString());
		verify(spy, never()).sendMessage(anyString());
	}

	@Test
	public void networkMessageReturnsSilentEchoWithoutDelivering() throws Exception {
		when(config.getString("loglevel", "info")).thenReturn("info");
		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(true);
		mockedMineverseChat.when(MineverseChat::isConnectedToProxy).thenReturn(true);
		AtomicReference<byte[]> outgoingPacket = new AtomicReference<>();
		mockedMineverseChat.when(() -> MineverseChat.sendPluginMessage(any(ByteArrayOutputStream.class)))
				.thenAnswer(invocation -> {
					outgoingPacket.set(((ByteArrayOutputStream) invocation.getArgument(0)).toByteArray());
					return null;
				});

		ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
		try (DataOutputStream request = new DataOutputStream(requestBytes)) {
			request.writeUTF("Message");
			request.writeUTF("Send");
			request.writeUTF("sender-server");
			request.writeUTF("Target");
			request.writeUTF(SENDER_UUID.toString());
			request.writeUTF("Sender");
			request.writeUTF("from");
			request.writeUTF("to");
			request.writeUTF("spy");
			request.writeUTF(" hello");
		}

		MineverseChat messageHandler = Mockito.mock(MineverseChat.class, Mockito.CALLS_REAL_METHODS);
		Mockito.doReturn(config).when(messageHandler).getConfig();
		messageHandler.onPluginMessageReceived(MineverseChat.PLUGIN_MESSAGING_CHANNEL, sender, requestBytes.toByteArray());

		try (DataInputStream response = new DataInputStream(new ByteArrayInputStream(outgoingPacket.get()))) {
			assertEquals("Message", response.readUTF());
			assertEquals("Echo", response.readUTF());
			assertEquals("sender-server", response.readUTF());
			assertEquals("Target", response.readUTF());
			assertEquals(TARGET_UUID.toString(), response.readUTF());
			assertEquals(SENDER_UUID.toString(), response.readUTF());
			assertEquals("Sender", response.readUTF());
			assertEquals("to hello", response.readUTF());
			assertEquals("VentureChat:NoSpy", response.readUTF());
			assertEquals(0, response.available());
		}

		verify(target, never()).sendMessage(anyString());
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
		mockedFormat.verify(() -> Format.playMessageSound(targetMcp), never());
	}
}
