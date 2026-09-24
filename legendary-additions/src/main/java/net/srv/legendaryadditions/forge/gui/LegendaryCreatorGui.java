package net.srv.legendaryadditions.forge.gui;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.srv.legendaryadditions.admin.suggestion.ChatInputManager;
import net.srv.legendaryadditions.admin.suggestion.SuggestionAccess;
import net.srv.legendaryadditions.admin.suggestion.gui.Items;
import net.srv.legendaryadditions.admin.suggestion.gui.Menu;
import net.srv.legendaryadditions.admin.util.Messages;
import net.srv.legendaryadditions.forge.Ability;
import net.srv.legendaryadditions.forge.LegendaryItems;
import net.srv.legendaryadditions.forge.LegendaryService;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The Legendary Creator, opened from /suggestionadmin. Admins build an item (name, material,
 * lore, enchantments, abilities, unbreakable, glow, model data) as an in-memory draft and save it
 * to legendaries.db. Every screen is admin-only and re-checks the permission on each click.
 */
public final class LegendaryCreatorGui {
   private static final int INPUT_SECONDS = 120;
   private static final String TIMEOUT = "Legendary Creator input timed out.";

   private final Plugin plugin;
   private final LegendaryService service;
   private final ChatInputManager chat;
   private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
   private BackAction back = player -> player.closeInventory();

   @FunctionalInterface
   public interface BackAction {
      void open(Player player);
   }

   /** Editable copy of a legendary. Only touched on its admin's own thread. */
   static final class Draft {
      String id;
      String name;
      String material;
      List<String> lore = new ArrayList<>();
      Map<String, Integer> enchants = new LinkedHashMap<>();
      Map<String, Integer> abilities = new LinkedHashMap<>();
      boolean unbreakable = true;
      boolean glow = true;
      Integer modelData;

      LegendaryDef toDef(UUID author) {
         long now = System.currentTimeMillis();
         return new LegendaryDef(this.id, this.name, this.material, this.lore, this.enchants, this.abilities,
               this.unbreakable, this.glow, this.modelData, author.toString(), now, now);
      }

      static Draft of(LegendaryDef def) {
         Draft d = new Draft();
         d.id = def.id();
         d.name = def.name();
         d.material = def.material();
         d.lore = new ArrayList<>(def.lore());
         d.enchants = new LinkedHashMap<>(def.enchants());
         d.abilities = new LinkedHashMap<>(def.abilities());
         d.unbreakable = def.unbreakable();
         d.glow = def.glow();
         d.modelData = def.modelData();
         return d;
      }
   }

   public LegendaryCreatorGui(Plugin plugin, LegendaryService service, ChatInputManager chat) {
      this.plugin = plugin;
      this.service = service;
      this.chat = chat;
   }

   /** Where "Back" on the list screen goes (the suggestion admin list). */
   public void setBack(BackAction back) {
      this.back = back;
   }

   // ---------------------------------------------------------------- list

   public void openList(Player player, int page) {
      if (!SuggestionAccess.isAdmin(player)) {
         Messages.error(player, Messages.NO_PERMISSION);
         return;
      }
      List<LegendaryDef> all = this.service.all();
      int pages = Math.max(1, (all.size() + 44) / 45);
      int current = Math.max(0, Math.min(page, pages - 1));
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Legendary Creator (" + all.size() + ")", NamedTextColor.DARK_PURPLE), true);
      int slot = 0;
      for (LegendaryDef def : all.subList(current * 45, Math.min(all.size(), current * 45 + 45))) {
         ItemStack icon = LegendaryItems.build(def);
         appendLore(icon, "", "&7ID: &f" + def.id(), "&eLeft click: edit", "&eRight click: give yourself one",
               "&cShift + right click: delete");
         menu.set(slot++, icon, (p, click) -> {
            if (click == ClickType.SHIFT_RIGHT) {
               this.confirmDelete(p, def.id());
            } else if (click.isRightClick()) {
               this.give(p, def);
            } else {
               this.drafts.put(p.getUniqueId(), Draft.of(def));
               this.openEditor(p);
            }
         });
      }
      if (all.isEmpty()) {
         menu.set(22, Items.icon(Material.BARRIER, "No legendaries yet", NamedTextColor.GRAY, "Click \"Create New Legendary\"."));
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE, "Back to the suggestion admin panel."), (p, c) -> this.back.open(p));
      if (current > 0) {
         menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openList(p, current - 1));
      }
      menu.set(49, Items.icon(Material.NETHER_STAR, "Create New Legendary", NamedTextColor.GREEN,
            "You will type an id in chat,", "e.g. storm_blade (a-z, 0-9, _)."), (p, c) -> this.askNewId(p));
      if (current < pages - 1) {
         menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openList(p, current + 1));
      }
      Draft draft = this.drafts.get(player.getUniqueId());
      if (draft != null) {
         menu.set(51, Items.icon(Material.WRITABLE_BOOK, "Continue editing: " + draft.id, NamedTextColor.AQUA,
               "You have unsaved changes open."), (p, c) -> this.openEditor(p));
      }
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   private void askNewId(Player player) {
      player.closeInventory();
      Messages.info(player, "Type an id for the new legendary in chat (a-z, 0-9, _; 2-32 letters), or 'cancel'.");
      this.chat.ask(player, INPUT_SECONDS, TIMEOUT, (p, text) -> {
         String id = text.toLowerCase();
         if (!LegendaryDef.validId(id)) {
            Messages.error(p, "Invalid id '" + text + "'. Use 2-32 of a-z, 0-9 and _.");
            this.askNewId(p);
            return;
         }
         if (this.service.get(id) != null) {
            Messages.error(p, "A legendary called '" + id + "' already exists. Opening it for editing.");
            this.drafts.put(p.getUniqueId(), Draft.of(this.service.get(id)));
            this.openEditor(p);
            return;
         }
         Draft draft = new Draft();
         draft.id = id;
         draft.name = "&6&l" + pretty(id);
         ItemStack held = p.getInventory().getItemInMainHand();
         draft.material = held.getType().isAir() ? "minecraft:diamond_sword" : held.getType().getKey().toString();
         this.drafts.put(p.getUniqueId(), draft);
         Messages.success(p, "Created draft '" + id + "'. Set it up, then click Save.");
         this.openEditor(p);
      });
   }

   // ---------------------------------------------------------------- editor

   public void openEditor(Player player) {
      Draft d = this.drafts.get(player.getUniqueId());
      if (d == null) {
         this.openList(player, 0);
         return;
      }
      boolean saved = this.service.get(d.id) != null;
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Editing: " + d.id + (saved ? "" : " (new)"), NamedTextColor.DARK_PURPLE), true);
      for (int i = 0; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      ItemStack preview = LegendaryItems.build(d.toDef(player.getUniqueId()));
      appendLore(preview, "", "&eClick: give yourself this preview");
      menu.set(4, preview, (p, c) -> this.give(p, d.toDef(p.getUniqueId())));

      menu.set(19, Items.icon(Material.NAME_TAG, "Name", NamedTextColor.YELLOW, "Now: " + d.name, "Click and type a name in chat.",
            "Colour codes like &6 and &l work."), (p, c) -> this.ask(p, "Type the item name (colour codes like &6&l work).", text -> d.name = text));
      menu.set(20, Items.icon(LegendaryItems.material(d.material), "Material", NamedTextColor.YELLOW, "Now: " + d.material,
            "Left click: use the item in your hand", "Right click: type a material in chat"), (p, c) -> {
         if (c.isLeftClick()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
               Messages.error(p, "Hold the item whose material you want to use.");
               return;
            }
            d.material = held.getType().getKey().toString();
            this.openEditor(p);
         } else {
            this.ask(p, "Type a material, e.g. netherite_axe or bow.", text -> {
               Material m = Material.matchMaterial(text.replace(' ', '_'));
               if (m == null || !m.isItem() || m.isAir()) {
                  Messages.error(p, "'" + text + "' is not an item material.");
                  return;
               }
               d.material = m.getKey().toString();
            });
         }
      });
      List<String> loreInfo = new ArrayList<>();
      loreInfo.add("Lines: " + d.lore.size());
      loreInfo.addAll(d.lore.stream().limit(6).map(l -> "  " + l).toList());
      loreInfo.add("Left click: add a line");
      loreInfo.add("Right click: remove the last line");
      loreInfo.add("Shift + right click: clear");
      menu.set(21, Items.icon(Material.WRITABLE_BOOK, "Lore", NamedTextColor.YELLOW, loreInfo.toArray(String[]::new)), (p, c) -> {
         if (c == ClickType.SHIFT_RIGHT) {
            d.lore.clear();
            this.openEditor(p);
         } else if (c.isRightClick()) {
            if (!d.lore.isEmpty()) {
               d.lore.remove(d.lore.size() - 1);
            }
            this.openEditor(p);
         } else if (d.lore.size() >= 20) {
            Messages.error(p, "Lore is limited to 20 lines.");
         } else {
            this.ask(p, "Type a lore line (colour codes work).", text -> d.lore.add(text));
         }
      });
      menu.set(22, Items.icon(Material.ENCHANTED_BOOK, "Enchantments (" + d.enchants.size() + ")", NamedTextColor.LIGHT_PURPLE,
            summary(d.enchants, true).toArray(String[]::new)), (p, c) -> this.openEnchants(p, 0));
      menu.set(23, Items.icon(Material.NETHER_STAR, "Abilities (" + d.abilities.size() + ")", NamedTextColor.GOLD,
            summary(d.abilities, false).toArray(String[]::new)), (p, c) -> this.openAbilities(p, 0));
      menu.set(24, Items.icon(d.unbreakable ? Material.OBSIDIAN : Material.COBBLESTONE, "Unbreakable: " + (d.unbreakable ? "ON" : "OFF"),
            d.unbreakable ? NamedTextColor.GREEN : NamedTextColor.RED, "Click to toggle."), (p, c) -> {
         d.unbreakable = !d.unbreakable;
         this.openEditor(p);
      });
      menu.set(25, Items.icon(d.glow ? Material.GLOWSTONE_DUST : Material.GUNPOWDER, "Enchant Glow: " + (d.glow ? "ON" : "OFF"),
            d.glow ? NamedTextColor.GREEN : NamedTextColor.RED, "Click to toggle."), (p, c) -> {
         d.glow = !d.glow;
         this.openEditor(p);
      });
      menu.set(31, Items.icon(Material.ITEM_FRAME, "Custom Model Data", NamedTextColor.YELLOW,
            "Now: " + (d.modelData == null ? "none" : d.modelData), "For resource packs. Click and type", "a number, or 'none'."),
            (p, c) -> this.ask(p, "Type a custom model data number, or 'none'.", text -> {
               if (text.equalsIgnoreCase("none")) {
                  d.modelData = null;
                  return;
               }
               try {
                  d.modelData = Integer.parseInt(text);
               } catch (NumberFormatException ex) {
                  Messages.error(p, "'" + text + "' is not a whole number.");
               }
            }));

      menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE, "Back to the list. Unsaved changes", "stay open until you save or discard."),
            (p, c) -> this.openList(p, 0));
      menu.set(47, Items.icon(Material.RED_CONCRETE, "Discard Changes", NamedTextColor.RED, "Throws away unsaved changes."), (p, c) -> {
         this.drafts.remove(p.getUniqueId());
         Messages.info(p, "Discarded unsaved changes to '" + d.id + "'.");
         this.openList(p, 0);
      });
      menu.set(49, Items.icon(Material.LIME_CONCRETE, "Save", NamedTextColor.GREEN, "Saves to legendaries.db.",
            "Existing copies update instantly."), (p, c) -> this.save(p, d));
      menu.set(51, Items.icon(Material.CHEST, "Give Yourself One", NamedTextColor.AQUA, "Gives the item as shown above."),
            (p, c) -> this.give(p, d.toDef(p.getUniqueId())));
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   private void save(Player player, Draft d) {
      LegendaryDef def = d.toDef(player.getUniqueId());
      UUID id = player.getUniqueId();
      this.service.save(def).whenComplete((stored, error) -> player.getScheduler().run(this.plugin, task -> {
         if (error != null) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save legendary " + d.id, error);
            Messages.error(player, Messages.ACTION_FAILED);
            return;
         }
         this.drafts.remove(id);
         this.plugin.getLogger().info(player.getName() + " saved custom legendary '" + stored.id() + "' with abilities "
               + stored.abilities() + " and enchantments " + stored.enchants());
         Messages.success(player, "Saved legendary '" + stored.id() + "'. Give it with /legendary_additions give <player> " + stored.id()
               + " or add it to the shop with /shop admin.");
         this.drafts.put(id, Draft.of(stored));
         this.openEditor(player);
      }, null));
   }

   /** Closes the GUI, asks one chat question, applies the answer and reopens the editor. */
   private void ask(Player player, String question, java.util.function.Consumer<String> apply) {
      player.closeInventory();
      Messages.info(player, question + " Type 'cancel' to go back.");
      this.chat.ask(player, INPUT_SECONDS, TIMEOUT, (p, text) -> {
         if (!text.isEmpty() && text.length() <= 200) {
            apply.accept(text);
         } else {
            Messages.error(p, "That is empty or too long (200 letters max).");
         }
         this.openEditor(p);
      });
   }

   // ---------------------------------------------------------------- enchantments

   private void openEnchants(Player player, int page) {
      Draft d = this.drafts.get(player.getUniqueId());
      if (d == null) {
         this.openList(player, 0);
         return;
      }
      List<Enchantment> all = new ArrayList<>();
      RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(all::add);
      all.sort(Comparator.comparing(e -> e.getKey().toString()));
      int pages = Math.max(1, (all.size() + 44) / 45);
      int current = Math.max(0, Math.min(page, pages - 1));
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Enchantments: " + d.id, NamedTextColor.DARK_PURPLE), true);
      int slot = 0;
      for (Enchantment enchantment : all.subList(current * 45, Math.min(all.size(), current * 45 + 45))) {
         String key = enchantment.getKey().toString();
         int level = d.enchants.getOrDefault(key, 0);
         ItemStack icon = Items.icon(level > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
               Items.text(pretty(enchantment.getKey().getKey()) + (level > 0 ? " " + level : ""), level > 0 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY),
               lines("Level: " + level + " (vanilla max " + enchantment.getMaxLevel() + ")", "Left click: +1", "Right click: -1",
                     "Shift + left click: +10", "Shift + right click: remove"), level > 0);
         menu.set(slot++, icon, (p, click) -> {
            int next = switch (click) {
               case SHIFT_LEFT -> level + 10;
               case SHIFT_RIGHT -> 0;
               case RIGHT -> level - 1;
               default -> level + 1;
            };
            next = Math.max(0, Math.min(255, next));
            if (next == 0) {
               d.enchants.remove(key);
            } else {
               d.enchants.put(key, next);
            }
            this.openEnchants(p, current);
         });
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, Items.icon(Material.ARROW, "Back to Editor", NamedTextColor.WHITE), (p, c) -> this.openEditor(p));
      if (current > 0) {
         menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openEnchants(p, current - 1));
      }
      if (current < pages - 1) {
         menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openEnchants(p, current + 1));
      }
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   // ---------------------------------------------------------------- abilities

   private void openAbilities(Player player, int page) {
      Draft d = this.drafts.get(player.getUniqueId());
      if (d == null) {
         this.openList(player, 0);
         return;
      }
      Ability[] all = Ability.values();
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Abilities: " + d.id, NamedTextColor.DARK_PURPLE), true);
      int slot = 0;
      for (Ability ability : all) {
         if (slot >= 45) {
            break;
         }
         int level = d.abilities.getOrDefault(ability.name(), 0);
         List<String> lore = new ArrayList<>(ability.description());
         lore.add("Level: " + level + " / " + ability.maxLevel());
         lore.add("Left click: +1");
         lore.add("Right click: -1");
         lore.add("Shift + left click: max level");
         lore.add("Shift + right click: remove");
         menu.set(slot++, Items.icon(ability.icon(), Items.text(ability.label() + (level > 0 ? " " + Ability.roman(level) : ""),
               level > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY), lines(lore.toArray(String[]::new)), level > 0), (p, click) -> {
            int next = switch (click) {
               case SHIFT_RIGHT -> 0;
               case RIGHT, SHIFT_LEFT -> click == ClickType.RIGHT ? level - 1 : ability.maxLevel();
               default -> level + 1;
            };
            next = Math.max(0, Math.min(ability.maxLevel(), next));
            if (next == 0) {
               d.abilities.remove(ability.name());
            } else {
               d.abilities.put(ability.name(), next);
            }
            this.openAbilities(p, page);
         });
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, Items.icon(Material.ARROW, "Back to Editor", NamedTextColor.WHITE), (p, c) -> this.openEditor(p));
      menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
      player.openInventory(menu.getInventory());
   }

   // ---------------------------------------------------------------- helpers

   private void confirmDelete(Player player, String id) {
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Delete " + id + "?", NamedTextColor.DARK_RED), true);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(4, Items.icon(Material.PAPER, "Delete legendary '" + id + "'?", NamedTextColor.YELLOW,
            "Copies players already own stop", "having abilities. This cannot be undone."));
      menu.set(11, Items.icon(Material.LIME_CONCRETE, "Confirm", NamedTextColor.GREEN), (p, c) ->
            this.service.delete(id).whenComplete((ok, error) -> p.getScheduler().run(this.plugin, task -> {
               if (error != null) {
                  this.plugin.getLogger().log(Level.SEVERE, "Could not delete legendary " + id, error);
                  Messages.error(p, Messages.ACTION_FAILED);
                  return;
               }
               Draft open = this.drafts.get(p.getUniqueId());
               if (open != null && open.id.equals(id)) {
                  this.drafts.remove(p.getUniqueId());
               }
               Messages.success(p, "Deleted legendary '" + id + "'.");
               this.openList(p, 0);
            }, null)));
      menu.set(15, Items.icon(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED), (p, c) -> this.openList(p, 0));
      player.openInventory(menu.getInventory());
   }

   private void give(Player player, LegendaryDef def) {
      ItemStack item = LegendaryItems.build(def);
      player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
      Messages.success(player, "Gave you " + def.id() + ".");
   }

   public void forget(UUID player) {
      this.drafts.remove(player);
   }

   private static List<String> summary(Map<String, Integer> map, boolean enchants) {
      List<String> out = new ArrayList<>();
      map.forEach((k, v) -> {
         if (enchants) {
            out.add(pretty(k.contains(":") ? k.substring(k.indexOf(':') + 1) : k) + " " + v);
         } else {
            Ability ability = Ability.byId(k);
            out.add(ability == null ? k : ability.label() + " " + Ability.roman(v));
         }
      });
      if (out.isEmpty()) {
         out.add("None yet.");
      }
      out.add("Click to edit.");
      return out;
   }

   private static List<Component> lines(String... text) {
      List<Component> out = new ArrayList<>();
      for (String line : text) {
         out.add(Items.text(line, NamedTextColor.GRAY));
      }
      return out;
   }

   private static void appendLore(ItemStack item, String... legacyLines) {
      ItemMeta meta = item.getItemMeta();
      List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
      for (String line : legacyLines) {
         lore.add(LegendaryItems.text(line));
      }
      meta.lore(lore);
      item.setItemMeta(meta);
   }

   static String pretty(String id) {
      StringBuilder out = new StringBuilder();
      for (String part : id.split("_")) {
         if (!part.isEmpty()) {
            if (!out.isEmpty()) {
               out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
         }
      }
      return out.toString();
   }
}
