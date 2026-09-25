package net.srv.eloranks.reward;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.srv.eloranks.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

/**
 * Builds the exact reward items from their config every time a reward is delivered. Container
 * rewards are packed into barrels through the item's minecraft:container data component (the
 * 1.20.5+ replacement of BlockEntityTag NBT) - never into shulker boxes.
 */
public final class RewardFactory {
   /** A barrel holds 27 stacks. */
   public static final int BARREL_SLOTS = 27;

   private RewardFactory() {
   }

   /** The stacks the player receives: barrels, or the loose items. */
   public static List<ItemStack> build(RewardDef reward) {
      List<ItemStack> stacks = new ArrayList<>();
      for (RewardItemDef def : reward.items()) {
         ItemStack template = template(def);
         int max = Math.max(1, template.getMaxStackSize());
         int left = def.amount();
         while (left > 0) {
            int size = Math.min(max, left);
            ItemStack stack = template.clone();
            stack.setAmount(size);
            stacks.add(stack);
            left -= size;
         }
      }
      if (reward.container() == RewardDef.Container.NONE) {
         return stacks;
      }
      List<ItemStack> barrels = new ArrayList<>();
      for (int from = 0; from < stacks.size(); from += BARREL_SLOTS) {
         List<ItemStack> contents = stacks.subList(from, Math.min(stacks.size(), from + BARREL_SLOTS));
         ItemStack barrel = ItemStack.of(Material.BARREL);
         if (reward.containerName() != null && !reward.containerName().isBlank()) {
            ItemMeta meta = barrel.getItemMeta();
            meta.displayName(Text.item(reward.containerName(), Map.of()));
            barrel.setItemMeta(meta);
         }
         barrel.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(List.copyOf(contents)));
         barrels.add(barrel);
      }
      return barrels;
   }

   private static ItemStack template(RewardItemDef def) {
      ItemStack item = ItemStack.of(def.material());
      ItemMeta meta = item.getItemMeta();
      if (meta instanceof PotionMeta potion) {
         if (def.potionType() != null) {
            potion.setBasePotionType(def.potionType());
         }
         for (RewardItemDef.Effect effect : def.effects()) {
            potion.addCustomEffect(new PotionEffect(effect.type(), effect.durationTicks(), effect.level() - 1), true);
         }
         if (def.color() != null) {
            potion.setColor(def.color());
         }
      }
      if (def.name() != null && !def.name().isBlank()) {
         meta.displayName(Text.item(def.name(), Map.of()));
      }
      if (!def.lore().isEmpty()) {
         meta.lore(def.lore().stream().map(line -> Text.item(line, Map.of())).toList());
      }
      item.setItemMeta(meta);
      return item;
   }
}
