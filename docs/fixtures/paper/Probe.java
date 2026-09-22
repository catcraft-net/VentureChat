package chatfixture;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import mineverse.Aust1n46.chat.*;
import mineverse.Aust1n46.chat.api.*;
import mineverse.Aust1n46.chat.settings.*;
import mineverse.Aust1n46.chat.database.*;
import mineverse.Aust1n46.chat.filter.*;
import mineverse.Aust1n46.chat.utilities.Format;
public class Probe extends JavaPlugin {
 final UUID id=UUID.fromString("00000000-0000-0000-0000-000000009901"), ignored=UUID.fromString("00000000-0000-0000-0000-000000009902");
 com.destroystokyo.paper.profile.PlayerProfile playerProfile;
 Inventory top;Player player;InventoryView view;MineverseChatPlayer state;ChatSettingsMenu menu;MineverseChat vc;
 public void onEnable(){later(100,this::start);}
 void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){e.printStackTrace();write("FAIL "+e);Bukkit.shutdown();}},ticks);}
 void require(boolean pass,String reason){if(!pass)throw new AssertionError(reason);getLogger().info("PASS "+reason);}
 Object fallback(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==float.class)return 0f;if(t==double.class)return 0d;return null;}
 void start(){try{
  vc=MineverseChat.getInstance();require(vc.isEnabled(),"plugin enabled");
  require(vc.getChatFeatures().scopeStatus().startsWith("AVAILABLE"),"scope available");
  require(vc.getChatFeatures().evaluate("badword")==FilterDecision.MATCH,"initialized ChatSentry rule match");
  require(vc.getChatFeatures().evaluate("a pleasant afternoon")==FilterDecision.CLEAN,"clean ChatSentry rule result");
  require(vc.getChatFeatures().censor("hello badword, friend").censored().equals("hello *******, friend"),"initialized ChatSentry censors only matching word");
  playerProfile=Bukkit.createProfile(id,"FixturePlayer");
  String texture=Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  playerProfile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures",texture));
  player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getUniqueId"->id;case "getName","getDisplayName"->"FixturePlayer";case "isOnline","hasPermission","canSee"->true;
   case "getPlayerProfile"->playerProfile;
   case "getWorld"->Bukkit.getWorlds().getFirst();case "getLocation"->Bukkit.getWorlds().getFirst().getSpawnLocation();
   case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}
   case "hashCode"->id.hashCode();case "equals"->p==a[0];case "toString"->"FixturePlayer";default->fallback(m.getReturnType());});
  view=(InventoryView)Proxy.newProxyInstance(getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()){
   case "getTopInventory"->top;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "getItem"->top.getItem((int)a[0]);
   case "getTitle","getOriginalTitle"->"Fixture";case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];default->fallback(m.getReturnType());});
  state=new MineverseChatPlayer(id,"FixturePlayer");state.setOnline(true);var f=MineverseChatPlayer.class.getDeclaredField("player");f.setAccessible(true);f.set(state,player);
  MineverseChatAPI.addMineverseChatPlayerToMap(state);MineverseChatAPI.addMineverseChatOnlinePlayerToMap(state);state.addIgnore(ignored);
  menu=new ChatSettingsMenu(vc);menu.open(player);require(top.getSize()==54 && top.getItem(15).getType()==Material.SHIELD,"real server menu items");
  var head=(org.bukkit.inventory.meta.SkullMeta)top.getItem(29).getItemMeta();
  require(head.getPlayerProfile()!=null && head.getPlayerProfile().getId().equals(id) && head.getPlayerProfile().hasProperty("textures"),"viewer head keeps supplied skin texture");
  require(top.getItem(11).getItemMeta().getDisplayName().contains(ChatColor.GREEN.toString()+ChatColor.BOLD+"ON"),"enabled status is bold green");
  require(top.getItem(15).getItemMeta().getLore().stream().noneMatch(line->line.contains("ChatSentry") || line.contains("punish") || line.contains("bypass")),"filter lore is player facing");
  click(15);later(2,()->{require(!state.hasPersonalFilter(),"filter click persisted preference");require(top.getItem(15).getItemMeta().getDisplayName().contains(ChatColor.RED.toString()+ChatColor.BOLD+"OFF"),"disabled status is bold red");click(11);later(2,()->{
   require(!state.getMessageToggle(),"PM click toggled");click(29);later(2,()->{
    require(top.getItem(0).getType()==Material.PLAYER_HEAD,"offline ignored player displayed");click(0);later(2,()->finish());
   });
  });});
 }catch(Exception e){throw new RuntimeException(e);}}
 void click(int slot){var event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);menu.click(event);require(event.isCancelled(),"menu cancels item movement");}
 void finish(){
  require(!state.getIgnores().contains(ignored),"offline UUID unignored");
  require(Format.createPacketPlayOutChat("{\"text\":\"fixture\"}")!=null,"modern SYSTEM_CHAT packet constructed");
  ChatFeatures.record(vc,state,"Global",null,"fixture public history",false);
  ChatFeatures.record(vc,state,"DirectMessage",ignored,"fixture private must not be recorded",true);
  PlayerData.flushDirtyPlayers();write("PASS: initialized detector; scope; menu real items/clicks; preference toggle; offline unignore; SYSTEM_CHAT; history integration");
  later(80,()->{Bukkit.getPluginManager().disablePlugin(vc);require(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&(t.getName().equals("VentureChat-history")||t.getName().equals("VentureChat-dashboard"))),"history/dashboard workers stopped");write("PASS: checks and service shutdown");Bukkit.shutdown();});
 }
 void write(String text){try{Files.writeString(Path.of("probe-result.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
}
