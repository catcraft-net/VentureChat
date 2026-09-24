package mineverse.Aust1n46.chat.command.chat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.settings.ChatSettingsMenu;

public final class ChatSettings extends Command {
    public ChatSettings() { super("chatsettings"); }
    @Override public boolean execute(CommandSender sender,String label,String[] args) {
        if(sender instanceof Player player) new ChatSettingsMenu(MineverseChat.getInstance()).open(player);
        else sender.sendMessage("Open chat settings in-game with /chatsettings.");
        return true;
    }
}
