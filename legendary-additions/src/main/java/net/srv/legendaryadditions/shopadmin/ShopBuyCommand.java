package net.srv.legendaryadditions.shopadmin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.srv.legendaryadditions.admin.util.Messages;
import net.srv.legendaryadditions.forge.LegendaryItems;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * la_shopbuy &lt;player&gt; &lt;category&gt; &lt;id&gt; - run by FoliaShop from the console after a
 * buyer paid the money part of a price. Takes the item part of the price from the buyer and hands
 * out the product; when the buyer does not have the items, nothing is taken and the money part is
 * paid back through Vault. Console only, so players cannot skip the money part.
 */
public final class ShopBuyCommand implements BasicCommand {
   private final Plugin plugin;
   private final FoliaShopStore store;

   public ShopBuyCommand(Plugin plugin, FoliaShopStore store) {
      this.plugin = plugin;
      this.store = store;
   }

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      CommandSender sender = source.getSender();
      if (!(sender instanceof ConsoleCommandSender)) {
         sender.sendMessage("This command is only run by FoliaShop from the console.");
         return;
      }
      if (args.length != 3) {
         sender.sendMessage("Usage: la_shopbuy <player> <category> <id>");
         return;
      }
      Player player = Bukkit.getPlayerExact(args[0]);
      if (player == null) {
         this.plugin.getLogger().warning("la_shopbuy: player " + args[0] + " is not online.");
         return;
      }
      String category = args[1];
      String id = args[2];
      this.store.purchase(category, id).whenComplete((purchase, error) -> player.getScheduler().run(this.plugin, task -> {
         if (error != null || purchase == null) {
            this.plugin.getLogger().log(Level.SEVERE, "la_shopbuy: could not read shop item " + category + "/" + id, error);
            Messages.error(player, "This shop item is set up wrong, tell an admin (" + category + "/" + id + ").");
            this.refund(player, purchase == null ? 0 : purchase.money());
            return;
         }
         this.complete(player, category, id, purchase);
      }, null));
   }

   private void complete(Player player, String category, String id, FoliaShopStore.Purchase purchase) {
      ItemStack product;
      if (purchase.legendaryId() != null) {
         product = this.legendary(purchase.legendaryId());
      } else {
         product = purchase.product() == null ? null : purchase.product().clone();
         if (product != null) {
            product.setAmount(purchase.amount());
         }
      }
      if (product == null) {
         Messages.error(player, "This shop item no longer exists, tell an admin (" + category + "/" + id + ").");
         this.refund(player, purchase.money());
         return;
      }

      PlayerInventory inventory = player.getInventory();
      List<String> missing = new ArrayList<>();
      for (ItemStack cost : purchase.cost()) {
         if (!inventory.containsAtLeast(cost, cost.getAmount())) {
            missing.add(cost.getAmount() + "x " + FoliaShopStore.itemName(cost));
         }
      }
      if (!missing.isEmpty()) {
         Messages.error(player, "You also need " + String.join(", ", missing) + " to buy this. Nothing was taken"
               + (purchase.money() > 0 ? " and your money was refunded." : "."));
         this.refund(player, purchase.money());
         return;
      }
      for (ItemStack cost : purchase.cost()) {
         inventory.removeItem(cost.clone());
      }
      inventory.addItem(product).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
      if (!purchase.cost().isEmpty()) {
         List<String> paid = purchase.cost().stream().map(c -> c.getAmount() + "x " + FoliaShopStore.itemName(c)).toList();
         Messages.success(player, "Paid " + String.join(", ", paid) + ".");
      }
      this.plugin.getLogger().info(player.getName() + " bought " + category + "/" + id + " (item price: "
            + purchase.cost().size() + " item types, money: " + purchase.money() + ").");
   }

   private ItemStack legendary(String id) {
      ItemStack builtIn = ShopAdminGui.builtIn(id);
      if (builtIn != null) {
         return builtIn;
      }
      LegendaryDef def = LegendaryItems.service() == null ? null : LegendaryItems.service().get(id);
      return def == null ? null : LegendaryItems.build(def);
   }

   /** Pays money back through whatever economy is registered with Vault; no-op for 0. */
   private void refund(OfflinePlayer player, double money) {
      if (money <= 0) {
         return;
      }
      try {
         for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
            if (service.getName().equals("net.milkbowl.vault.economy.Economy")) {
               RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(service);
               if (rsp != null) {
                  Method deposit = service.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                  deposit.invoke(rsp.getProvider(), player, money);
                  return;
               }
            }
         }
         this.plugin.getLogger().warning("la_shopbuy: no Vault economy to refund " + money + " to " + player.getName());
      } catch (ReflectiveOperationException | RuntimeException ex) {
         this.plugin.getLogger().log(Level.SEVERE, "la_shopbuy: could not refund " + money + " to " + player.getName(), ex);
      }
   }

   @Override
   public boolean canUse(CommandSender sender) {
      return sender instanceof ConsoleCommandSender;
   }
}
