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

public class Message extends Command {
	public Message() {
		super("message");
	}

	private MineverseChat plugin = MineverseChat.getInstance();

	@Override
	public boolean execute(CommandSender sender, String command, String[] args) {
		if (!(sender instanceof Player)) {
			plugin.getServer().getConsoleSender().sendMessage(LocalizedMessage.COMMAND_MUST_BE_RUN_BY_PLAYER.toString());
			return true;
		}

		MineverseChatPlayer mcp = MineverseChatAPI.getOnlineMineverseChatPlayer((Player) sender);
		if (args.length == 0) {
			mcp.getPlayer().sendMessage(LocalizedMessage.COMMAND_INVALID_ARGUMENTS.toString().replace("{command}", "/" + command).replace("{args}", "[player] [message]"));
			return true;
		}

		MineverseChatPlayer player = MineverseChatAPI.getOnlineMineverseChatPlayer(args[0]);
		if (player == null) {
			mcp.getPlayer().sendMessage(LocalizedMessage.PLAYER_OFFLINE.toString().replace("{args}", args[0]));
			return true;
		}
		if (!mcp.getPlayer().canSee(player.getPlayer())) {
			mcp.getPlayer().sendMessage(LocalizedMessage.PLAYER_OFFLINE.toString().replace("{args}", args[0]));
			return true;
		}
		boolean senderBypassesToggle = mcp.getPlayer().hasPermission(MineverseChat.MESSAGETOGGLE_BYPASS_PERMISSION);
		boolean ignored = player.getIgnores().contains(mcp.getUUID()) && !senderBypassesToggle;
		if (!player.getMessageToggle() && !senderBypassesToggle) {
			mcp.getPlayer().sendMessage(LocalizedMessage.BLOCKING_MESSAGE.toString().replace("{player}", player.getName()));
			return true;
		}

		if (args.length >= 2) {
			String msg = "";
			String echo = "";
			String send = "";
			String spy = "";
			if (args[1].length() > 0) {
				for (int r = 1; r < args.length; r++) {
					msg += " " + args[r];
				}
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

				send = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(mcp.getPlayer(), plugin.getConfig().getString("tellformatfrom").replaceAll("sender_", "")));
				echo = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(mcp.getPlayer(), plugin.getConfig().getString("tellformatto").replaceAll("sender_", "")));
				spy = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(mcp.getPlayer(), plugin.getConfig().getString("tellformatspy").replaceAll("sender_", "")));

				send = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(player.getPlayer(), send.replaceAll("receiver_", ""))) + msg;
				echo = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(player.getPlayer(), echo.replaceAll("receiver_", ""))) + msg;
				spy = Format.FormatStringAll(PlaceholderAPI.setBracketPlaceholders(player.getPlayer(), spy.replaceAll("receiver_", ""))) + msg;

				if (ignored) {
					mcp.setReplyPlayer(player.getUUID());
					PrivateMessages.send(plugin, mcp.getPlayer(), echo, player.getName());
					return true;
				}

				var personalResult = ChatFeatures.censor(plugin, org.bukkit.ChatColor.stripColor(msg));
				player.setReplyPlayer(mcp.getUUID());
				mcp.setReplyPlayer(player.getUUID());
				PrivateMessages.send(plugin, player.getPlayer(), ChatFeatures.incoming(send, msg, personalResult, mcp, player), mcp.getName());
				ChatFeatures.record(plugin, mcp, "DirectMessage", player.getUUID(), msg, true);
				PrivateMessages.send(plugin, mcp.getPlayer(), echo, player.getName());
				if (player.hasNotifications()) {
					Format.playMessageSound(player);
				}
				if (!mcp.getPlayer().hasPermission("venturechat.spy.override")) {
					for (MineverseChatPlayer sp : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
						if (sp.getName().equals(mcp.getName()) || sp.getName().equals(player.getName())) {
							continue;
						}
						if (sp.isSpy()) {
							sp.getPlayer().sendMessage(ChatFeatures.incoming(spy, msg, personalResult, mcp, sp));
						}
					}
				}
			}
		}
		if (args.length == 1) {
			if (args[0].length() > 0) {
				if (!mcp.hasConversation() || (mcp.hasConversation() && !mcp.getConversation().toString().equals(player.getUUID().toString()))) {
					mcp.setConversation(player.getUUID());
					if (!ignored && !mcp.getPlayer().hasPermission("venturechat.spy.override")) {
						for (MineverseChatPlayer sp : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
							if (sp.getName().equals(mcp.getName())) {
								continue;
							}
							if (sp.isSpy()) {
								sp.getPlayer().sendMessage(LocalizedMessage.ENTER_PRIVATE_CONVERSATION_SPY.toString().replace("{player_sender}", mcp.getName())
										.replace("{player_receiver}", player.getName()));
							}
						}
					}
					mcp.getPlayer().sendMessage(LocalizedMessage.ENTER_PRIVATE_CONVERSATION.toString().replace("{player_receiver}", player.getName()));
				} else {
					mcp.setConversation(null);
					if (!ignored && !mcp.getPlayer().hasPermission("venturechat.spy.override")) {
						for (MineverseChatPlayer sp : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
							if (sp.getName().equals(mcp.getName())) {
								continue;
							}
							if (sp.isSpy()) {
								sp.getPlayer().sendMessage(LocalizedMessage.EXIT_PRIVATE_CONVERSATION_SPY.toString().replace("{player_sender}", mcp.getName())
										.replace("{player_receiver}", player.getName()));
							}
						}
					}
					mcp.getPlayer().sendMessage(LocalizedMessage.EXIT_PRIVATE_CONVERSATION.toString().replace("{player_receiver}", player.getName()));
				}
			}
		}
		return true;
	}

}
