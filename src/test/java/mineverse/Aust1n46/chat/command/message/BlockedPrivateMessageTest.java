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
import mineverse.Aust1n46.chat.listeners.ChatListener;
import mineverse.Aust1n46.chat.localization.Localization;
import mineverse.Aust1n46.chat.utilities.Format;

/**
 * A player who has blocked private messages must never receive them, even when
 * they are also ignoring the sender.
 */
public class BlockedPrivateMessageTest {
	private static final UUID SENDER_UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID TARGET_UUID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

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
	private MineverseChatPlayer senderMcp;
	private MineverseChatPlayer targetMcp;

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

		mockedFormat.when(() -> Format.FormatStringAll(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		mockedPlaceholderAPI.when(() -> PlaceholderAPI.setBracketPlaceholders(any(Player.class), anyString()))
				.thenAnswer(invocation -> invocation.getArgument(1));
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
		senderMcp = Mockito.mock(MineverseChatPlayer.class);
		targetMcp = Mockito.mock(MineverseChatPlayer.class);

		when(senderMcp.getPlayer()).thenReturn(sender);
		when(senderMcp.getUUID()).thenReturn(SENDER_UUID);
		when(senderMcp.getName()).thenReturn("Sender");
		when(senderMcp.hasFilter()).thenReturn(false);
		when(senderMcp.getReplyPlayer()).thenReturn(TARGET_UUID);
		when(senderMcp.hasReplyPlayer()).thenReturn(true);
		when(sender.hasPermission(anyString())).thenReturn(false);
		when(sender.canSee(target)).thenReturn(true);

		when(targetMcp.getPlayer()).thenReturn(target);
		when(targetMcp.getUUID()).thenReturn(TARGET_UUID);
		when(targetMcp.getName()).thenReturn("Target");
		when(targetMcp.isOnline()).thenReturn(true);
		// Target blocks private messages and also ignores the sender.
		when(targetMcp.getMessageToggle()).thenReturn(false);
		when(targetMcp.getIgnores()).thenReturn(Collections.singleton(SENDER_UUID));

		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(false);
		when(config.getString("loglevel", "info")).thenReturn("info");
		when(config.getString("tellformatfrom")).thenReturn("from");
		when(config.getString("tellformatto")).thenReturn("to");
		when(config.getString("tellformatspy")).thenReturn("spy");
		when(config.getString("replyformatfrom")).thenReturn("reply-from");
		when(config.getString("replyformatto")).thenReturn("reply-to");
		when(config.getString("replyformatspy")).thenReturn("reply-spy");

		when(localization.getString("BlockingMessage")).thenReturn("BLOCKING {player}");

		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(sender)).thenReturn(senderMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer("Target")).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(TARGET_UUID)).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getMineverseChatPlayer(TARGET_UUID)).thenReturn(targetMcp);
		mockedMineverseChatAPI.when(MineverseChatAPI::getOnlineMineverseChatPlayers)
				.thenReturn(Arrays.asList(senderMcp, targetMcp));
	}

	@After
	public void tearDown() {
		// The API stubs above are per test. Without this reset the UUID lookups
		// leak into later tests and hand back the previous test's player mocks.
		mockedMineverseChatAPI.reset();
	}

	@Test
	public void messageCommandReportsBlockingInsteadOfEchoing() {
		new Message().execute(sender, "vmessage", new String[] { "Target", "hello" });

		verify(sender).sendMessage("BLOCKING Target");
		verify(target, never()).sendMessage(anyString());
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
	}

	@Test
	public void replyCommandReportsBlockingInsteadOfEchoing() {
		new Reply().execute(sender, "reply", new String[] { "hello" });

		verify(sender).sendMessage("BLOCKING Target");
		verify(target, never()).sendMessage(anyString());
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
	}

	@Test
	public void conversationReportsBlockingAndStaysOutOfPublicChat() {
		when(senderMcp.hasConversation()).thenReturn(true);
		when(senderMcp.getConversation()).thenReturn(TARGET_UUID);
		AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
		when(event.getPlayer()).thenReturn(sender);
		when(event.getMessage()).thenReturn("hello");
		when(event.getRecipients()).thenReturn(Collections.emptySet());

		new ChatListener().handleTrueAsyncPlayerChatEvent(event);

		verify(sender).sendMessage("BLOCKING Target");
		verify(event).setCancelled(true);
		verify(target, never()).sendMessage(anyString());
	}

	@Test
	public void ignoredConversationStaysOutOfPublicChat() {
		when(targetMcp.getMessageToggle()).thenReturn(true);
		when(senderMcp.hasConversation()).thenReturn(true);
		when(senderMcp.getConversation()).thenReturn(TARGET_UUID);
		AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
		when(event.getPlayer()).thenReturn(sender);
		when(event.getMessage()).thenReturn("hello");
		when(event.getRecipients()).thenReturn(Collections.emptySet());

		new ChatListener().handleTrueAsyncPlayerChatEvent(event);

		verify(sender).sendMessage("to hello");
		verify(event).setCancelled(true);
		verify(target, never()).sendMessage(anyString());
	}

	@Test
	public void networkMessageReportsBlockedInsteadOfEchoing() throws Exception {
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
			assertEquals("Blocked", response.readUTF());
			assertEquals("sender-server", response.readUTF());
			assertEquals("Target", response.readUTF());
			assertEquals(SENDER_UUID.toString(), response.readUTF());
			assertEquals(0, response.available());
		}

		verify(target, never()).sendMessage(anyString());
		verify(targetMcp, never()).setReplyPlayer(any(UUID.class));
	}

	@Test
	public void bypassPermissionDeliversMessageToABlockedPlayer() {
		when(targetMcp.getIgnores()).thenReturn(Collections.emptySet());
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);

		new Message().execute(sender, "vmessage", new String[] { "Target", "hello" });

		verify(target).sendMessage("from hello");
		verify(sender).sendMessage("to hello");
		verify(targetMcp).setReplyPlayer(SENDER_UUID);
	}

	@Test
	public void bypassPermissionDeliversReplyToABlockedPlayer() {
		when(targetMcp.getIgnores()).thenReturn(Collections.emptySet());
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);

		new Reply().execute(sender, "reply", new String[] { "hello" });

		verify(target).sendMessage("reply-from hello");
		verify(sender).sendMessage("reply-to hello");
	}

	@Test
	public void bypassPermissionDeliversConversationMessages() {
		when(targetMcp.getIgnores()).thenReturn(Collections.emptySet());
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);
		when(senderMcp.hasConversation()).thenReturn(true);
		when(senderMcp.getConversation()).thenReturn(TARGET_UUID);
		AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
		when(event.getPlayer()).thenReturn(sender);
		when(event.getMessage()).thenReturn("hello");
		when(event.getRecipients()).thenReturn(Collections.emptySet());

		new ChatListener().handleTrueAsyncPlayerChatEvent(event);

		verify(target).sendMessage("from hello");
		verify(sender).sendMessage("to hello");
	}

	@Test
	public void bypassPermissionForcesMessageThroughTheReceiversIgnoreList() {
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);

		new Message().execute(sender, "vmessage", new String[] { "Target", "hello" });

		verify(target).sendMessage("from hello");
		verify(sender).sendMessage("to hello");
	}

	@Test
	public void bypassPermissionForcesMessageThroughWhenTheReceiverAcceptsMessages() {
		when(targetMcp.getMessageToggle()).thenReturn(true);
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);

		new Message().execute(sender, "vmessage", new String[] { "Target", "hello" });

		verify(target).sendMessage("from hello");
		verify(sender).sendMessage("to hello");
	}

	@Test
	public void bypassPermissionForcesReplyThroughTheReceiversIgnoreList() {
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);

		new Reply().execute(sender, "reply", new String[] { "hello" });

		verify(target).sendMessage("reply-from hello");
		verify(sender).sendMessage("reply-to hello");
	}

	@Test
	public void bypassPermissionForcesConversationMessageThroughTheReceiversIgnoreList() {
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);
		when(senderMcp.hasConversation()).thenReturn(true);
		when(senderMcp.getConversation()).thenReturn(TARGET_UUID);
		AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
		when(event.getPlayer()).thenReturn(sender);
		when(event.getMessage()).thenReturn("hello");
		when(event.getRecipients()).thenReturn(Collections.emptySet());

		new ChatListener().handleTrueAsyncPlayerChatEvent(event);

		verify(target).sendMessage("from hello");
		verify(sender).sendMessage("to hello");
	}

	@Test
	public void networkBypassPermissionForcesMessageThroughTheReceiversIgnoreList() throws Exception {
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);
		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(true);
		mockedMineverseChat.when(MineverseChat::isConnectedToProxy).thenReturn(true);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(SENDER_UUID)).thenReturn(senderMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getMineverseChatPlayer(SENDER_UUID)).thenReturn(senderMcp);
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

		verify(target).sendMessage("from hello");
		verify(targetMcp).setReplyPlayer(SENDER_UUID);
	}

	@Test
	public void networkBypassPermissionDeliversToABlockedPlayer() throws Exception {
		when(targetMcp.getIgnores()).thenReturn(Collections.emptySet());
		when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);
		when(config.getBoolean("bungeecordmessaging", true)).thenReturn(true);
		mockedMineverseChat.when(MineverseChat::isConnectedToProxy).thenReturn(true);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(SENDER_UUID)).thenReturn(senderMcp);
		mockedMineverseChatAPI.when(() -> MineverseChatAPI.getMineverseChatPlayer(SENDER_UUID)).thenReturn(senderMcp);
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

		verify(target).sendMessage("from hello");
		verify(targetMcp).setReplyPlayer(SENDER_UUID);
		try (DataInputStream response = new DataInputStream(new ByteArrayInputStream(outgoingPacket.get()))) {
			assertEquals("Message", response.readUTF());
			assertEquals("Echo", response.readUTF());
		}
	}
}
