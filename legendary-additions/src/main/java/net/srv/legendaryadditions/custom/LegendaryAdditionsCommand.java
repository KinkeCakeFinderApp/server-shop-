package net.srv.legendaryadditions.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LegendaryAdditionsCommand implements CommandExecutor, TabCompleter {
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("legendaryadditions.give")) {
         sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
         return true;
      } else if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
         String playerName = args[1];
         String itemName = args[2];
         Player target = Bukkit.getPlayerExact(playerName);
         if (target == null) {
            return true;
         } else {
            ItemStack item = this.resolveItem(itemName);
            if (item == null) {
               return true;
            } else {
               target.getInventory().addItem(new ItemStack[]{item});
               return true;
            }
         }
      } else {
         return true;
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
      } else {
         return itemName.equalsIgnoreCase("paxel") ? Paxel.create() : null;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> completions = new ArrayList<>();
      if (args.length == 1) {
         completions.add("give");
      } else if (args.length == 2) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            completions.add(player.getName());
         }
      } else if (args.length == 3) {
         completions.addAll(Arrays.asList("riftcaster", "tidefire_crossbow", "embershade", "drill", "paxel"));
      }

      String current = args[args.length - 1].toLowerCase();
      return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).collect(Collectors.toList());
   }
}
