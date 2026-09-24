package net.srv.legendaryadditions.admin.suggestion;

import java.util.function.Supplier;
import net.srv.legendaryadditions.admin.AdminSettings;
import org.bukkit.entity.Player;

/** Every permission decision for suggestions lives here and is evaluated at the moment of use. */
public final class SuggestionAccess {
   public static final String USE = "admindimension.suggestions";
   public static final String ADMIN = "admindimension.suggestions.admin";

   private final Supplier<AdminSettings> settings;

   public SuggestionAccess(Supplier<AdminSettings> settings) {
      this.settings = settings;
   }

   public AdminSettings.Suggestions config() {
      return this.settings.get().suggestions();
   }

   public boolean enabled() {
      return this.config().enabled();
   }

   /** Browsing, voting and submitting. */
   public boolean canUse(Player player) {
      if (isAdmin(player)) {
         return true;
      }
      return this.enabled() && (this.config().allowAllPlayers() || player.hasPermission(USE));
   }

   /** Checked before opening any admin screen and again before every admin action. */
   public static boolean isAdmin(Player player) {
      return player.isOnline() && player.hasPermission(ADMIN);
   }
}
