package net.srv.legendaryadditions.shopadmin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.srv.legendaryadditions.admin.suggestion.ChatInputManager;
import net.srv.legendaryadditions.admin.suggestion.gui.Items;
import net.srv.legendaryadditions.admin.suggestion.gui.Menu;
import net.srv.legendaryadditions.admin.util.Messages;
import net.srv.legendaryadditions.custom.Drill;
import net.srv.legendaryadditions.custom.Embershade;
import net.srv.legendaryadditions.custom.Paxel;
import net.srv.legendaryadditions.custom.TidefireCrossbow;
import net.srv.legendaryadditions.forge.LegendaryItems;
import net.srv.legendaryadditions.forge.LegendaryService;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * /shop admin: browse FoliaShop's categories, search every shop item, change prices, remove items
 * and add new ones - custom legendaries from the Legendary Creator, the built-in legendaries, any
 * vanilla item (searched by name) or the item in your hand.
 */
public final class ShopAdminGui {
   public static final String PERMISSION = "legendaryadditions.shopadmin";
   private static final Predicate<Player> ACCESS = p -> p.hasPermission(PERMISSION);
   private static final int INPUT_SECONDS = 120;
   private static final String TIMEOUT = "Shop admin input timed out.";
   private static final Map<String, java.util.function.Supplier<ItemStack>> BUILT_INS = new LinkedHashMap<>();

   static {
      BUILT_INS.put("drill", Drill::create);
      BUILT_INS.put("paxel", Paxel::create);
      BUILT_INS.put("embershade", Embershade::create);
      BUILT_INS.put("tidefire_crossbow", TidefireCrossbow::create);
   }

   private final Plugin plugin;
   private final FoliaShopStore store;
   private final LegendaryService legendaries;
   private final ChatInputManager chat;

   public ShopAdminGui(Plugin plugin, FoliaShopStore store, LegendaryService legendaries, ChatInputManager chat) {
      this.plugin = plugin;
      this.store = store;
      this.legendaries = legendaries;
      this.chat = chat;
   }

   private boolean check(Player player) {
      if (!ACCESS.test(player)) {
         Messages.error(player, Messages.NO_PERMISSION);
         return false;
      }
      if (!this.store.available()) {
         Messages.error(player, "FoliaShop is not installed, so there is no /shop to edit (plugins/FoliaShop/shops.yml not found).");
         return false;
      }
      return true;
   }

   /** Loads the shop file off-thread, then renders on the player's thread. */
   private void withShops(Player player, Consumer<List<FoliaShopStore.Category>> render) {
      this.store.load().whenComplete((cats, error) -> player.getScheduler().run(this.plugin, task -> {
         if (error != null) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not read FoliaShop shops.yml", error);
            Messages.error(player, "Could not read FoliaShop's shops.yml: " + rootMessage(error));
            return;
         }
         render.accept(cats);
      }, null));
   }

   // ---------------------------------------------------------------- categories

   public void openMain(Player player) {
      if (!this.check(player)) {
         return;
      }
      this.withShops(player, cats -> {
         Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Shop Admin", NamedTextColor.DARK_GREEN), ACCESS);
         int slot = 0;
         for (FoliaShopStore.Category cat : cats) {
            if (slot >= 45) {
               break;
            }
            menu.set(slot++, Items.icon(material(cat.icon()), legacy(cat.name()), lines("ID: " + cat.id(), cat.entries().size() + " items",
                  "Click to manage this category."), false), (p, c) -> this.openCategory(p, cat.id(), 0));
         }
         for (int i = 45; i < 54; i++) {
            menu.set(i, Items.filler());
         }
         menu.set(47, Items.icon(Material.SPYGLASS, "Search Shop Items", NamedTextColor.AQUA, "Find any item in any category", "by name, id or material."),
               (p, c) -> this.askSearch(p));
         menu.set(49, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
         menu.set(51, Items.icon(Material.REPEATER, "Reload FoliaShop", NamedTextColor.YELLOW, "Runs /foliashop reload."), (p, c) -> {
            this.store.reloadShop();
            Messages.success(p, "FoliaShop reloaded.");
         });
         player.openInventory(menu.getInventory());
      });
   }

   private void openCategory(Player player, String catId, int page) {
      if (!this.check(player)) {
         return;
      }
      this.withShops(player, cats -> {
         FoliaShopStore.Category cat = cats.stream().filter(c -> c.id().equals(catId)).findFirst().orElse(null);
         if (cat == null) {
            Messages.error(player, "That category no longer exists.");
            this.openMain(player);
            return;
         }
         List<FoliaShopStore.Entry> entries = new ArrayList<>(cat.entries());
         entries.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
         int pages = Math.max(1, (entries.size() + 44) / 45);
         int current = Math.max(0, Math.min(page, pages - 1));
         Menu menu = new Menu(player.getUniqueId(), 6, legacy(cat.name()).append(Items.text(" - Admin", NamedTextColor.DARK_GREEN)), ACCESS);
         int slot = 0;
         for (FoliaShopStore.Entry entry : entries.subList(current * 45, Math.min(entries.size(), current * 45 + 45))) {
            menu.set(slot++, this.entryIcon(entry, false), (p, click) -> this.entryClicked(p, entry, click, () -> this.openCategory(p, catId, current)));
         }
         for (int i = 45; i < 54; i++) {
            menu.set(i, Items.filler());
         }
         menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openMain(p));
         if (current > 0) {
            menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openCategory(p, catId, current - 1));
         }
         menu.set(49, Items.icon(Material.EMERALD_BLOCK, "Add Item", NamedTextColor.GREEN, "Add a custom legendary, a built-in", "legendary, any vanilla item or", "the item in your hand."),
               (p, c) -> this.openAddPicker(p, catId));
         if (current < pages - 1) {
            menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openCategory(p, catId, current + 1));
         }
         menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
         player.openInventory(menu.getInventory());
      });
   }

   private ItemStack entryIcon(FoliaShopStore.Entry entry, boolean showCategory) {
      ItemStack icon;
      LegendaryDef def = entry.legendaryId() == null ? null : this.legendaries.get(entry.legendaryId());
      icon = def != null ? LegendaryItems.build(def) : new ItemStack(material(entry.material()));
      ItemMeta meta = icon.getItemMeta();
      meta.customName(legacy(entry.name()));
      List<Component> lore = new ArrayList<>();
      if (showCategory) {
         lore.add(Items.text("Category: " + entry.category(), NamedTextColor.GRAY));
      }
      lore.add(Items.text("ID: " + entry.id() + (entry.enabled() ? "" : " (disabled)"), NamedTextColor.GRAY));
      lore.add(Items.text("Buy: " + money(entry.buy()) + "   Sell: " + money(entry.sell()), NamedTextColor.YELLOW));
      lore.add(Items.text("Amount: " + entry.amount() + "   Slot: " + entry.slot(), NamedTextColor.GRAY));
      if (entry.legendaryId() != null) {
         lore.add(Items.text("Gives legendary: " + entry.legendaryId(), NamedTextColor.LIGHT_PURPLE));
      }
      lore.add(Items.text("Left click: change prices", NamedTextColor.GREEN));
      lore.add(Items.text("Shift + right click: remove", NamedTextColor.RED));
      meta.lore(lore);
      icon.setItemMeta(meta);
      return icon;
   }

   private void entryClicked(Player player, FoliaShopStore.Entry entry, ClickType click, Runnable reopen) {
      if (click == ClickType.SHIFT_RIGHT) {
         this.confirm(player, "Remove " + entry.id() + " from " + entry.category() + "?", p -> this.store.remove(entry.category(), entry.id())
               .whenComplete((v, error) -> this.done(p, error, "Removed " + entry.id() + " from the shop.", reopen)), reopen);
      } else if (click.isLeftClick()) {
         boolean legendary = entry.legendaryId() != null;
         this.askPrices(player, legendary, (buy, sell, amount) -> this.store.setPrices(entry.category(), entry.id(), buy, sell, legendary ? 1 : amount)
               .whenComplete((v, error) -> this.done(player, error, "Updated " + entry.id() + ": buy " + money(buy) + ", sell " + money(sell) + ".", reopen)));
      }
   }

   // ---------------------------------------------------------------- search

   private void askSearch(Player player) {
      player.closeInventory();
      Messages.info(player, "Type what to search for (name, id or material), or 'cancel'.");
      this.chat.ask(player, INPUT_SECONDS, TIMEOUT, (p, text) -> this.openSearch(p, text, 0));
   }

   private void openSearch(Player player, String query, int page) {
      if (!this.check(player)) {
         return;
      }
      String q = query.toLowerCase(Locale.ROOT);
      this.withShops(player, cats -> {
         List<FoliaShopStore.Entry> hits = new ArrayList<>();
         for (FoliaShopStore.Category cat : cats) {
            for (FoliaShopStore.Entry e : cat.entries()) {
               String name = plain(e.name()).toLowerCase(Locale.ROOT);
               if (name.contains(q) || e.id().toLowerCase(Locale.ROOT).contains(q) || e.material().toLowerCase(Locale.ROOT).contains(q.replace(' ', '_'))) {
                  hits.add(e);
               }
            }
         }
         int pages = Math.max(1, (hits.size() + 44) / 45);
         int current = Math.max(0, Math.min(page, pages - 1));
         Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Search: " + query + " (" + hits.size() + ")", NamedTextColor.DARK_GREEN), ACCESS);
         int slot = 0;
         for (FoliaShopStore.Entry e : hits.subList(current * 45, Math.min(hits.size(), current * 45 + 45))) {
            menu.set(slot++, this.entryIcon(e, true), (p, click) -> this.entryClicked(p, e, click, () -> this.openSearch(p, query, current)));
         }
         if (hits.isEmpty()) {
            menu.set(22, Items.icon(Material.BARRIER, "No shop items match '" + query + "'", NamedTextColor.GRAY));
         }
         for (int i = 45; i < 54; i++) {
            menu.set(i, Items.filler());
         }
         menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openMain(p));
         if (current > 0) {
            menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openSearch(p, query, current - 1));
         }
         menu.set(49, Items.icon(Material.SPYGLASS, "New Search", NamedTextColor.AQUA), (p, c) -> this.askSearch(p));
         if (current < pages - 1) {
            menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openSearch(p, query, current + 1));
         }
         menu.set(53, Items.icon(Material.BARRIER, "Close", NamedTextColor.RED), (p, c) -> p.closeInventory());
         player.openInventory(menu.getInventory());
      });
   }

   // ---------------------------------------------------------------- adding

   private void openAddPicker(Player player, String catId) {
      if (!this.check(player)) {
         return;
      }
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Add to " + catId, NamedTextColor.DARK_GREEN), ACCESS);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(10, Items.icon(Material.NETHER_STAR, "Custom Legendaries (" + this.legendaries.all().size() + ")", NamedTextColor.LIGHT_PURPLE,
            "Items made in the Legendary Creator", "(/suggestionadmin)."), (p, c) -> this.openPickCustom(p, catId, 0));
      menu.set(12, Items.icon(Material.NETHERITE_PICKAXE, "Built-in Legendaries", NamedTextColor.GOLD, "Drill, Paxel, Embershade,", "Tidefire Crossbow."),
            (p, c) -> this.openPickBuiltIn(p, catId));
      menu.set(14, Items.icon(Material.SPYGLASS, "Search Vanilla Items", NamedTextColor.AQUA, "Type part of an item name,", "e.g. diamond or oak log."),
            (p, c) -> this.askVanilla(p, catId));
      menu.set(16, Items.icon(Material.CHEST, "Item In Your Hand", NamedTextColor.GREEN, "Adds exactly the item you are", "holding (uses FoliaShop's addheld)."),
            (p, c) -> this.addHeld(p, catId));
      menu.set(22, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openCategory(p, catId, 0));
      player.openInventory(menu.getInventory());
   }

   private void openPickCustom(Player player, String catId, int page) {
      List<LegendaryDef> all = this.legendaries.all();
      int pages = Math.max(1, (all.size() + 44) / 45);
      int current = Math.max(0, Math.min(page, pages - 1));
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Pick a custom legendary", NamedTextColor.DARK_PURPLE), ACCESS);
      int slot = 0;
      for (LegendaryDef def : all.subList(current * 45, Math.min(all.size(), current * 45 + 45))) {
         menu.set(slot++, LegendaryItems.build(def), (p, c) -> this.askPrices(p, true, (buy, sell, amount) -> {
            Map<String, Object> values = FoliaShopStore.legendaryValues(def);
            putPrices(values, buy, sell);
            this.add(p, catId, def.id(), values);
         }));
      }
      if (all.isEmpty()) {
         menu.set(22, Items.icon(Material.BARRIER, "No custom legendaries yet", NamedTextColor.GRAY, "Make one in /suggestionadmin", "(Legendary Creator)."));
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openAddPicker(p, catId));
      if (current > 0) {
         menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openPickCustom(p, catId, current - 1));
      }
      if (current < pages - 1) {
         menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openPickCustom(p, catId, current + 1));
      }
      player.openInventory(menu.getInventory());
   }

   private void openPickBuiltIn(Player player, String catId) {
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Pick a built-in legendary", NamedTextColor.GOLD), ACCESS);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      int slot = 11;
      for (Map.Entry<String, java.util.function.Supplier<ItemStack>> e : BUILT_INS.entrySet()) {
         ItemStack item = e.getValue().get();
         menu.set(slot++, item.clone(), (p, c) -> this.askPrices(p, true, (buy, sell, amount) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("enabled", true);
            values.put("material", item.getType().getKey().toString());
            ItemMeta meta = item.getItemMeta();
            if (meta.customName() != null) {
               values.put("name", LegacyComponentSerializer.legacyAmpersand().serialize(meta.customName()));
            } else if (meta.hasDisplayName()) {
               values.put("name", LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName()));
            }
            if (meta.lore() != null) {
               values.put("lore", meta.lore().stream().map(l -> LegacyComponentSerializer.legacyAmpersand().serialize(l)).toList());
            }
            values.put("glint", true);
            values.put("amount", 1);
            values.put("give-item", false);
            values.put("buy-commands", List.of(FoliaShopStore.GIVE_COMMAND + e.getKey()));
            values.put("tags", Map.of("legendaryadditions:item_id", e.getKey()));
            values.put("match", FoliaShopStore.matchNbt());
            putPrices(values, buy, sell);
            this.add(p, catId, e.getKey(), values);
         }));
      }
      menu.set(22, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openAddPicker(p, catId));
      player.openInventory(menu.getInventory());
   }

   private void askVanilla(Player player, String catId) {
      player.closeInventory();
      Messages.info(player, "Type part of an item name to search, e.g. 'diamond' or 'oak log'. Type 'cancel' to stop.");
      this.chat.ask(player, INPUT_SECONDS, TIMEOUT, (p, text) -> this.openVanilla(p, catId, text, 0));
   }

   private void openVanilla(Player player, String catId, String query, int page) {
      String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
      List<Material> hits = Arrays.stream(Material.values())
            .filter(m -> m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_"))
            .filter(m -> m.getKey().getKey().contains(q))
            .toList();
      int pages = Math.max(1, (hits.size() + 44) / 45);
      int current = Math.max(0, Math.min(page, pages - 1));
      Menu menu = new Menu(player.getUniqueId(), 6, Items.text("Items matching '" + query + "' (" + hits.size() + ")", NamedTextColor.DARK_GREEN), ACCESS);
      int slot = 0;
      for (Material m : hits.subList(current * 45, Math.min(hits.size(), current * 45 + 45))) {
         menu.set(slot++, Items.icon(m, Items.text(pretty(m.getKey().getKey()), NamedTextColor.WHITE), lines("Click to add to " + catId + "."), false),
               (p, c) -> this.askPrices(p, false, (buy, sell, amount) -> {
                  Map<String, Object> values = new LinkedHashMap<>();
                  values.put("enabled", true);
                  values.put("material", m.getKey().toString());
                  values.put("amount", Math.max(1, Math.min(m.getMaxStackSize(), amount)));
                  putPrices(values, buy, sell);
                  this.add(p, catId, m.getKey().getKey(), values);
               }));
      }
      if (hits.isEmpty()) {
         menu.set(22, Items.icon(Material.BARRIER, "No items match '" + query + "'", NamedTextColor.GRAY));
      }
      for (int i = 45; i < 54; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(45, Items.icon(Material.ARROW, "Back", NamedTextColor.WHITE), (p, c) -> this.openAddPicker(p, catId));
      if (current > 0) {
         menu.set(48, Items.icon(Material.ARROW, "Previous Page", NamedTextColor.YELLOW), (p, c) -> this.openVanilla(p, catId, query, current - 1));
      }
      menu.set(49, Items.icon(Material.SPYGLASS, "New Search", NamedTextColor.AQUA), (p, c) -> this.askVanilla(p, catId));
      if (current < pages - 1) {
         menu.set(50, Items.icon(Material.ARROW, "Next Page", NamedTextColor.YELLOW), (p, c) -> this.openVanilla(p, catId, query, current + 1));
      }
      player.openInventory(menu.getInventory());
   }

   private void addHeld(Player player, String catId) {
      ItemStack held = player.getInventory().getItemInMainHand();
      if (held.getType().isAir()) {
         Messages.error(player, "Hold the item you want to add first.");
         return;
      }
      String legendary = LegendaryItems.idOf(held);
      if (legendary != null && this.legendaries.get(legendary) != null) {
         // A custom legendary is better sold through the give command, which always hands out the latest version.
         LegendaryDef def = this.legendaries.get(legendary);
         this.askPrices(player, true, (buy, sell, amount) -> {
            Map<String, Object> values = FoliaShopStore.legendaryValues(def);
            putPrices(values, buy, sell);
            this.add(player, catId, def.id(), values);
         });
         return;
      }
      this.askPrices(player, false, (buy, sell, amount) -> {
         String id = FoliaShopStore.sanitize(held.getType().getKey().getKey()) + "_" + Long.toString(System.currentTimeMillis() % 100000, 36);
         this.withShops(player, cats -> {
            int slot = cats.stream().filter(c -> c.id().equals(catId)).findFirst()
                  .map(c -> freeSlot(c.entries())).orElse(0);
            String command = "foliashop addheld " + catId + " " + id + " " + plainNumber(buy) + " " + plainNumber(Math.max(0, sell)) + " "
                  + Math.max(1, amount) + " " + slot;
            // FoliaShop saves the exact held item itself (saved-items.yml) and reloads.
            player.performCommand(command);
            this.plugin.getLogger().info(player.getName() + " ran '" + command + "' from /shop admin.");
            player.getScheduler().runDelayed(this.plugin, t -> this.openCategory(player, catId, 0), null, 10L);
         });
      });
   }

   private void add(Player player, String catId, String baseId, Map<String, Object> values) {
      this.store.add(catId, baseId, values).whenComplete((id, error) -> this.done(player, error,
            "Added " + id + " to " + catId + ". It is in /shop now.", () -> this.openCategory(player, catId, 0)));
      this.plugin.getLogger().info(player.getName() + " added '" + baseId + "' to FoliaShop category '" + catId + "' from /shop admin.");
   }

   // ---------------------------------------------------------------- shared

   @FunctionalInterface
   private interface Prices {
      void accept(double buy, double sell, int amount);
   }

   private void askPrices(Player player, boolean legendary, Prices then) {
      player.closeInventory();
      Messages.info(player, legendary
            ? "Type the prices in chat: <buy> <sell>, e.g. '50000 0'. Sell 0 = players cannot sell it back. Type 'cancel' to stop."
            : "Type the prices in chat: <buy> <sell> [amount], e.g. '100 25 16'. Sell 0 = cannot be sold. Type 'cancel' to stop.");
      this.chat.ask(player, INPUT_SECONDS, TIMEOUT, (p, text) -> {
         String[] parts = text.trim().split("\\s+");
         try {
            double buy = Double.parseDouble(parts[0].replace(",", ""));
            double sell = parts.length > 1 ? Double.parseDouble(parts[1].replace(",", "")) : 0;
            int amount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
            if (buy < 0 || sell < 0 || amount < 1 || amount > 64 || !Double.isFinite(buy) || !Double.isFinite(sell)) {
               throw new NumberFormatException();
            }
            then.accept(buy, sell, amount);
         } catch (RuntimeException ex) {
            Messages.error(p, "Could not read '" + text + "'. Example: 100 25 16");
            this.askPrices(p, legendary, then);
         }
      });
   }

   private static void putPrices(Map<String, Object> values, double buy, double sell) {
      values.put("buy-price", FoliaShopStore.priceSection(buy));
      if (sell > 0) {
         values.put("sell-price", FoliaShopStore.priceSection(sell));
      }
   }

   private void done(Player player, Throwable error, String success, Runnable then) {
      player.getScheduler().run(this.plugin, task -> {
         if (error != null) {
            this.plugin.getLogger().log(Level.SEVERE, "Shop admin change failed", error);
            Messages.error(player, "Shop change failed: " + rootMessage(error));
         } else {
            Messages.success(player, success);
         }
         then.run();
      }, null);
   }

   private void confirm(Player player, String question, Consumer<Player> yes, Runnable no) {
      Menu menu = new Menu(player.getUniqueId(), 3, Items.text("Are you sure?", NamedTextColor.DARK_RED), ACCESS);
      for (int i = 0; i < 27; i++) {
         menu.set(i, Items.filler());
      }
      menu.set(4, Items.icon(Material.PAPER, question, NamedTextColor.YELLOW));
      menu.set(11, Items.icon(Material.LIME_CONCRETE, "Confirm", NamedTextColor.GREEN), (p, c) -> yes.accept(p));
      menu.set(15, Items.icon(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED), (p, c) -> no.run());
      player.openInventory(menu.getInventory());
   }

   private static int freeSlot(List<FoliaShopStore.Entry> entries) {
      boolean[] used = new boolean[45];
      int max = -1;
      for (FoliaShopStore.Entry e : entries) {
         if (e.slot() >= 0 && e.slot() < 45) {
            used[e.slot()] = true;
         }
         max = Math.max(max, e.slot());
      }
      for (int i = 0; i < 45; i++) {
         if (!used[i]) {
            return i;
         }
      }
      return max + 1;
   }

   private static Material material(String key) {
      Material m = key == null ? null : Material.matchMaterial(key);
      return m != null && m.isItem() && !m.isAir() ? m : Material.PAPER;
   }

   private static Component legacy(String text) {
      return LegendaryItems.text(text == null ? "" : text);
   }

   private static String plain(String legacyText) {
      return legacyText == null ? "" : legacyText.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
   }

   private static List<Component> lines(String... text) {
      List<Component> out = new ArrayList<>();
      for (String line : text) {
         out.add(Items.text(line, NamedTextColor.GRAY));
      }
      return out;
   }

   private static String money(double amount) {
      return amount < 0 ? "-" : amount == Math.rint(amount) ? String.format("%,.0f", amount) : String.format("%,.2f", amount);
   }

   private static String plainNumber(double amount) {
      return amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
   }

   private static String pretty(String id) {
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

   private static String rootMessage(Throwable error) {
      Throwable t = error;
      while (t.getCause() != null) {
         t = t.getCause();
      }
      return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
   }
}
