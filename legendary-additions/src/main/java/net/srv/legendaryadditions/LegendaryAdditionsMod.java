package net.srv.legendaryadditions;

import net.srv.legendaryadditions.custom.DrillListener;
import net.srv.legendaryadditions.custom.LegendaryAdditionsCommand;
import net.srv.legendaryadditions.custom.PaxelListener;
import net.srv.legendaryadditions.custom.RiftcasterListener;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendaryAdditionsMod extends JavaPlugin {
   public static JavaPlugin plugin;
   public static Server server;

   public void onEnable() {
      plugin = this;
      server = this.getServer();
      this.getServer().getPluginManager().registerEvents(new RiftcasterListener(), this);
      this.getServer().getPluginManager().registerEvents(new DrillListener(), this);
      this.getServer().getPluginManager().registerEvents(new PaxelListener(this), this);
      LegendaryAdditionsCommand giveCommand = new LegendaryAdditionsCommand();
      this.getCommand("legendary_additions").setExecutor(giveCommand);
      this.getCommand("legendary_additions").setTabCompleter(giveCommand);
   }

   public void onDisable() {
   }
}
