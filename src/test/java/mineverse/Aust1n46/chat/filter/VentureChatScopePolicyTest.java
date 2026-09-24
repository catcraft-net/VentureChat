package mineverse.Aust1n46.chat.filter;

import java.util.HashSet;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.Test;
import org.mockito.Mockito;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;
import static org.junit.Assert.*;

public class VentureChatScopePolicyTest {
    @Test public void publicOtherChannelAndQuickPublicMessageRemainModerated() {
        try (var plugin = Mockito.mockStatic(mineverse.Aust1n46.chat.MineverseChat.class);
             var api = Mockito.mockStatic(MineverseChatAPI.class)) {
            Player player = Mockito.mock(Player.class);
            MineverseChatPlayer state = Mockito.mock(MineverseChatPlayer.class);
            ChatChannel local = Mockito.mock(ChatChannel.class), trade = Mockito.mock(ChatChannel.class);
            Mockito.when(local.getName()).thenReturn("Local");
            Mockito.when(trade.getName()).thenReturn("Trade");
            api.when(() -> MineverseChatAPI.getOnlineMineverseChatPlayer(player)).thenReturn(state);
            var policy = new VentureChatScopePolicy(List.of("Local"), List.of("msg"), p -> false);
            var chat = new AsyncPlayerChatEvent(true, player, "message", new HashSet<>());
            Mockito.when(state.getCurrentChannel()).thenReturn(trade);
            assertFalse(policy.test(chat));
            Mockito.when(state.getCurrentChannel()).thenReturn(local);
            assertTrue(policy.test(chat));
            Mockito.when(state.hasConversation()).thenReturn(true);
            Mockito.when(state.isQuickChat()).thenReturn(true);
            Mockito.when(state.getQuickChannel()).thenReturn(trade);
            assertFalse(policy.test(chat));
            Mockito.when(state.isQuickChat()).thenReturn(false);
            assertTrue(policy.test(chat));
        }
    }
    @Test public void explicitCommandsDoNotImplicitlyExemptOtherNamespacesOrUnknownChannels() {
        try (var plugin = Mockito.mockStatic(mineverse.Aust1n46.chat.MineverseChat.class);
             var channels = Mockito.mockStatic(ChatChannel.class)) {
            channels.when(ChatChannel::getChatChannels).thenReturn(List.of());
            Player player = Mockito.mock(Player.class);
            var commands = new java.util.HashMap<String, org.bukkit.command.Command>();
            var message = Mockito.mock(mineverse.Aust1n46.chat.command.message.Message.class);
            commands.put("msg", message);
            commands.put("venturechat:msg", message);
            var foreign = Mockito.mock(org.bukkit.command.Command.class);
            commands.put("essentials:msg", foreign);
            commands.put("parties:p", foreign);
            var policy = new VentureChatScopePolicy(List.of("Local"), List.of("msg", "parties:p"), p -> false, commands::get);
            assertTrue(policy.test(new PlayerCommandPreprocessEvent(player, "/msg recipient message", new HashSet<>())));
            assertTrue(policy.test(new PlayerCommandPreprocessEvent(player, "/venturechat:msg recipient message", new HashSet<>())));
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/parties:p message", new HashSet<>())));
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/essentials:msg recipient message", new HashSet<>())));
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/msg", new HashSet<>())));
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/unknown message", new HashSet<>())));
            commands.put("msg", foreign);
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/msg recipient message", new HashSet<>())));
            commands.remove("msg");
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/msg recipient message", new HashSet<>())));
            var local = Mockito.mock(ChatChannel.class);
            Mockito.when(local.getName()).thenReturn("Local");
            Mockito.when(local.getAlias()).thenReturn("l");
            channels.when(ChatChannel::getChatChannels).thenReturn(List.of(local));
            commands.put("l", Mockito.mock(mineverse.Aust1n46.chat.command.ChannelAlias.class));
            assertTrue(policy.test(new PlayerCommandPreprocessEvent(player, "/l message", new HashSet<>())));
            commands.put("l", foreign);
            assertFalse(policy.test(new PlayerCommandPreprocessEvent(player, "/l message", new HashSet<>())));
        }
    }
}
