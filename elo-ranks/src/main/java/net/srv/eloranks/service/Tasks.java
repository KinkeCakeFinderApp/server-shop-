package net.srv.eloranks.service;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Folia helpers. */
public final class Tasks {
   private Tasks() {
   }

   /**
    * Runs {@code task} on the thread that owns {@code entity}; runs {@code retired} instead if the
    * entity is gone (player logged out) - so cleanup always happens.
    */
   public static void onEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
      Runnable safeRetired = retired == null ? () -> { } : retired;
      if (!plugin.isEnabled()) {
         safeRetired.run();
         return;
      }
      if (entity.getScheduler().run(plugin, t -> task.run(), safeRetired) == null) {
         safeRetired.run();
      }
   }
}
