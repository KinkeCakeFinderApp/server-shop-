package net.srv.legendaryadditions.shopadmin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.regex.Pattern;
import net.srv.legendaryadditions.admin.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * /shopadmin opens the shop admin GUI. "/shop admin" (and FoliaShop's other aliases followed by
 * "admin") is rewritten to /shopadmin before FoliaShop sees it, since /shop belongs to FoliaShop.
 */
public final class ShopAdminCommand implements BasicCommand, Listener {
   private static final Pattern SHOP_ADMIN = Pattern.compile("(?i)^/(?:foliashop:)?(?:shop|shops|fshop|foliashop|sp)\\s+admin\\s*$");

   private final ShopAdminGui gui;

   public ShopAdminCommand(ShopAdminGui gui) {
      this.gui = gui;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      if (!(source.getExecutor() instanceof Player player)) {
         Messages.error(source.getSender(), "Only players can open the shop admin GUI.");
         return;
      }
      this.gui.openMain(player);
   }

   @Override
   public boolean canUse(CommandSender sender) {
      return !(sender instanceof Player player) || player.hasPermission(ShopAdminGui.PERMISSION);
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (SHOP_ADMIN.matcher(event.getMessage().trim()).matches()) {
         event.setMessage("/shopadmin");
      }
   }
}
