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
   public static final String NO_SAFE_TELEPORT = "Teleport cancelled: no safe spot with solid ground and two blocks of headroom near that target.";
   public static final String FAKE_ROD = "This rod failed authentication and has no power.";
   public static final String ACTIVATION_FAILED = "The rod failed to activate. Check the server console.";
   public static final String DIMENSION_UNAVAILABLE = "The Admin dimension is unavailable right now. Ask an admin to check the server console.";
   public static final String ACTION_FAILED = "That action failed. Please try again; details were logged for admins.";
   public static final String SUGGESTIONS_DISABLED = "Suggestions are currently disabled.";
   public static final String DISABLED_WORLD = "Special rods are disabled in this world.";
}
