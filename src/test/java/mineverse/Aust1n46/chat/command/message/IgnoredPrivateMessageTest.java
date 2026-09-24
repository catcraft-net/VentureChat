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
		when(plugin.getChatFeatures()).thenReturn(null);
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
		when(targetMcp.getMessageToggle()).thenReturn(true);
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

    private void matchingPersonalFilter() {
        var features = Mockito.mock(mineverse.Aust1n46.chat.settings.ChatFeatures.class);
        when(plugin.getChatFeatures()).thenReturn(features);
        when(features.censor(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return new mineverse.Aust1n46.chat.filter.CensorResult(text, text.replace("hello", "*****"), mineverse.Aust1n46.chat.filter.FilterDecision.MATCH);
        });
        when(targetMcp.getIgnores()).thenReturn(Collections.emptySet());
        when(targetMcp.hasPersonalFilter()).thenReturn(true);
        when(targetMcp.hasNotifications()).thenReturn(true);
    }

    @Test public void personalFilterCensorsDirectMessageEvenFromStaffBypass() {
        matchingPersonalFilter();
        when(sender.hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION)).thenReturn(true);
        new Message().execute(sender, "msg", new String[] {"Target", "hello"});
        verify(target).sendMessage("from *****");
        verify(sender).sendMessage("to hello");
        verify(spy).sendMessage("spy hello");
        verify(targetMcp).setReplyPlayer(SENDER_UUID);
        mockedFormat.verify(() -> Format.playMessageSound(targetMcp));
    }
    @Test public void personalFilterCensorsReplyAndOptedInSpy() {
        matchingPersonalFilter();
        when(spyMcp.hasPersonalFilter()).thenReturn(true);
        when(senderMcp.hasReplyPlayer()).thenReturn(true);
        when(senderMcp.getReplyPlayer()).thenReturn(TARGET_UUID);
        new Reply().execute(sender, "reply", new String[] {"hello"});
        verify(target).sendMessage("reply-from *****");
        verify(sender).sendMessage("reply-to hello");
        verify(spy).sendMessage("reply-spy *****");
        verify(targetMcp).setReplyPlayer(SENDER_UUID);
        mockedFormat.verify(() -> Format.playMessageSound(targetMcp));
    }
    @Test public void personalFilterCensorsConversation() {
        matchingPersonalFilter();
        when(senderMcp.hasConversation()).thenReturn(true);
        when(senderMcp.getConversation()).thenReturn(TARGET_UUID);
        AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
        when(event.getPlayer()).thenReturn(sender);
        when(event.getMessage()).thenReturn("hello");
        when(event.getRecipients()).thenReturn(Collections.emptySet());
        new ChatListener().handleTrueAsyncPlayerChatEvent(event);
        verify(target).sendMessage("from *****");
        verify(sender).sendMessage("to hello");
        verify(spy).sendMessage("spy hello");
        verify(targetMcp).setReplyPlayer(SENDER_UUID);
        mockedFormat.verify(() -> Format.playMessageSound(targetMcp));
    }
    @Test public void personalFilterOptOutAllowsDelivery() {
        matchingPersonalFilter();
        when(targetMcp.hasPersonalFilter()).thenReturn(false);
        new Message().execute(sender, "msg", new String[] {"Target", "hello"});
        verify(target).sendMessage("from hello");
        verify(targetMcp).setReplyPlayer(SENDER_UUID);
        mockedFormat.verify(() -> Format.playMessageSound(targetMcp));
    }

    @Test public void nativePartyKeepsSenderEchoAndCensorsOnlyOptedInRecipients() {
        matchingPersonalFilter();
        when(senderMcp.isPartyChat()).thenReturn(true);
        when(senderMcp.hasParty()).thenReturn(true);
        when(senderMcp.getParty()).thenReturn(TARGET_UUID);
        when(targetMcp.hasParty()).thenReturn(true);
        when(targetMcp.getParty()).thenReturn(TARGET_UUID);
        when(senderMcp.hasPersonalFilter()).thenReturn(true);
        when(config.getString("partyformat")).thenReturn("{host} party {player}:");
        var console = Mockito.mock(org.bukkit.command.ConsoleCommandSender.class);
        mockedBukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        AsyncPlayerChatEvent event = Mockito.mock(AsyncPlayerChatEvent.class);
        when(event.getPlayer()).thenReturn(sender);
        when(event.getMessage()).thenReturn("hello");
        when(event.getRecipients()).thenReturn(Collections.emptySet());
        new ChatListener().handleTrueAsyncPlayerChatEvent(event);
        verify(sender).sendMessage("Target party Sender: hello");
        verify(target).sendMessage("Target party Sender: *****");
        verify(spy).sendMessage("Target party Sender: hello");
        verify(console).sendMessage("Target party Sender: hello");
        verify(plugin.getChatFeatures()).censor(" hello");
    }

    @Test public void channelDeliversMaskedAndOriginalPacketsToEligibleRecipients() {
        matchingPersonalFilter();
        when(senderMcp.hasPersonalFilter()).thenReturn(true);
        mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(target)).thenReturn(targetMcp);
        mockedMineverseChatAPI.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(spy)).thenReturn(spyMcp);
        var console = Mockito.mock(org.bukkit.command.ConsoleCommandSender.class);
        mockedBukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        var channel = Mockito.mock(mineverse.Aust1n46.chat.channel.ChatChannel.class);
        when(channel.getName()).thenReturn("Local");
        var event = Mockito.mock(mineverse.Aust1n46.chat.api.events.VentureChatEvent.class);
        when(event.getMineverseChatPlayer()).thenReturn(senderMcp);
        when(event.getChannel()).thenReturn(channel);
        when(event.getRecipients()).thenReturn(new java.util.LinkedHashSet<>(Arrays.asList(sender, target, spy)));
        when(event.getRecipientCount()).thenReturn(3);
        when(event.getChat()).thenReturn("hello");
        when(event.getFormat()).thenReturn("Sender: ");
        when(event.getConsoleChat()).thenReturn("Sender: hello");
        String original = "[\"\",{\"text\":\"\",\"extra\":[{\"text\":\"Sender: \"}]},{\"text\":\"hello\"}]";
        when(event.getGlobalJSON()).thenReturn(original);
        java.util.Map<Player, String> deliveries = new java.util.HashMap<>();
        mockedFormat.when(() -> Format.formatModerationGUI(anyString(), any(Player.class), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> { deliveries.put(invocation.getArgument(1), invocation.getArgument(0)); return invocation.getArgument(0); });
        new ChatListener().handleVentureChatEvent(event);
        assertEquals(original, deliveries.get(sender));
        assertEquals(original, deliveries.get(spy));
        org.junit.Assert.assertTrue(deliveries.get(target).contains("*****"));
        org.junit.Assert.assertFalse(deliveries.get(target).contains("hello"));
        assertEquals(3, deliveries.size());
        verify(plugin.getChatFeatures()).censor("hello");
        verify(console).sendMessage("Sender: hello");
    }

}
