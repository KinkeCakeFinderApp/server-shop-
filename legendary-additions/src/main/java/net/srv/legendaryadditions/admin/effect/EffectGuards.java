package net.srv.legendaryadditions.admin.effect;

import org.bukkit.Bukkit;
import org.bukkit.World;

/** Checks used by long-running effect tasks so they stop cleanly when their world goes away. */
final class EffectGuards {
   private EffectGuards() {
   }

   static boolean worldStillLoaded(World world) {
      return world != null && Bukkit.getWorld(world.getUID()) != null;
   }
}
