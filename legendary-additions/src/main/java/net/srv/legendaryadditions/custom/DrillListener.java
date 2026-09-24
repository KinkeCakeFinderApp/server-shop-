package net.srv.legendaryadditions.custom;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Sound;
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
import org.bukkit.util.Vector;

public class DrillListener implements Listener {
   @EventHandler
   public void onInteract(PlayerInteractEvent event) {
      Action action = event.getAction();
      if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
         Player player = event.getPlayer();
         if (player.isSneaking()) {
            EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
            ItemStack item = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
            if (Drill.isDrill(item)) {
               event.setCancelled(true);
               Drill.toggleMode(item);
               if (hand == EquipmentSlot.OFF_HAND) {
                  player.getInventory().setItemInOffHand(item);
               } else {
                  player.getInventory().setItemInMainHand(item);
               }

               String mode = Drill.getMode(item);
               player.playSound(player.getLocation(), mode.equals("DRILL") ? Sound.BLOCK_SMITHING_TABLE_USE : Sound.ITEM_ARMOR_EQUIP_IRON, 1.0F, 1.0F);
            }
         }
      }
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      ItemStack item = player.getInventory().getItemInMainHand();
      if (Drill.isDrill(item)) {
         if ("DRILL".equals(Drill.getMode(item))) {
            Block center = event.getBlock();
            BlockFace axis = this.getDominantAxis(player.getEyeLocation().getDirection());

            for (Block block : this.getPlaneBlocks(center, axis)) {
               if (!block.equals(center) && !block.getType().isAir()) {
                  block.breakNaturally(item);
               }
            }
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

   private Iterable<Block> getPlaneBlocks(Block center, BlockFace axis) {
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
}
