package net.srv.legendaryadditions.admin.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Player-facing messages. Never includes stack traces. */
public final class Messages {
   private static final Component PREFIX = Component.text("[Admin] ", NamedTextColor.DARK_RED);

   private Messages() {
   }

   public static void info(Audience audience, String text) {
      audience.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GRAY)));
   }

   public static void success(Audience audience, String text) {
      audience.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GREEN)));
   }

   public static void error(Audience audience, String text) {
      audience.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
   }

   public static final String NO_PERMISSION = "You do not have permission to do that.";
   public static final String NO_TARGET = "No valid target - look at a block or entity within range.";
   public static final String UNSAFE_TELEPORT = "That destination is not safe to teleport to.";
   public static final String FAKE_ROD = "This rod failed authentication and has no power.";
   public static final String ACTIVATION_FAILED = "The rod failed to activate. Check the server console.";
   public static final String DIMENSION_UNAVAILABLE = "The Admin dimension is unavailable. Restart the server once after installing the plugin.";
   public static final String DISABLED_WORLD = "Special rods are disabled in this world.";
}
