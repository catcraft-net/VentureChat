package mineverse.Aust1n46.chat.command.chat;

import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.database.PlayerData;
import mineverse.Aust1n46.chat.localization.LocalizedMessage;
import mineverse.Aust1n46.chat.utilities.Format;

public class Chatreload extends Command {
	private MineverseChat plugin = MineverseChat.getInstance();

	public Chatreload() {
		super("chatreload");
	}

	@Override
	public boolean execute(CommandSender sender, String command, String[] args) {
		if (sender.hasPermission("venturechat.reload")) {
			PlayerData.flushDirtyPlayers();
			plugin.reloadConfig();
			MineverseChat.initializeConfigReaders();
			for (MineverseChatPlayer mcp : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
				String currentName = mcp.getCurrentChannel().getName();
				mcp.setCurrentChannel(mineverse.Aust1n46.chat.channel.ChatChannel.isChannel(currentName)
						? mineverse.Aust1n46.chat.channel.ChatChannel.getChannel(currentName)
						: mineverse.Aust1n46.chat.channel.ChatChannel.getDefaultChannel());
				for (String channel : new HashSet<>(mcp.getListening())) {
					if (!mineverse.Aust1n46.chat.channel.ChatChannel.isChannel(channel)) {
						mcp.removeListening(channel);
					}
				}
				if (mcp.getListening().isEmpty()) {
					mcp.addListening(mineverse.Aust1n46.chat.channel.ChatChannel.getDefaultChannel().getName());
				}
				mcp.setJsonFormat();
			}

			Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&e - Config reloaded"));
			for (MineverseChatPlayer player : MineverseChatAPI.getOnlineMineverseChatPlayers()) {
				if (player.getPlayer().hasPermission("venturechat.reload")) {
					player.getPlayer().sendMessage(LocalizedMessage.CONFIG_RELOADED.toString());
				}
			}
			return true;
		}
		sender.sendMessage(LocalizedMessage.COMMAND_NO_PERMISSION.toString());
		return true;
	}
}
