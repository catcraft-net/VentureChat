package mineverse.Aust1n46.chat.filter;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import mineverse.Aust1n46.chat.command.ChannelAlias;
import mineverse.Aust1n46.chat.command.message.Message;
import mineverse.Aust1n46.chat.command.message.Reply;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;

/** Explicit private-channel allowlist. Unrecognized routes remain moderated. */
public final class VentureChatScopePolicy implements Predicate<Event> {
    private final Set<String> privateChannels;
    private final Set<String> privateCommands;
    private final Predicate<Player> partiesMode;
    private final Function<String, Command> commandResolver;

    public VentureChatScopePolicy(Collection<String> privateChannels, Collection<String> privateCommands,
                                  Predicate<Player> partiesMode) {
        this(privateChannels, privateCommands, partiesMode, label -> Bukkit.getServer().getCommandMap().getCommand(label));
    }
    VentureChatScopePolicy(Collection<String> privateChannels, Collection<String> privateCommands,
                           Predicate<Player> partiesMode, Function<String, Command> commandResolver) {
        this.commandResolver = commandResolver;
        this.privateChannels = normalize(privateChannels);
        this.privateCommands = normalize(privateCommands);
        this.partiesMode = partiesMode;
    }
    private static Set<String> normalize(Collection<String> strings) {
        return strings.stream().map(s -> s.toLowerCase(Locale.ROOT).replaceFirst("^/", "")).collect(Collectors.toUnmodifiableSet());
    }
    @Override public boolean test(Event event) {
        if (event instanceof PlayerCommandPreprocessEvent command) return privateCommand(command.getMessage());
        if (!(event instanceof AsyncPlayerChatEvent chat)) return false;
        // Parties' real mode takes precedence because its LOWEST chat handler owns that route.
        if (partiesMode != null && partiesMode.test(chat.getPlayer())) return true;
        MineverseChatPlayer player = MineverseChatAPI.getOnlineMineverseChatPlayer(chat.getPlayer());
        if (player == null) return false;
        if (!player.isQuickChat() && (player.hasConversation() || player.isPartyChat())) return true;
        return privateChannel(player.isQuickChat() ? player.getQuickChannel() : player.getCurrentChannel());
    }
    private boolean privateCommand(String message) {
        String[] pieces = message.strip().toLowerCase(Locale.ROOT).replaceFirst("^/", "").split("\\s+", 2);
        if (pieces.length != 2 || pieces[1].isBlank()) return false;
        String label = pieces[0];
        Command resolved = commandResolver.apply(label);
        if (resolved == null) return false;
        String base = label.startsWith("venturechat:") ? label.substring("venturechat:".length()) : label;
        if (resolved instanceof Message || resolved instanceof Reply)
            return privateCommands.contains(label) || privateCommands.contains(base);
        if (resolved instanceof ChannelAlias) {
            // ChannelAlias itself routes by the complete alias label, not channel name/namespace.
            for (ChatChannel channel : ChatChannel.getChatChannels()) {
                if (label.equalsIgnoreCase(channel.getAlias())) return privateChannel(channel);
            }
            return false;
        }
        // External exemptions require both an explicit namespace and verified plugin ownership.
        if (label.startsWith("parties:") && privateCommands.contains(label)
                && resolved instanceof PluginIdentifiableCommand owned)
            return owned.getPlugin().isEnabled() && owned.getPlugin().getName().equals("Parties");
        return false;
    }
    private boolean privateChannel(ChatChannel channel) {
        return channel != null && privateChannels.contains(channel.getName().toLowerCase(Locale.ROOT));
    }
}
