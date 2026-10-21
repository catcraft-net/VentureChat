package mineverse.Aust1n46.chat.settings;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;
import org.mockito.MockedStatic;
import mineverse.Aust1n46.chat.MineverseChat;

public class ChatSettingsMenuTest {
    @Test public void allMovementTypesAreCancelledIncludingBottomInventory() {
        var menu=new ChatSettingsMenu(mock(MineverseChat.class));
        var owner=UUID.randomUUID();var holder=new ChatSettingsMenu.View(owner,ChatSettingsMenu.Page.SETTINGS,0);
        var inventory=mock(Inventory.class);when(inventory.getHolder()).thenReturn(holder);
        var view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(inventory);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(owner);
        for(ClickType type: new ClickType[]{ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.SWAP_OFFHAND,ClickType.DOUBLE_CLICK,ClickType.DROP}) {
            var event=mock(InventoryClickEvent.class);when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(player);
            when(event.getRawSlot()).thenReturn(60);when(event.getClick()).thenReturn(type);
            menu.click(event);verify(event).setCancelled(true);
        }
        var drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);
        menu.drag(drag);verify(drag).setCancelled(true);
    }
    @Test public void foreignViewerCannotScheduleOwnerAction() {
        var plugin=mock(MineverseChat.class);var menu=new ChatSettingsMenu(plugin);
        var holder=new ChatSettingsMenu.View(UUID.randomUUID(),ChatSettingsMenu.Page.SETTINGS,0);holder.actions.put(11,new ChatSettingsMenu.Action("pm",""));
        var inventory=mock(Inventory.class);when(inventory.getHolder()).thenReturn(holder);
        var view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(inventory);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var event=mock(InventoryClickEvent.class);when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(player);when(event.getRawSlot()).thenReturn(11);
        try(var bukkit=mockStatic(Bukkit.class)) {menu.click(event);bukkit.verifyNoInteractions();}
        verify(event).setCancelled(true);
    }
    @Test public void deferredActionDoesNothingAfterViewerChangesInventory() {
        var plugin=mock(MineverseChat.class);var menu=new ChatSettingsMenu(plugin);var owner=UUID.randomUUID();
        var holder=new ChatSettingsMenu.View(owner,ChatSettingsMenu.Page.SETTINGS,0);holder.actions.put(11,new ChatSettingsMenu.Action("pm",""));
        var inventory=mock(Inventory.class);holder.inventory=inventory;when(inventory.getHolder()).thenReturn(holder);
        var view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(inventory);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(owner);when(player.isOnline()).thenReturn(true);
        var replacement=mock(InventoryView.class);when(replacement.getTopInventory()).thenReturn(mock(Inventory.class));when(player.getOpenInventory()).thenReturn(replacement);
        var event=mock(InventoryClickEvent.class);when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(player);when(event.getRawSlot()).thenReturn(11);
        var scheduler=mock(BukkitScheduler.class);var task=org.mockito.ArgumentCaptor.forClass(Runnable.class);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);menu.click(event);verify(scheduler).runTask(eq(plugin),task.capture());task.getValue().run();
        }
        verify(player,never()).openInventory(any(Inventory.class));
    }
    @Test public void staleLookupContinuesOnCurrentIgnoredPlayersPage() throws Exception {
        ChatSettingsMenu.resetNameLookups();
        var plugin=mock(MineverseChat.class);when(plugin.isEnabled()).thenReturn(true);
        var menu=new ChatSettingsMenu(plugin);var owner=UUID.randomUUID();var ignored=UUID.randomUUID();
        var old=new ChatSettingsMenu.View(owner,ChatSettingsMenu.Page.IGNORES,0);old.inventory=mock(Inventory.class);
        var current=new ChatSettingsMenu.View(owner,ChatSettingsMenu.Page.IGNORES,45);current.inventory=mock(Inventory.class);
        current.actions.put(0,new ChatSettingsMenu.Action("unignore",ignored.toString()));
        when(current.inventory.getHolder()).thenReturn(current);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(owner);when(player.isOnline()).thenReturn(true);
        var open=mock(InventoryView.class);when(open.getTopInventory()).thenReturn(current.inventory);when(player.getOpenInventory()).thenReturn(open);
        var refresh=ChatSettingsMenu.class.getDeclaredMethod("refreshNames",Player.class,ChatSettingsMenu.View.class,java.util.List.class,int.class);
        refresh.setAccessible(true);
        try(var database=mockStatic(mineverse.Aust1n46.chat.database.PlayerData.class)) {
            database.when(()->mineverse.Aust1n46.chat.database.PlayerData.findByUuidAsync(ignored)).thenReturn(new java.util.concurrent.CompletableFuture<>());
            refresh.invoke(menu,player,old,java.util.List.of(ignored),0);
            database.verify(()->mineverse.Aust1n46.chat.database.PlayerData.findByUuidAsync(ignored));
        } finally {ChatSettingsMenu.resetNameLookups();}
    }
    @Test public void explicitReplyLinkDoesNotUseMutableReplyTarget() {
        var component=PrivateMessages.replyLink("Alice: hello","Alice");
        org.junit.Assert.assertEquals(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/msg Alice "),component.clickEvent());
        org.junit.Assert.assertNull(PrivateMessages.replyLink("test","Alice;op me").clickEvent());
    }
}
