package net.srv.eloranks.reward;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * One kind of item in a reward.
 *
 * @param amount total number of items, split into full stacks
 * @param potionType vanilla potion (e.g. strong_swiftness = Speed II), or null
 * @param effects extra custom potion effects
 */
public record RewardItemDef(Material material, int amount, String name, List<String> lore, PotionType potionType,
                            List<Effect> effects, Color color) {

   /** @param level 1 = level I; @param durationTicks effect duration in ticks */
   public record Effect(PotionEffectType type, int level, int durationTicks) {
   }
}
