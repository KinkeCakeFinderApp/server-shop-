package net.srv.legendaryadditions.admin.rod;

/** Where a rod design comes from. Stored on the item so the two families can be told apart. */
public enum RodOrigin {
   /** Built into this plugin (/stab, /nuke, /teleportshot). */
   PLUGIN("plugin"),
   /** Converted from the Orbital Strike Cannon datapack. */
   DATAPACK("datapack");

   private final String id;

   RodOrigin(String id) {
      this.id = id;
   }

   public String id() {
      return this.id;
   }
}
