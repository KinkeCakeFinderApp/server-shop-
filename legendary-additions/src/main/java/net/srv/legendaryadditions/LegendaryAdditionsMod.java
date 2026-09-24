package net.srv.legendaryadditions;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.logging.Level;
import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.command.AdminCommand;
import net.srv.legendaryadditions.admin.command.RodGiveCommand;
import net.srv.legendaryadditions.admin.dimension.AdminDimension;
import net.srv.legendaryadditions.admin.effect.ExplosionGuard;
import net.srv.legendaryadditions.admin.rod.RodAuthenticator;
import net.srv.legendaryadditions.admin.rod.RodKind;
import net.srv.legendaryadditions.admin.rod.RodListener;
import net.srv.legendaryadditions.admin.rod.RodRegistry;
import net.srv.legendaryadditions.custom.DrillListener;
import net.srv.legendaryadditions.custom.LegendaryAdditionsCommand;
import net.srv.legendaryadditions.custom.PaxelListener;
import net.srv.legendaryadditions.custom.RiftcasterListener;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendaryAdditionsMod extends JavaPlugin {
   public static JavaPlugin plugin;
   public static Server server;

   private volatile AdminSettings settings;

   @Override
   public void onEnable() {
      plugin = this;
      server = this.getServer();

      this.saveDefaultConfig();
      this.settings = AdminSettings.load(this.getConfig());

      RodAuthenticator authenticator;
      try {
         authenticator = RodAuthenticator.loadOrCreate(this.getDataPath().resolve("rod-secret.key"));
      } catch (IOException | IllegalArgumentException ex) {
         this.getLogger().log(Level.SEVERE, "Could not load rod-secret.key - disabling plugin", ex);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }
      RodRegistry registry = new RodRegistry(authenticator);
      AdminDimension dimension = new AdminDimension(this, () -> this.settings);

      this.getServer().getPluginManager().registerEvents(new RiftcasterListener(), this);
      this.getServer().getPluginManager().registerEvents(new DrillListener(), this);
      this.getServer().getPluginManager().registerEvents(new PaxelListener(this), this);
      this.getServer().getPluginManager().registerEvents(new RodListener(this, registry, () -> this.settings), this);
      this.getServer().getPluginManager().registerEvents(new ExplosionGuard(), this);

      this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
         var commands = event.registrar();
         commands.register("legendary_additions", "Give legendary items to players.", new LegendaryAdditionsCommand());
         commands.register("admin", "Enter the Admin dimension, or /admin return to leave.", new AdminCommand(dimension));
         for (RodKind kind : RodKind.values()) {
            if (kind.hasSingleUse()) {
               RodGiveCommand single = new RodGiveCommand(this, registry, kind, false);
               commands.register(single.label(), single.description(), single);
            }
            RodGiveCommand reusable = new RodGiveCommand(this, registry, kind, true);
            commands.register(reusable.label(), reusable.description(), reusable);
         }
      });

      if (dimension.world() == null) {
         this.getLogger().warning("Admin dimension '" + this.settings.adminWorldKey()
               + "' is not loaded yet. It is created by the plugin's datapack - restart the server once if this is the first install.");
      }
   }

   @Override
   public void onDisable() {
      // Folia/Paper cancel all of this plugin's scheduled tasks automatically; effects are temporary by design.
   }
}
