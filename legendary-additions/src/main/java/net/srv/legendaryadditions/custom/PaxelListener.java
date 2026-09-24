package net.srv.legendaryadditions.custom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class PaxelListener implements Listener {
   private static final int MAX_TREE_BLOCKS = 256;
   private static final int MAX_VEIN_BLOCKS = 64;

   public PaxelListener(JavaPlugin plugin) {
      this.startHasteTask(plugin);
   }

   @EventHandler
   public void onInteract(PlayerInteractEvent event) {
      Action action = event.getAction();
      if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
         Player player = event.getPlayer();
         if (player.isSneaking()) {
            EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
            ItemStack item = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
            if (Paxel.isPaxel(item)) {
               event.setCancelled(true);
               Paxel.toggleMode(item);
               if (hand == EquipmentSlot.OFF_HAND) {
                  player.getInventory().setItemInOffHand(item);
               } else {
                  player.getInventory().setItemInMainHand(item);
               }

               this.playModeSound(player, Paxel.getMode(item));
            }
         }
      }
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      ItemStack item = player.getInventory().getItemInMainHand();
      if (Paxel.isPaxel(item)) {
         Block center = event.getBlock();
         String mode = Paxel.getMode(item);
         switch (mode) {
            case "AREA_3X3":
               this.mine3x3(player, center, item);
               break;
            case "VEIN_MINER":
               this.veinMine(center, item);
               break;
            case "CHAINSAW":
               if (Tag.LOGS.isTagged(center.getType())) {
                  this.fellTree(center, item);
               }
            case "NORMAL":
         }
      }
   }

   private void fellTree(Block origin, ItemStack item) {
      Material logType = origin.getType();
      Set<Block> visited = new HashSet<>();
      Deque<Block> queue = new ArrayDeque<>();
      visited.add(origin);
      queue.add(origin);

      while (!queue.isEmpty() && visited.size() < 256) {
         Block current = queue.poll();

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx != 0 || dy != 0 || dz != 0) {
                     Block neighbor = current.getRelative(dx, dy, dz);
                     if (!visited.contains(neighbor) && neighbor.getType() == logType) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                     }
                  }
               }
            }
         }
      }

      for (Block block : visited) {
         if (!block.equals(origin)) {
            block.breakNaturally(item);
         }
      }
   }

   private void veinMine(Block origin, ItemStack item) {
      Material veinType = origin.getType();
      Set<Block> visited = new HashSet<>();
      Deque<Block> queue = new ArrayDeque<>();
      visited.add(origin);
      queue.add(origin);

      while (!queue.isEmpty() && visited.size() < 64) {
         Block current = queue.poll();

         for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block neighbor = current.getRelative(face);
            if (!visited.contains(neighbor) && neighbor.getType() == veinType) {
               visited.add(neighbor);
               queue.add(neighbor);
            }
         }
      }

      for (Block block : visited) {
         if (!block.equals(origin)) {
            block.breakNaturally(item);
         }
      }
   }

   private void mine3x3(Player player, Block center, ItemStack item) {
      BlockFace axis = this.getDominantAxis(player.getEyeLocation().getDirection());

      for (Block block : this.getPlaneBlocks(center, axis)) {
         if (!block.equals(center) && !block.getType().isAir()) {
            block.breakNaturally(item);
         }
      }
   }

   private BlockFace getDominantAxis(Vector direction) {
      double ax = Math.abs(direction.getX());
      double ay = Math.abs(direction.getY());
      double az = Math.abs(direction.getZ());
      if (ay >= ax && ay >= az) {
         return direction.getY() > 0.0 ? BlockFace.UP : BlockFace.DOWN;
      } else if (ax >= az) {
         return direction.getX() > 0.0 ? BlockFace.EAST : BlockFace.WEST;
      } else {
         return direction.getZ() > 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
      }
   }

   private List<Block> getPlaneBlocks(Block center, BlockFace axis) {
      List<Block> blocks = new ArrayList<>();

      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            int x = 0;
            int y = 0;
            int z = 0;
            switch (axis) {
               case UP:
               case DOWN:
                  x = a;
                  z = b;
                  break;
               case NORTH:
               case SOUTH:
                  x = a;
                  y = b;
                  break;
               case EAST:
               case WEST:
               default:
                  z = a;
                  y = b;
            }

            blocks.add(center.getRelative(x, y, z));
         }
      }

      return blocks;
   }

   private void playModeSound(Player player, String mode) {
      player.playSound(player.getLocation(), switch (mode) {
         case "AREA_3X3" -> Sound.BLOCK_SMITHING_TABLE_USE;
         case "VEIN_MINER" -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
         case "CHAINSAW" -> Sound.BLOCK_GRINDSTONE_USE;
         default -> Sound.ITEM_ARMOR_EQUIP_IRON;
      }, 1.0F, 1.0F);
   }

   private void startHasteTask(Plugin plugin) {
      // Folia: one global timer hands each player's check to that player's own scheduler.
      Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, timer -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
               ItemStack main = player.getInventory().getItemInMainHand();
               ItemStack off = player.getInventory().getItemInOffHand();
               if (Paxel.isPaxel(main) || Paxel.isPaxel(off)) {
                  player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 4, true, false, false));
               }
            }, null);
         }
      }, 1L, 40L);
   }
}
