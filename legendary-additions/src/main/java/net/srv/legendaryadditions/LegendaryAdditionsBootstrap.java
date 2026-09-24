package net.srv.legendaryadditions;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Registers the built-in datapack that defines the persistent Admin dimension
 * ({@code adminplugin:admin}). Registering it at bootstrap means the dimension exists from the
 * very first start and is saved and reloaded with the server like any other dimension.
 */
public final class LegendaryAdditionsBootstrap implements PluginBootstrap {
   private static final String PACK_PATH = "/admin_dimension_pack";

   @Override
   public void bootstrap(BootstrapContext context) {
      context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
         URL pack = LegendaryAdditionsBootstrap.class.getResource(PACK_PATH);
         if (pack == null) {
            context.getLogger().error("Admin dimension datapack is missing from the plugin jar.");
            return;
         }
         try {
            event.registrar().discoverPack(pack.toURI(), "admin_dimension", configurer -> configurer.autoEnableOnServerStart(true));
         } catch (URISyntaxException | IOException ex) {
            context.getLogger().error("Could not register the Admin dimension datapack", ex);
         }
      }));
   }
}
