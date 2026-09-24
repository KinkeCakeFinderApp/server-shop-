package net.srv.legendaryadditions.admin.rod;

import java.util.Objects;
import org.bukkit.NamespacedKey;

/** Hidden persistent-data keys written on every genuine rod. */
public final class RodKeys {
   public static final NamespacedKey TYPE = key("rod_type");
   public static final NamespacedKey AUTH = key("rod_auth");
   public static final NamespacedKey VERSION = key("rod_version");
   public static final NamespacedKey NONCE = key("rod_nonce");
   public static final NamespacedKey ORIGIN = key("rod_origin");
   public static final NamespacedKey SOURCE = key("rod_source");
   /** Written on entities spawned by rods (arrows, skulls, wolves) - value is the caster's UUID. */
   public static final NamespacedKey OWNER = key("owner");
   /** Byte flag on rod projectiles: 1 = never hurt {@link #OWNER}. */
   public static final NamespacedKey PROTECT_OWNER = key("protect_owner");
   /** Byte flag on rod projectiles: 1 = the explosion must not break blocks. */
   public static final NamespacedKey NO_BLOCK_DAMAGE = key("no_block_damage");
   /** Player data: where /admin return sends the player. */
   public static final NamespacedKey RETURN_LOCATION = key("return_location");

   public static final int CURRENT_VERSION = 1;

   private RodKeys() {
   }

   private static NamespacedKey key(String name) {
      return Objects.requireNonNull(NamespacedKey.fromString("adminplugin:" + name));
   }
}
