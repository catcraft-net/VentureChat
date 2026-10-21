package mineverse.Aust1n46.chat.settings;

import org.bukkit.entity.Player;
import mineverse.Aust1n46.chat.MineverseChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class PrivateMessages {
    private PrivateMessages() {}
    public static Component replyLink(String formatted,String recipient) {
        if(!recipient.matches("[A-Za-z0-9_]{1,16}")) return LegacyComponentSerializer.legacySection().deserialize(formatted);
        return LegacyComponentSerializer.legacySection().deserialize(formatted)
                .clickEvent(ClickEvent.suggestCommand("/msg "+recipient+" "))
                .hoverEvent(HoverEvent.showText(Component.text("Message "+recipient)));
    }
    public static void send(MineverseChat plugin,Player player,String formatted,String recipient) {
        if(plugin.getConfig().getBoolean("private-messages.clickable-replies",true)) player.sendMessage(replyLink(formatted,recipient));
        else player.sendMessage(formatted);
    }
}
