package chatfixture;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.*;
import mineverse.Aust1n46.chat.database.PlayerData;

/** Disposable-server fixture only: creates synthetic offline mailboxes and shuts down. */
public class MailProbe extends JavaPlugin {
    final UUID sender=UUID.fromString("00000000-0000-0000-0000-000000008811");
    MineverseChat vc; Object essentials,service,source; Method send; Class<?> userType;
    Object cachedTarget,blockedTarget,allowedTarget;
    public void onEnable() {later(100,this::start);}
    void later(long ticks,Runnable action) {Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable ex){ex.printStackTrace();write("FAIL "+ex);Bukkit.shutdown();}},ticks);}
    void require(boolean value,String label) {if(!value)throw new AssertionError(label);getLogger().info("PASS "+label);}
    void start() {
        try {
            vc=MineverseChat.getInstance();essentials=Bukkit.getPluginManager().getPlugin("Essentials");
            require(vc.isEnabled() && essentials!=null && ((org.bukkit.plugin.Plugin)essentials).isEnabled(),"both plugins enabled");
            ClassLoader loader=essentials.getClass().getClassLoader();
            userType=loader.loadClass("net.ess3.api.IUser");var senderType=loader.loadClass("net.essentialsx.api.v2.services.mail.MailSender");
            service=essentials.getClass().getMethod("getMail").invoke(essentials);
            send=service.getClass().getMethod("sendMail",userType,senderType,String.class,long.class);
            source=Proxy.newProxyInstance(loader,new Class<?>[]{senderType},(p,m,a)->m.getName().equals("getUUID")?sender:"MailFixtureSender");
            UUID cached=UUID.randomUUID(),blocked=UUID.randomUUID(),allowed=UUID.randomUUID();
            cachedTarget=user(cached);blockedTarget=user(blocked);allowedTarget=user(allowed);
            var state=new MineverseChatPlayer(cached,"MailFixtureCached");state.addIgnore(sender);MineverseChatAPI.addMineverseChatPlayerToMap(state);
            send.invoke(service,cachedTarget,source,"must be blocked",0L);require(size(cachedTarget)==0,"cached VentureChat ignore blocks real Essentials mailbox write");
            state.removeIgnore(sender);send.invoke(service,cachedTarget,source,"allowed after unignore",0L);require(size(cachedTarget)==1,"unignore restores real delivery");
            var offline=new MineverseChatPlayer(blocked,"MailFixtureOffline");offline.addIgnore(sender);PlayerData.savePlayerData(offline);
            require(MineverseChatAPI.getCachedMineverseChatPlayer(blocked)==null,"offline recipient is not in VentureChat cache");
            later(20,()->{try {
                send.invoke(service,blockedTarget,source,"offline blocked",0L);
                send.invoke(service,allowedTarget,source,"offline allowed",System.currentTimeMillis()+60000);
                later(20,()->finish());
            }catch(Exception ex){throw new RuntimeException(ex);}});
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    Object user(UUID id)throws Exception{
        Path data=((org.bukkit.plugin.Plugin)essentials).getDataFolder().toPath().resolve("userdata");
        Files.createDirectories(data);Files.writeString(data.resolve(id+".yml"),"last-account-name: MailFixture\n",StandardOpenOption.CREATE_NEW);
        Object target=essentials.getClass().getMethod("getUser",UUID.class).invoke(essentials,id);
        require(target!=null,"synthetic offline Essentials user loaded");return target;
    }
    int size(Object target)throws Exception{return ((List<?>)userType.getMethod("getMailMessages").invoke(target)).size();}
    void finish() {try {
        require(size(blockedTarget)==0,"stored offline ignore blocks real mailbox write");
        require(size(allowedTarget)==1,"uncached allowed mail delivered exactly once");
        Bukkit.getPluginManager().disablePlugin(vc);
        send.invoke(service,allowedTarget,source,"bridge removed",0L);
        require(size(allowedTarget)==2,"mail listener unregisters cleanly on disable");
        write("PASS: cached ignore; unignore; stored offline ignore; uncached allowed mail; listener shutdown");Bukkit.shutdown();
    }catch(Exception ex){throw new RuntimeException(ex);}}
    void write(String text) {try{Files.writeString(Path.of("mail-probe-result.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ex){throw new RuntimeException(ex);}}
}
