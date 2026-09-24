package net.srv.legendaryadditions.forge;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Builds custom legendary items and recognises them. An item only carries its id
 * ({@code legendaryadditions:custom_id}); its abilities are always read from the saved definition,
 * so editing a legendary in the creator changes what every existing copy does.
 */
public final class LegendaryItems {
   private static NamespacedKey idKey;
   private static LegendaryService service;

   private LegendaryItems() {
   }

   public static void init(Plugin plugin, LegendaryService legendaryService) {
      idKey = new NamespacedKey(plugin, "custom_id");
      service = legendaryService;
   }

   public static NamespacedKey idKey() {
      return idKey;
   }

   public static LegendaryService service() {
      return service;
   }

   public static Component text(String legacy) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
   }

   public static Material material(String key) {
      Material material = key == null ? null : Material.matchMaterial(key);
      return material != null && material.isItem() && !material.isAir() ? material : Material.PAPER;
   }

   public static Enchantment enchantment(String key) {
      NamespacedKey nk = key == null ? null : NamespacedKey.fromString(key);
      return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(nk);
   }

   public static ItemStack build(LegendaryDef def) {
      ItemStack item = new ItemStack(material(def.material()));
      ItemMeta meta = item.getItemMeta();
      meta.customName(text(def.name()));
      List<Component> lore = new ArrayList<>();
      for (String line : def.lore()) {
         lore.add(text(line));
      }
      Map<Ability, Integer> abilities = abilities(def);
      if (!abilities.isEmpty()) {
         if (!lore.isEmpty()) {
            lore.add(Component.empty());
         }
         for (Map.Entry<Ability, Integer> e : abilities.entrySet()) {
            String level = e.getKey().maxLevel() > 1 ? " " + Ability.roman(e.getValue()) : "";
            lore.add(Component.text(e.getKey().label() + level, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
         }
      }
      lore.add(Component.text("Legendary", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
      meta.lore(lore);
      for (Map.Entry<String, Integer> e : def.enchants().entrySet()) {
         Enchantment enchantment = enchantment(e.getKey());
         if (enchantment != null && e.getValue() > 0) {
            meta.addEnchant(enchantment, Math.min(255, e.getValue()), true);
         }
      }
      meta.setUnbreakable(def.unbreakable());
      if (def.glow()) {
         meta.setEnchantmentGlintOverride(true);
      }
      if (def.modelData() != null) {
         CustomModelDataComponent model = meta.getCustomModelDataComponent();
         model.setFloats(List.of(def.modelData().floatValue()));
         meta.setCustomModelDataComponent(model);
      }
      meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
      item.setItemMeta(meta);
      return item;
   }

   /** Abilities in display order, skipping unknown ids and clamping levels. */
   public static Map<Ability, Integer> abilities(LegendaryDef def) {
      Map<Ability, Integer> out = new EnumMap<>(Ability.class);
      for (Map.Entry<String, Integer> e : def.abilities().entrySet()) {
         Ability ability = Ability.byId(e.getKey());
         if (ability != null && e.getValue() > 0) {
            out.put(ability, Math.min(ability.maxLevel(), e.getValue()));
         }
      }
      return out;
   }

   public static String idOf(ItemStack item) {
      if (item == null || idKey == null || item.getType().isAir() || !item.hasItemMeta()) {
         return null;
      }
      return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
   }

   /** The saved definition of a custom legendary item, or null for any other item. */
   public static LegendaryDef defOf(ItemStack item) {
      String id = idOf(item);
      return id == null || service == null ? null : service.get(id);
   }

   /** Level of {@code ability} on this item, 0 if it has none. */
   public static int level(ItemStack item, Ability ability) {
      LegendaryDef def = defOf(item);
      if (def == null) {
         return 0;
      }
      Integer level = def.abilities().get(ability.name());
      return level == null || level <= 0 ? 0 : Math.min(ability.maxLevel(), level);
   }
}
