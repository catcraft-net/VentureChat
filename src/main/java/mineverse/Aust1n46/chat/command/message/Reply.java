package mineverse.Aust1n46.chat.command.message;

import mineverse.Aust1n46.chat.settings.ChatFeatures;
import mineverse.Aust1n46.chat.settings.PrivateMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.localization.LocalizedMessage;
import mineverse.Aust1n46.chat.utilities.Format;

public class Reply extends Command {
	private MineverseChat plugin = MineverseChat.getInstance();

	public Reply() {
		super("reply");
	}

	@Override
	public boolean execute(CommandSender sender, String command, String[] args) {
		if (!(sender instanceof Player)) {
			plugin.getServer().getConsoleSender().sendMessage(LocalizedMessage.COMMAND_MUST_BE_RUN_BY_PLAYER.toString());
			return true;
		}
		MineverseChatPlayer mcp = MineverseChatAPI.getOnlineMineverseChatPlayer((Player) sender);
		if (args.length > 0) {
			if (mcp.hasReplyPlayer()) {
				MineverseChatPlayer player = MineverseChatAPI.getOnlineMineverseChatPlayer(mcp.getReplyPlayer());
				if (player == null) {
					mcp.getPlayer().sendMessage(LocalizedMessage.NO_PLAYER_TO_REPLY_TO.toString());
					return true;
				}
				if (!mcp.getPlayer().canSee(player.getPlayer())) {
					mcp.getPlayer().sendMessage(LocalizedMessage.NO_PLAYER_TO_REPLY_TO.toString());
					return true;
				}
				boolean senderBypassesToggle = mcp.getPlayer().hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION);
				boolean ignored = player.getIgnores().contains(mcp.getUUID()) && !senderBypassesToggle;
				if (!player.getMessageToggle() && !senderBypassesToggle) {
					mcp.getPlayer().sendMessage(LocalizedMessage.BLOCKING_MESSAGE.toString().replace("{player}", player.getName()));
					return true;
				}
				String msg = "";
				String echo = "";
				String send = "";
				String spy = "";
				if (args.length > 0) {
					for (int r = 0; r < args.length; r++)
						msg += " " + args[r];
					var personalDecision = ChatFeatures.evaluate(plugin, msg);
if (mcp.hasFilter() && ChatFeatures.legacyPrivateFilter(plugin)) {
						msg = Format.FilterChat(msg);
					}
					if (mcp.getPlayer().hasPermission("venturechat.color.legacy")) {
						msg = Format.FormatStringLegacyColor(msg);
					}
					if (mcp.getPlayer().hasPermission("venturechat.color")) {
						msg = Format.FormatStringColor(msg);
					}
					if (mcp.getPlayer().hasPermission("venturechat.format")) {
						msg = Format.FormatString(msg);
					}

					send = Format
							.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(mcp.getPlayer(), plugin.getConfig().getString("replyformatfrom").replaceAll("sender_", "")));
					echo = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(mcp.getPlayer(), plugin.getConfig().getString("replyformatto").replaceAll("sender_", "")));
					spy = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(mcp.getPlayer(), plugin.getConfig().getString("replyformatspy").replaceAll("sender_", "")));

					send = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(player.getPlayer(), send.replaceAll("receiver_", ""))) + msg;
					echo = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(player.getPlayer(), echo.replaceAll("receiver_", ""))) + msg;
					spy = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(player.getPlayer(), spy.replaceAll("receiver_", ""))) + msg;

					if (ignored || ChatFeatures.hide(personalDecision, mcp, player)) {
						PrivateMessages.send(plugin, mcp.getPlayer(), echo, player.getName());
						return true;
					}

					if (!mcp.getPlayer().hasPermission("venturechat.spy.override")) {
						for (MineverseChatPlayer p : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
							if (p.getName().equals(mcp.getName()) || p.getName().equals(player.getName())) {
								continue;
							}
							if (p.isSpy()) {
								p.getPlayer().sendMessage(spy);
							}
						}
					}
					PrivateMessages.send(plugin, player.getPlayer(), send, mcp.getName());
				ChatFeatures.record(plugin, mcp, "DirectMessage", player.getUUID(), msg, true);
					PrivateMessages.send(plugin, mcp.getPlayer(), echo, player.getName());
					if (player.hasNotifications()) {
						Format.playMessageSound(player);
					}
					player.setReplyPlayer(mcp.getUUID());
					return true;
				}
			}
			mcp.getPlayer().sendMessage(LocalizedMessage.NO_PLAYER_TO_REPLY_TO.toString());
			return true;
		}
		mcp.getPlayer().sendMessage(LocalizedMessage.COMMAND_INVALID_ARGUMENTS.toString().replace("{command}", "/reply").replace("{args}", "[message]"));
		return true;
	}
}
