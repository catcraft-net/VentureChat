package mineverse.Aust1n46.chat.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;
import mineverse.Aust1n46.chat.database.PlayerData;
import mineverse.Aust1n46.chat.utilities.Format;
import mineverse.Aust1n46.chat.utilities.UUIDFetcher;

/**
 * Manages player login and logout events.
 * 
 * @author Aust1n46
 */
public class LoginListener implements Listener {
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
		try {
			PlayerData.prepareLogin(event.getUniqueId(), event.getName());
		} catch (Exception exception) {
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
					"Your VentureChat data could not be loaded. Please try joining again.");
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
		MineverseChatPlayer mcp = MineverseChatAPI.getOnlineMineverseChatPlayer(playerQuitEvent.getPlayer());
		if (mcp == null) return;
		PlayerData.savePlayerData(mcp);
		mcp.clearMessages();
		mcp.setOnline(false);
		MineverseChatAPI.removeMineverseChatOnlinePlayerToMap(mcp);
	}
	
	void handleNameChange(MineverseChatPlayer mcp, Player eventPlayerInstance) {
		Bukkit.getConsoleSender().sendMessage(Format.FormatStringAll("&8[&eVentureChat&8]&e - Detected Name Change. Old Name:&c " + mcp.getName() + " &eNew Name:&c " + eventPlayerInstance.getName()));
		MineverseChatAPI.removeNameFromMap(mcp.getName());
		mcp.setName(eventPlayerInstance.getName());
		MineverseChatAPI.addNameToMap(mcp);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerJoin(PlayerJoinEvent event) throws Exception {
		Player player = event.getPlayer();
		String name = player.getName();
		MineverseChatPlayer mcp = PlayerData.consumeLogin(player.getUniqueId(), name);
		MineverseChatAPI.addMineverseChatPlayerToMap(mcp);
		MineverseChatAPI.addNameToMap(mcp);
		UUIDFetcher.checkOfflineUUIDWarning(mcp.getUUID());
		mcp.setOnline(true);
		mcp.setHasPlayed(false);
		MineverseChatAPI.addMineverseChatOnlinePlayerToMap(mcp);
		mcp.setJsonFormat();
		for(ChatChannel ch : ChatChannel.getAutojoinList()) {
			if(ch.hasPermission()) {
				if(mcp.getPlayer().hasPermission(ch.getPermission())) {
					mcp.addListening(ch.getName());
				}
			}
			else {
				mcp.addListening(ch.getName());
			}
		}
	}
}
