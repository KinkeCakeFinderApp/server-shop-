package net.srv.legendaryadditions.admin.effect;

import net.srv.legendaryadditions.admin.AdminSettings;
import net.srv.legendaryadditions.admin.rod.RayTargeting;
import org.bukkit.entity.Player;

/** One rod's behaviour. Called on the caster's region thread with a target locked at cast time. */
@FunctionalInterface
public interface RodEffect {
   /**
    * @return true if the effect was started; false if it refused to activate (a single-use rod is
    *         then not consumed). Implementations tell the player why they refused.
    */
   boolean activate(Player caster, RayTargeting.Target target, AdminSettings settings);
}
