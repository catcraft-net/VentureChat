package mineverse.Aust1n46.chat.command.message;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.localization.LocalizedMessage;

public class MessageToggle extends Command {
	/**
	 * Player permission required to flip the private message setting.
	 */
	public static final String PERMISSION = "venturechat.messagetoggle";

	/**
	 * Argument accepted by the message command to flip the private message setting,
	 * for example {@code /pm toggle}.
	 */
	public static final String TOGGLE_ARGUMENT = "toggle";

	public MessageToggle() {
		super("messagetoggle");
	}

	@Override
	public boolean execute(CommandSender sender, String command, String[] args) {
		if (!(sender instanceof Player)) {
			Bukkit.getServer().getConsoleSender().sendMessage(LocalizedMessage.COMMAND_MUST_BE_RUN_BY_PLAYER.toString());
			return true;
		}
		toggleMessages(MineverseChatAPI.getOnlineMineverseChatPlayer((Player) sender));
		return true;
	}

	/**
	 * Flips the supplied player's private message setting, tells them the new state
	 * and persists the change for the rest of the network.
	 *
	 * @param mcp
	 *            the player whose private message setting should be flipped
	 */
	public static void toggleMessages(MineverseChatPlayer mcp) {
		if (!mcp.getPlayer().hasPermission(PERMISSION)) {
			mcp.getPlayer().sendMessage(LocalizedMessage.COMMAND_NO_PERMISSION.toString());
			return;
		}
		mcp.setMessageToggle(!mcp.getMessageToggle());
		mcp.getPlayer().sendMessage((mcp.getMessageToggle() ? LocalizedMessage.MESSAGE_TOGGLE_ON : LocalizedMessage.MESSAGE_TOGGLE_OFF).toString());
		MineverseChat.synchronize(mcp, true);
	}
}
