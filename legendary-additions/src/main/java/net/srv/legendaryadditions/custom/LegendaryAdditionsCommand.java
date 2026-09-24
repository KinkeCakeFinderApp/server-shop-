package net.srv.legendaryadditions.custom;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.srv.legendaryadditions.LegendaryAdditionsMod;
import net.srv.legendaryadditions.forge.LegendaryItems;
import net.srv.legendaryadditions.forge.LegendaryService;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LegendaryAdditionsCommand implements BasicCommand {
   public static final String PERMISSION = "legendaryadditions.give";

   @Override
   public void execute(CommandSourceStack source, String[] args) {
      CommandSender sender = source.getSender();
      if (!sender.hasPermission(PERMISSION)) {
         sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
      } else if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
         Player target = Bukkit.getPlayerExact(args[1]);
         String itemName = args[2];
         if (target == null || this.resolveItem(itemName) == null) {
            sender.sendMessage(ChatColor.RED + "Usage: /legendary_additions give <player> <item>");
            return;
         }
         // Folia: the target's inventory may only be touched on the target's own thread.
         target.getScheduler().run(LegendaryAdditionsMod.plugin, task -> {
            ItemStack item = this.resolveItem(itemName);
            if (item != null) {
               target.getInventory().addItem(item).values()
                     .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
            }
         }, null);
         sender.sendMessage(ChatColor.GREEN + "Gave " + itemName.toLowerCase() + " to " + target.getName() + ".");
      } else {
         sender.sendMessage(ChatColor.RED + "Usage: /legendary_additions give <player> <item>");
      }
   }

   private ItemStack resolveItem(String itemName) {
      if (itemName.equalsIgnoreCase("riftcaster")) {
         return Riftcaster.create();
      } else if (itemName.equalsIgnoreCase("tidefire_crossbow")) {
         return TidefireCrossbow.create();
      } else if (itemName.equalsIgnoreCase("embershade")) {
         return Embershade.create();
      } else if (itemName.equalsIgnoreCase("drill")) {
         return Drill.create();
      } else if (itemName.equalsIgnoreCase("paxel")) {
         return Paxel.create();
      }
      // Admin-made legendaries from the Legendary Creator (legendaries.db).
      LegendaryService service = LegendaryItems.service();
      LegendaryDef def = service == null ? null : service.get(itemName.toLowerCase());
      return def == null ? null : LegendaryItems.build(def);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack source, String[] args) {
      List<String> completions = new ArrayList<>();
      int index = Math.max(0, args.length - 1);
      if (index == 0) {
         completions.add("give");
      } else if (index == 1) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            completions.add(player.getName());
         }
      } else if (index == 2) {
         completions.addAll(Arrays.asList("riftcaster", "tidefire_crossbow", "embershade", "drill", "paxel"));
         if (LegendaryItems.service() != null) {
            LegendaryItems.service().all().forEach(def -> completions.add(def.id()));
         }
      }

      String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
      return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).collect(Collectors.toList());
   }

   @Override
   public String permission() {
      return PERMISSION;
   }
}
