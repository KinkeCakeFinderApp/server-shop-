package net.srv.legendaryadditions.shopadmin;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import net.srv.legendaryadditions.forge.Ability;
import net.srv.legendaryadditions.forge.LegendaryItems;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Reads and edits FoliaShop's plugins/FoliaShop/shops.yml, then runs "foliashop reload" so the
 * change shows in /shop straight away. Every file access runs on one IO thread, never on a region
 * thread. Custom legendaries are sold as command products: FoliaShop charges the price and runs
 * "legendary_additions give %player% &lt;id&gt;" from the console, so buyers get the exact item.
 *
 * <p>Item prices: FoliaShop itself can only charge money or XP. An entry with an item price keeps
 * its money price in FoliaShop (0 when it costs only items) and runs "la_shopbuy" from the
 * console instead of giving the item; that command takes the items and hands out the product, or
 * refunds the money when the buyer does not have them. What it needs is stored in the entry's
 * "legendaryadditions" section: cost (serialized items), money, and give (legendary id) or
 * product (serialized item).</p>
 */
public final class FoliaShopStore {
   public static final String GIVE_COMMAND = "[console] legendary_additions give %player% ";
   public static final String BUY_COMMAND = "[console] la_shopbuy %player% ";
   public static final String DATA = "legendaryadditions";
   /** Keys that make FoliaShop build a product this class cannot rebuild, so no item price for those. */
   private static final List<String> SPECIAL_PRODUCTS = List.of("saved-item", "buffed-item", "item-spawner", "smart-spawner",
         "vanilla-spawner", "type", "items", "products");

   public record Entry(String category, String id, String material, String name, double buy, double sell, int amount,
                       int slot, boolean enabled, String legendaryId, List<ItemStack> itemCost) {}

   /** What la_shopbuy needs for one purchase. product is null when it gives a legendary. */
   public record Purchase(List<ItemStack> cost, double money, String legendaryId, ItemStack product, int amount, String name) {}

   public record Category(String id, String name, String icon, List<Entry> entries) {}

   private final Plugin plugin;
   private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "LegendaryAdditions-ShopAdminIO");
      thread.setDaemon(true);
      return thread;
   });

   public FoliaShopStore(Plugin plugin) {
      this.plugin = plugin;
   }

   /** Null when FoliaShop is not installed. */
   public File shopsFile() {
      Plugin shop = Bukkit.getPluginManager().getPlugin("FoliaShop");
      return shop == null ? null : new File(shop.getDataFolder(), "shops.yml");
   }

   public boolean available() {
      File file = this.shopsFile();
      return file != null && file.isFile();
   }

   public CompletableFuture<List<Category>> load() {
      return this.read(yaml -> {
         List<Category> out = new ArrayList<>();
         ConfigurationSection shops = yaml.getConfigurationSection("shops");
         if (shops == null) {
            return out;
         }
         for (String catId : shops.getKeys(false)) {
            ConfigurationSection cat = shops.getConfigurationSection(catId);
            if (cat == null) {
               continue;
            }
            List<Entry> entries = new ArrayList<>();
            ConfigurationSection items = cat.getConfigurationSection("items");
            if (items != null) {
               for (String itemId : items.getKeys(false)) {
                  ConfigurationSection item = items.getConfigurationSection(itemId);
                  if (item != null) {
                     entries.add(entry(catId, itemId, item));
                  }
               }
            }
            out.add(new Category(catId, cat.getString("name", catId), cat.getString("icon", "minecraft:chest"), entries));
         }
         return out;
      });
   }

   private static Entry entry(String catId, String itemId, ConfigurationSection item) {
      String legendary = legendaryOf(item);
      ConfigurationSection data = item.getConfigurationSection(DATA);
      List<ItemStack> cost = data == null ? List.of() : decodeAll(data.getStringList("cost"));
      double buy = data != null && item.getStringList("buy-commands").stream().anyMatch(c -> c.startsWith(BUY_COMMAND))
            ? data.getDouble("money", 0) : price(item, "buy");
      return new Entry(catId, itemId, item.getString("material", "minecraft:paper"), item.getString("name", itemId),
            buy, price(item, "sell"), item.getInt("amount", 1), item.getInt("slot", -1),
            item.getBoolean("enabled", true), legendary, cost);
   }

   private static String legendaryOf(ConfigurationSection item) {
      String legendary = item.getString(DATA + ".give");
      for (String command : item.getStringList("buy-commands")) {
         if (command.startsWith(GIVE_COMMAND)) {
            legendary = command.substring(GIVE_COMMAND.length()).trim();
         }
      }
      return legendary;
   }

   /** Reads what la_shopbuy needs; null when the entry no longer exists or has no item price data. */
   public CompletableFuture<Purchase> purchase(String category, String id) {
      return this.read(yaml -> {
         ConfigurationSection item = yaml.getConfigurationSection("shops." + category + ".items." + id);
         ConfigurationSection data = item == null ? null : item.getConfigurationSection(DATA);
         if (data == null) {
            return null;
         }
         String product = data.getString("product");
         return new Purchase(decodeAll(data.getStringList("cost")), data.getDouble("money", 0), data.getString("give"),
               product == null ? null : decode(product), Math.max(1, item.getInt("amount", 1)), item.getString("name", id));
      });
   }

   /** FoliaShop accepts both "buy: 10" and "buy-price: {provider, amount}". -1 = not set. */
   private static double price(ConfigurationSection item, String kind) {
      if (item.isConfigurationSection(kind + "-price")) {
         return item.getDouble(kind + "-price.amount", -1);
      }
      return item.contains(kind) ? item.getDouble(kind, -1) : -1;
   }

   /** Adds an entry with a unique id in {@code category}. @return the id that was used. */
   public CompletableFuture<String> add(String category, String baseId, Map<String, Object> values) {
      return this.add(category, baseId, values, List.of(), null);
   }

   /**
    * Adds an entry; with a non-empty {@code cost} (or a {@code product} FoliaShop cannot give by
    * itself, such as a held item) it is sold through la_shopbuy.
    */
   public CompletableFuture<String> add(String category, String baseId, Map<String, Object> values, List<ItemStack> cost,
                                        ItemStack product) {
      return this.write(yaml -> {
         ConfigurationSection cat = yaml.getConfigurationSection("shops." + category);
         if (cat == null) {
            throw new IllegalStateException("Shop category '" + category + "' no longer exists.");
         }
         ConfigurationSection items = cat.isConfigurationSection("items") ? cat.getConfigurationSection("items") : cat.createSection("items");
         String id = sanitize(baseId);
         for (int n = 2; items.contains(id); n++) {
            id = sanitize(baseId) + "_" + n;
         }
         Map<String, Object> ordered = new LinkedHashMap<>(values);
         ordered.put("slot", freeSlot(items));
         ConfigurationSection item = items.createSection(id, ordered);
         if (product != null) {
            ItemStack one = product.clone();
            one.setAmount(1);
            item.set(DATA + ".product", encode(one));
         }
         if (!cost.isEmpty() || product != null) {
            ConfigurationSection buy = item.getConfigurationSection("buy-price");
            double money = buy == null ? Math.max(0, item.getDouble("buy", 0)) : buy.getDouble("amount", 0);
            ConfigurationSection sell = item.getConfigurationSection("sell-price");
            applyPricing(item, category, id, money, sell == null ? -1 : sell.getDouble("amount", -1), item.getInt("amount", 1), cost);
         }
         return id;
      });
   }

   public CompletableFuture<Void> remove(String category, String id) {
      return this.write(yaml -> {
         yaml.set("shops." + category + ".items." + id, null);
         return null;
      });
   }

   public CompletableFuture<Void> setPrices(String category, String id, double buy, double sell, int amount) {
      return this.setPrices(category, id, buy, sell, amount, List.of());
   }

   /** Money prices plus an optional item price ({@code cost}; empty = money only). */
   public CompletableFuture<Void> setPrices(String category, String id, double buy, double sell, int amount, List<ItemStack> cost) {
      return this.write(yaml -> {
         ConfigurationSection item = yaml.getConfigurationSection("shops." + category + ".items." + id);
         if (item == null) {
            throw new IllegalStateException("That shop item no longer exists.");
         }
         applyPricing(item, category, id, buy, sell, amount, cost);
         return null;
      });
   }

   private void applyPricing(ConfigurationSection item, String category, String id, double buy, double sell, int amount,
                             List<ItemStack> cost) {
      item.set("buy", null);
      item.set("sell", null);
      item.set("sell-price", sell > 0 ? priceSection(sell) : null);
      item.set("amount", amount);
      ConfigurationSection data = item.getConfigurationSection(DATA);
      if (cost.isEmpty() && data == null) {
         item.set("buy-price", priceSection(buy));
         return;
      }
      if (data == null) {
         // First item price on this entry: remember what the buyer gets before FoliaShop stops giving it.
         String legendary = legendaryOf(item);
         ItemStack product = null;
         if (legendary == null) {
            if (!item.getBoolean("give-item", true) || !item.getStringList("buy-commands").isEmpty()) {
               throw new IllegalStateException("This item runs its own buy commands, so it cannot have an item price.");
            }
            for (String key : SPECIAL_PRODUCTS) {
               if (item.contains(key)) {
                  throw new IllegalStateException("Item prices are not supported for FoliaShop '" + key + "' items.");
               }
            }
            product = productFromYaml(item);
         }
         data = item.createSection(DATA);
         data.set("give", legendary);
         data.set("product", product == null ? null : encode(product));
         data.set("lore", item.getStringList("lore"));
      }
      data.set("money", Math.max(0, buy));
      data.set("cost", cost.stream().map(FoliaShopStore::encode).toList());
      // Buy price 0 is allowed by FoliaShop (free); XP points need no economy plugin.
      item.set("buy-price", buy > 0 ? priceSection(buy) : freePrice());
      item.set("give-item", false);
      item.set("buy-commands", List.of(BUY_COMMAND + category + " " + id));
      item.set("lore", withCostLore(data.getStringList("lore"), cost));
   }

   private static List<String> withCostLore(List<String> lore, List<ItemStack> cost) {
      List<String> out = new ArrayList<>(lore);
      if (!cost.isEmpty()) {
         out.add("&6Also costs:");
         for (ItemStack stack : cost) {
            out.add("&e - " + stack.getAmount() + "x " + itemName(stack));
         }
      }
      return out;
   }

   /** The plain name of an item: its custom name, or the material name. */
   public static String itemName(ItemStack stack) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null && meta.customName() != null) {
         return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.customName());
      }
      if (meta != null && meta.hasItemName()) {
         return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.itemName());
      }
      StringBuilder out = new StringBuilder();
      for (String part : stack.getType().getKey().getKey().split("_")) {
         if (!part.isEmpty()) {
            out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
         }
      }
      return out.toString();
   }

   /** The item FoliaShop would have given for a plain material entry (name and lore are display-only there). */
   private static ItemStack productFromYaml(ConfigurationSection item) {
      ItemStack product = new ItemStack(LegendaryItems.material(item.getString("material", "minecraft:stone")));
      ItemMeta meta = product.getItemMeta();
      if (meta != null) {
         ConfigurationSection enchants = item.getConfigurationSection("enchantments");
         if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
               Enchantment enchantment = LegendaryItems.enchantment(key.contains(":") ? key : "minecraft:" + key);
               if (enchantment != null) {
                  meta.addEnchant(enchantment, enchants.getInt(key, 1), true);
               }
            }
         }
         if (item.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
         }
         product.setItemMeta(meta);
      }
      return product;
   }

   /**
    * "amount:base64" of the item with amount 1, because a saved item stack can hold at most 99 and
    * an item price can ask for more. Plain base64 (older saves) is still read by {@link #decode}.
    */
   public static String encode(ItemStack stack) {
      ItemStack one = stack.clone();
      one.setAmount(1);
      return stack.getAmount() + ":" + Base64.getEncoder().encodeToString(one.serializeAsBytes());
   }

   public static ItemStack decode(String data) {
      int colon = data.indexOf(':');
      if (colon < 0) {
         return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
      }
      ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(data.substring(colon + 1)));
      stack.setAmount(Math.max(1, Integer.parseInt(data.substring(0, colon))));
      return stack;
   }

   private static List<ItemStack> decodeAll(List<String> data) {
      List<ItemStack> out = new ArrayList<>();
      for (String s : data) {
         try {
            out.add(decode(s));
         } catch (RuntimeException ignored) {
            // An item from a newer or broken save is skipped rather than breaking the whole shop.
         }
      }
      return out;
   }

   /** Keeps every shop entry that sells {@code def} in step with its latest saved version. */
   public CompletableFuture<Integer> syncLegendary(LegendaryDef def) {
      if (!this.available()) {
         return CompletableFuture.completedFuture(0);
      }
      return this.write(yaml -> {
         int updated = 0;
         ConfigurationSection shops = yaml.getConfigurationSection("shops");
         if (shops == null) {
            return 0;
         }
         for (String catId : shops.getKeys(false)) {
            ConfigurationSection items = shops.getConfigurationSection(catId + ".items");
            if (items == null) {
               continue;
            }
            for (String itemId : items.getKeys(false)) {
               ConfigurationSection item = items.getConfigurationSection(itemId);
               if (item != null && def.id().equals(entry(catId, itemId, item).legendaryId())) {
                  legendaryValues(def).forEach((k, v) -> {
                     if (!k.equals("buy-commands") && !k.equals("give-item")) {
                        item.set(k, v);
                     }
                  });
                  if (def.modelData() == null) {
                     item.set("custom-model-data", null);
                  }
                  ConfigurationSection data = item.getConfigurationSection(DATA);
                  if (data != null) {
                     data.set("lore", item.getStringList("lore"));
                     item.set("lore", withCostLore(item.getStringList("lore"), decodeAll(data.getStringList("cost"))));
                  }
                  updated++;
               }
            }
         }
         return updated;
      }).thenApply(n -> n);
   }

   /** Shop entry values for a custom legendary, without slot and prices. */
   public static Map<String, Object> legendaryValues(LegendaryDef def) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("enabled", true);
      values.put("material", LegendaryItems.material(def.material()).getKey().toString());
      values.put("name", def.name());
      List<String> lore = new ArrayList<>(def.lore());
      LegendaryItems.abilities(def).forEach((ability, level) ->
            lore.add("&6" + ability.label() + (ability.maxLevel() > 1 ? " " + Ability.roman(level) : "")));
      values.put("lore", lore);
      Map<String, Object> enchants = new LinkedHashMap<>();
      def.enchants().forEach((key, level) -> enchants.put(key.startsWith("minecraft:") ? key.substring(10) : key, level));
      if (!enchants.isEmpty()) {
         values.put("enchantments", enchants);
      }
      values.put("unbreakable", def.unbreakable());
      values.put("glint", def.glow());
      if (def.modelData() != null) {
         values.put("custom-model-data", def.modelData());
      }
      values.put("amount", 1);
      values.put("give-item", false);
      values.put("buy-commands", List.of(GIVE_COMMAND + def.id()));
      values.put("tags", Map.of("legendaryadditions:custom_id", def.id()));
      values.put("match", matchNbt());
      return values;
   }

   public static Map<String, Object> matchNbt() {
      Map<String, Object> match = new LinkedHashMap<>();
      match.put("name", false);
      match.put("lore", false);
      match.put("nbt", true);
      return match;
   }

   /** A free FoliaShop price that needs no economy plugin (0 XP points). */
   public static Map<String, Object> freePrice() {
      Map<String, Object> price = new LinkedHashMap<>();
      price.put("provider", "xp_points");
      price.put("amount", 0.0);
      return price;
   }

   public static Map<String, Object> priceSection(double amount) {
      Map<String, Object> price = new LinkedHashMap<>();
      price.put("provider", "vault");
      price.put("amount", amount);
      return price;
   }

   /** Runs FoliaShop's own reload from the console on the global region thread. */
   public void reloadShop() {
      Bukkit.getGlobalRegionScheduler().execute(this.plugin,
            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "foliashop reload"));
   }

   private static int freeSlot(ConfigurationSection items) {
      boolean[] used = new boolean[45];
      int max = -1;
      for (String key : items.getKeys(false)) {
         int slot = items.getInt(key + ".slot", -1);
         if (slot >= 0 && slot < 45) {
            used[slot] = true;
         }
         max = Math.max(max, slot);
      }
      for (int i = 0; i < 45; i++) {
         if (!used[i]) {
            return i;
         }
      }
      return max + 1;
   }

   public static String sanitize(String id) {
      String clean = id.toLowerCase(Locale.ROOT).replace("minecraft:", "").replaceAll("[^a-z0-9_]", "_");
      return clean.isEmpty() ? "item" : clean;
   }

   private <T> CompletableFuture<T> read(Function<YamlConfiguration, T> reader) {
      return CompletableFuture.supplyAsync(() -> reader.apply(this.loadYaml()), this.io);
   }

   private <T> CompletableFuture<T> write(Function<YamlConfiguration, T> change) {
      return CompletableFuture.supplyAsync(() -> {
         YamlConfiguration yaml = this.loadYaml();
         T result = change.apply(yaml);
         try {
            yaml.save(this.shopsFile());
         } catch (Exception ex) {
            throw new CompletionException(ex);
         }
         this.reloadShop();
         return result;
      }, this.io);
   }

   private YamlConfiguration loadYaml() {
      File file = this.shopsFile();
      if (file == null || !file.isFile()) {
         throw new IllegalStateException("FoliaShop is not installed (plugins/FoliaShop/shops.yml not found).");
      }
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.options().parseComments(true);
      try {
         yaml.load(file);
      } catch (Exception ex) {
         throw new CompletionException(ex);
      }
      return yaml;
   }

   public void close() {
      this.io.shutdown();
   }
}
