package mineverse.Aust1n46.chat.settings;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.*;
import mineverse.Aust1n46.chat.channel.ChatChannel;
import mineverse.Aust1n46.chat.database.PlayerData;

/** Inventory identity and viewer UUID are authoritative; item names/lore never execute actions. */
public final class ChatSettingsMenu implements Listener {
    private static final java.util.concurrent.Semaphore NAME_BATCHES = new java.util.concurrent.Semaphore(32);
    private static final Set<UUID> NAME_VIEWERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static long nameGeneration;
    private final MineverseChat plugin;
    public ChatSettingsMenu(MineverseChat plugin) { this.plugin=plugin; }
    enum Page { SETTINGS, IGNORES, CHANNELS }
    record Action(String kind, String value) {}
    static final class View implements InventoryHolder {
        final long generation=nameGeneration;
        final UUID owner; final Page page; final int offset;
        final Map<Integer,Action> actions=new HashMap<>();
        Inventory inventory;
        View(UUID owner,Page page,int offset) { this.owner=owner;this.page=page;this.offset=offset; }
        public Inventory getInventory() { return inventory; }
    }
    public void open(Player player) { open(player,Page.SETTINGS,0); }
    private void open(Player player,Page page,int requested) {
        MineverseChatPlayer state=MineverseChatAPI.getOnlineMineverseChatPlayer(player);
        if(state==null) return;
        List<UUID> ignores=new ArrayList<>(state.getIgnores()); ignores.sort(Comparator.comparing(UUID::toString));
        List<ChatChannel> channels=ChatChannel.getChatChannels().stream().filter(c->!c.hasPermission() || player.hasPermission(c.getPermission())).sorted(Comparator.comparing(ChatChannel::getName)).toList();
        int count=page==Page.IGNORES ? ignores.size() : channels.size();
        int offset=Math.max(0,Math.min(requested,Math.max(0,(count-1)/45*45)));
        View view=new View(player.getUniqueId(),page,offset);
        view.inventory=Bukkit.createInventory(view,54,page==Page.SETTINGS?"Chat settings":page==Page.IGNORES?"Ignored players":"Chat channels");
        if(page==Page.SETTINGS) {
            put(view,11,Material.PAPER,"Private messages: "+on(state.getMessageToggle()),player.hasPermission("venturechat.messagetoggle")?"pm":"locked","", "Choose whether players can message you.","Staff with bypass permission can still contact you.");
            put(view,13,Material.NOTE_BLOCK,"Message sounds: "+on(state.hasNotifications()),"sounds","","Play a sound for incoming private messages.");
            put(view,15,Material.SHIELD,"Personal filter: "+on(state.hasPersonalFilter()),"filter","", "Hide matching incoming messages just for you.","Does not warn or punish the sender.",plugin.getChatFeatures().filterStatus());
            put(view,29,Material.PLAYER_HEAD,"Ignored players ("+ignores.size()+")","ignores","","Click a player to stop ignoring them.","To ignore someone: /ignore <name>");
            put(view,33,Material.BOOK,"Channel subscriptions","channels","","Choose which permitted channels you receive.");
            if(state.hasConversation()) {
                var target=MineverseChatAPI.getOnlineMineverseChatPlayer(state.getConversation());
                put(view,31,Material.WRITABLE_BOOK,"Conversation: "+(target==null?"offline player":target.getName()),"exit","","Your normal chat is directed to this player.","Click to end this private conversation.");
            }
        } else if(page==Page.IGNORES) {
            for(int i=offset;i<Math.min(offset+45,ignores.size());i++) {
                UUID id=ignores.get(i); int slot=i-offset;
                var online=Bukkit.getPlayer(id);
                String cached=MineverseChatAPI.getCachedName(id);
                String name=online!=null?online.getName():cached!=null?cached:id.toString();
                put(view,slot,Material.PLAYER_HEAD,name,"unignore",id.toString(),"Click to stop ignoring this player.",id.toString());

            }
        } else {
            for(int i=offset;i<Math.min(offset+45,channels.size());i++) {
                var channel=channels.get(i);boolean locked=locked(state,channel);
                put(view,i-offset,state.isListening(channel.getName())?Material.LIME_DYE:Material.GRAY_DYE,
                        channel.getName()+": "+on(state.isListening(channel.getName())),locked?"locked":"channel",channel.getName(),
                        locked?"Required or currently active channel.":"Click to toggle listening.");
            }
        }
        if(page!=Page.SETTINGS) {
            put(view,49,Material.ARROW,"Back to settings","settings","");
            if(offset>0) put(view,45,Material.ARROW,"Previous page","previous","");
            if(offset+45<count) put(view,53,Material.ARROW,"Next page","next","");
        } else put(view,49,Material.BARRIER,"Close","close","");
        player.openInventory(view.inventory);
        if(page==Page.IGNORES && NAME_VIEWERS.add(view.owner)) {
            if(NAME_BATCHES.tryAcquire()) refreshNames(player,view,ignores.subList(offset,Math.min(offset+45,ignores.size())),0);
            else NAME_VIEWERS.remove(view.owner);
        }
    }
    public static synchronized void resetNameLookups() { nameGeneration++; NAME_VIEWERS.clear(); NAME_BATCHES.drainPermits(); NAME_BATCHES.release(32); }
    private static synchronized void finishNames(View view) { if(view.generation==nameGeneration) {NAME_VIEWERS.remove(view.owner); NAME_BATCHES.release();} }
    private void refreshNames(Player player,View view,List<UUID> ids,int index) {
        if(view.generation!=nameGeneration || index>=ids.size() || !plugin.isEnabled() || !player.isOnline() || player.getOpenInventory().getTopInventory()!=view.inventory) {
            finishNames(view);return;
        }
        UUID id=ids.get(index);
        try {
            // At most one lookup per open viewer and 32 globally; page changes stop the chain.
            PlayerData.findByUuidAsync(id).whenComplete((result,error)->{
                if(!plugin.isEnabled()) {finishNames(view);return;}
                try { Bukkit.getScheduler().runTask(plugin,()->{
                    if(player.isOnline() && player.getOpenInventory().getTopInventory()==view.inventory && error==null && result.isPresent())
                        put(view,index,Material.PLAYER_HEAD,result.get().name(),"unignore",id.toString(),"Click to stop ignoring this player.",id.toString());
                    refreshNames(player,view,ids,index+1);
                }); } catch(RuntimeException disabled) {finishNames(view);}
            });
        } catch(RuntimeException unavailable) {finishNames(view);}
    }
    private boolean locked(MineverseChatPlayer state,ChatChannel channel) {
        return channel==ChatChannel.getDefaultChannel() || state.getCurrentChannel()==channel ||
                plugin.getConfig().getStringList("chat-settings.locked-channels").stream().anyMatch(s->s.equalsIgnoreCase(channel.getName()));
    }
    private static String on(boolean enabled) { return enabled?"ON":"OFF"; }
    private static void put(View view,int slot,Material material,String name,String kind,String value,String... lore) {
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(ChatColor.YELLOW+name);
        meta.setLore(Arrays.stream(lore).map(s->ChatColor.GRAY+s).toList());item.setItemMeta(meta);
        view.inventory.setItem(slot,item);view.actions.put(slot,new Action(kind,value));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(view.owner)) return;
        Action action=view.actions.get(event.getRawSlot());if(action==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=view.inventory) return;
            var state=MineverseChatAPI.getOnlineMineverseChatPlayer(player);if(state==null)return;
            Page page=view.page;int offset=view.offset;
            switch(action.kind()) {
                case "pm" -> { if(player.hasPermission("venturechat.messagetoggle")) state.setMessageToggle(!state.getMessageToggle()); }
                case "sounds" -> state.setNotifications(!state.hasNotifications());
                case "filter" -> state.setPersonalFilter(!state.hasPersonalFilter());
                case "unignore" -> state.removeIgnore(UUID.fromString(action.value()));
                case "ignores" -> {page=Page.IGNORES;offset=0;}
                case "channels" -> {page=Page.CHANNELS;offset=0;}
                case "settings" -> {page=Page.SETTINGS;offset=0;}
                case "previous" -> offset-=45;
                case "next" -> offset+=45;
                case "exit" -> state.setConversation(null);
                case "close" -> {player.closeInventory();return;}
                case "channel" -> {
                    var channel=ChatChannel.getChannel(action.value());
                    if(channel!=null && !locked(state,channel) && (!channel.hasPermission() || player.hasPermission(channel.getPermission()))) {
                        if(state.isListening(channel.getName())) state.removeListening(channel.getName());else state.addListening(channel.getName());
                    }
                }
                default -> {return;}
            }
            open(player,page,offset);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }
}
