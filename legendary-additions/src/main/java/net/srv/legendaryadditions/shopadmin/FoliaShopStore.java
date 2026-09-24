package net.srv.legendaryadditions.shopadmin;

import java.io.File;
import java.util.ArrayList;
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
import org.bukkit.plugin.Plugin;

/**
 * Reads and edits FoliaShop's plugins/FoliaShop/shops.yml, then runs "foliashop reload" so the
 * change shows in /shop straight away. Every file access runs on one IO thread, never on a region
 * thread. Custom legendaries are sold as command products: FoliaShop charges the price and runs
 * "legendary_additions give %player% &lt;id&gt;" from the console, so buyers get the exact item.
 */
public final class FoliaShopStore {
   public static final String GIVE_COMMAND = "[console] legendary_additions give %player% ";

   public record Entry(String category, String id, String material, String name, double buy, double sell, int amount,
                       int slot, boolean enabled, String legendaryId) {}

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
      String legendary = null;
      for (String command : item.getStringList("buy-commands")) {
         if (command.startsWith(GIVE_COMMAND)) {
            legendary = command.substring(GIVE_COMMAND.length()).trim();
         }
      }
      return new Entry(catId, itemId, item.getString("material", "minecraft:paper"), item.getString("name", itemId),
            price(item, "buy"), price(item, "sell"), item.getInt("amount", 1), item.getInt("slot", -1),
            item.getBoolean("enabled", true), legendary);
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
         items.createSection(id, ordered);
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
      return this.write(yaml -> {
         ConfigurationSection item = yaml.getConfigurationSection("shops." + category + ".items." + id);
         if (item == null) {
            throw new IllegalStateException("That shop item no longer exists.");
         }
         item.set("buy", null);
         item.set("sell", null);
         item.set("buy-price", priceSection(buy));
         item.set("sell-price", sell > 0 ? priceSection(sell) : null);
         item.set("amount", amount);
         return null;
      });
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
