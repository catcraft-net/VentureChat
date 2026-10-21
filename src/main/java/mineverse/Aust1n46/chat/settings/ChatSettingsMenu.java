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
    private static volatile long nameGeneration;
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
        view.inventory=Bukkit.createInventory(view,54,page==Page.SETTINGS?"Chat Settings":page==Page.IGNORES?"Ignored Players":"Chat Channels");
        if(page==Page.SETTINGS) {
            put(view,11,Material.PAPER,"Private messages: "+on(state.getMessageToggle()),player.hasPermission("venturechat.messagetoggle")?"pm":"locked","", "Choose whether players", "can message you.");
            put(view,13,Material.NOTE_BLOCK,"Message sounds: "+on(state.hasNotifications()),"sounds","","Play a sound for incoming", "private messages.");
            put(view,15,Material.SHIELD,"Personal filter: "+on(state.hasPersonalFilter()),"filter","", "Replace filtered words", "with asterisks in your chat.");
            put(view,29,Material.PLAYER_HEAD,"Ignored players ("+ignores.size()+")","ignores","","View players you ignore.", "Click a player to unignore.", "Add: /ignore <name>");
            applyOnlineHead(view,29,player);
            if (plugin.getChatFeatures()!=null && !plugin.getChatFeatures().filterStatus().startsWith("AVAILABLE")) {
                var item=view.inventory.getItem(15);var meta=item.getItemMeta();
                var lore=new ArrayList<>(meta.getLore());lore.add(ChatColor.GRAY+"Temporarily unavailable.");meta.setLore(lore);item.setItemMeta(meta);
            }
            put(view,33,Material.BOOK,"Channel subscriptions","channels","","Choose which channels", "appear in your chat.");
            if(state.hasConversation()) {
                var target=MineverseChatAPI.getOnlineMineverseChatPlayer(state.getConversation());
                put(view,31,Material.WRITABLE_BOOK,"Conversation: "+(target==null?"offline player":target.getName()),"exit","","Your chat goes to this player.", "Click to end the conversation.");
            }
        } else if(page==Page.IGNORES) {
            for(int i=offset;i<Math.min(offset+45,ignores.size());i++) {
                UUID id=ignores.get(i); int slot=i-offset;
                var online=Bukkit.getPlayer(id);
                String cached=MineverseChatAPI.getCachedName(id);
                String name=online!=null?online.getName():cached!=null?cached:id.toString();
                put(view,slot,Material.PLAYER_HEAD,name,"unignore",id.toString(),"Click to stop ignoring", "this player.");
                if(online!=null) applyOnlineHead(view,slot,online);
            }
        } else {
            for(int i=offset;i<Math.min(offset+45,channels.size());i++) {
                var channel=channels.get(i);boolean locked=locked(state,channel);
                put(view,i-offset,state.isListening(channel.getName())?Material.LIME_DYE:Material.GRAY_DYE,
                        channel.getName()+": "+on(state.isListening(channel.getName())),locked?"locked":"channel",channel.getName(),
                        locked?"Required or active channel.":"Click to toggle listening.");
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
        if(view.generation!=nameGeneration || !plugin.isEnabled() || !player.isOnline()) {
            finishNames(view);return;
        }
        var current=player.getOpenInventory().getTopInventory();
        if(current!=view.inventory) {
            // Transfer the existing permit only after the old request completes. Rapid page
            // changes must neither skip the new page nor start overlapping network requests.
            if(current.getHolder() instanceof View next && next.page==Page.IGNORES &&
                    next.owner.equals(view.owner) && next.generation==nameGeneration) {
                var nextIds=new TreeMap<>(next.actions).values().stream()
                        .filter(action->action.kind().equals("unignore"))
                        .map(action->UUID.fromString(action.value())).toList();
                refreshNames(player,next,nextIds,0);
            } else finishNames(view);
            return;
        }
        if(index>=ids.size()) {finishNames(view);return;}
        UUID id=ids.get(index);
        try {
            // At most one lookup per viewer and 32 globally; navigation reuses the chain.
            PlayerData.findByUuidAsync(id).whenComplete((result,error)->{
                if(!plugin.isEnabled()) {finishNames(view);return;}
                try { Bukkit.getScheduler().runTask(plugin,()->{
                    if(view.generation!=nameGeneration || !player.isOnline() || player.getOpenInventory().getTopInventory()!=view.inventory) {refreshNames(player,view,ids,index);return;}
                    if(error==null && result.isPresent())
                        put(view,index,Material.PLAYER_HEAD,result.get().name(),"unignore",id.toString(),"Click to stop ignoring", "this player.");
                    var online=Bukkit.getPlayer(id);
                    if(online!=null) {applyOnlineHead(view,index,online);refreshNames(player,view,ids,index+1);return;}
                    // Same bounded, sequential page chain as names; never block the server for skins.
                    try {
                        Bukkit.createPlayerProfile(id).update().whenComplete((profile,failure)->{
                            if(!plugin.isEnabled()) {finishNames(view);return;}
                            try { Bukkit.getScheduler().runTask(plugin,()->{
                                if(view.generation==nameGeneration && player.isOnline() && player.getOpenInventory().getTopInventory()==view.inventory && failure==null && profile!=null) {
                                    var item=view.inventory.getItem(index);
                                    if(item!=null && item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull && !profile.getTextures().isEmpty()) {
                                        skull.setOwnerProfile(profile);item.setItemMeta(skull);
                                    }
                                }
                                refreshNames(player,view,ids,index+1);
                            }); } catch(RuntimeException disabled) {finishNames(view);}
                        });
                    } catch(RuntimeException unavailable) {refreshNames(player,view,ids,index+1);}

                }); } catch(RuntimeException disabled) {finishNames(view);}
            });
        } catch(RuntimeException unavailable) {finishNames(view);}
    }
    private static void applyOnlineHead(View view,int slot,Player player) {
        var item=view.inventory.getItem(slot);
        if(item!=null && item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            var profile=player.getPlayerProfile();
            if(profile!=null) {skull.setPlayerProfile(profile);item.setItemMeta(skull);}
        }
    }
    private boolean locked(MineverseChatPlayer state,ChatChannel channel) {
        return channel==ChatChannel.getDefaultChannel() || state.getCurrentChannel()==channel ||
                plugin.getConfig().getStringList("chat-settings.locked-channels").stream().anyMatch(s->s.equalsIgnoreCase(channel.getName()));
    }
    private static String on(boolean enabled) { return (enabled?ChatColor.GREEN:ChatColor.RED).toString()+ChatColor.BOLD+(enabled?"ON":"OFF"); }
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
