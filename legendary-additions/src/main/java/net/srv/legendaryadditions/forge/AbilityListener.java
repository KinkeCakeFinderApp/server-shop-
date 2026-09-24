package net.srv.legendaryadditions.forge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.effect.ExplosionGuard;
import net.srv.legendaryadditions.forge.data.LegendaryDef;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Runs every ability of admin-made legendaries. Each handler reads the item's saved definition
 * from the in-memory cache, so it never blocks a region thread. Extra blocks from Vein Miner, Tree
 * Capitator and Area Miner are broken with {@link Player#breakBlock}, which fires a normal
 * BlockBreakEvent for each one - land-claim and protection plugins can still stop them.
 */
public final class AbilityListener implements Listener {
   private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> false);
   private static final BlockFace[] PLANE_FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
         BlockFace.EAST, BlockFace.WEST};

   private final Plugin plugin;
   private final Map<Material, ItemStack> smelting = new EnumMap<>(Material.class);
   private final Map<UUID, Long> lastDash = new ConcurrentHashMap<>();

   public AbilityListener(Plugin plugin) {
      this.plugin = plugin;
      Iterator<Recipe> recipes = Bukkit.recipeIterator();
      while (recipes.hasNext()) {
         if (recipes.next() instanceof FurnaceRecipe furnace && furnace.getInputChoice() instanceof RecipeChoice.MaterialChoice choice) {
            for (Material input : choice.getChoices()) {
               this.smelting.putIfAbsent(input, furnace.getResult());
            }
         }
      }
      Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, timer -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> this.passives(player), null);
         }
      }, 20L, 20L);
   }

   // ---------------------------------------------------------------- mining

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      ItemStack tool = player.getInventory().getItemInMainHand();
      LegendaryDef def = LegendaryItems.defOf(tool);
      if (def == null) {
         return;
      }
      Map<Ability, Integer> abilities = LegendaryItems.abilities(def);
      Block block = event.getBlock();
      Material type = block.getType();

      Integer wisdom = abilities.get(Ability.WISDOM);
      if (wisdom != null && event.getExpToDrop() > 0) {
         event.setExpToDrop((int) Math.round(event.getExpToDrop() * (1.0 + 0.5 * wisdom)));
      }
      if (abilities.containsKey(Ability.REPLANT)) {
         this.replant(block);
      }
      if (CHAINING.get()) {
         return;
      }

      Set<Block> extra = new LinkedHashSet<>();
      Integer vein = abilities.get(Ability.VEIN_MINE);
      if (vein != null && isOre(type)) {
         extra.addAll(connected(block, type, 16 * vein));
      }
      Integer timber = abilities.get(Ability.TREE_CAPITATOR);
      if (timber != null && Tag.LOGS.isTagged(type)) {
         extra.addAll(connected(block, type, 64 * timber));
      }
      Integer area = abilities.get(Ability.AREA_MINE);
      if (area != null) {
         for (Block b : plane(block, player, area)) {
            if (!b.getType().isAir() && !b.isLiquid() && b.getType().getHardness() >= 0 && b.isPreferredTool(tool)) {
               extra.add(b);
            }
         }
      }
      extra.remove(block);
      if (extra.isEmpty()) {
         return;
      }
      CHAINING.set(true);
      try {
         for (Block b : extra) {
            if (player.getInventory().getItemInMainHand().getType().isAir()) {
               break; // the tool broke
            }
            if (Bukkit.isOwnedByCurrentRegion(b)) {
               player.breakBlock(b);
            }
         }
      } finally {
         CHAINING.set(false);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBlockDrop(BlockDropItemEvent event) {
      Player player = event.getPlayer();
      LegendaryDef def = LegendaryItems.defOf(player.getInventory().getItemInMainHand());
      if (def == null) {
         return;
      }
      Map<Ability, Integer> abilities = LegendaryItems.abilities(def);
      if (abilities.containsKey(Ability.AUTO_SMELT)) {
         for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            ItemStack result = this.smelting.get(stack.getType());
            if (result != null) {
               item.setItemStack(new ItemStack(result.getType(), Math.min(64, stack.getAmount() * result.getAmount())));
            }
         }
      }
      if (abilities.containsKey(Ability.TELEKINESIS)) {
         event.getItems().removeIf(item -> collect(player, item));
      }
   }

   /** Moves an item into the player's inventory. @return true if all of it fitted. */
   private static boolean collect(Player player, Item item) {
      Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.getItemStack());
      if (leftover.isEmpty()) {
         return true;
      }
      item.setItemStack(leftover.values().iterator().next());
      return false;
   }

   private void replant(Block block) {
      if (!(block.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) {
         return;
      }
      Material crop = block.getType();
      Material soil = crop == Material.NETHER_WART ? Material.SOUL_SAND : Material.FARMLAND;
      if (crop != Material.WHEAT && crop != Material.CARROTS && crop != Material.POTATOES && crop != Material.BEETROOTS
            && crop != Material.NETHER_WART) {
         return;
      }
      Location at = block.getLocation();
      Bukkit.getRegionScheduler().runDelayed(this.plugin, at, task -> {
         Block b = at.getBlock();
         if (b.getType().isAir() && b.getRelative(BlockFace.DOWN).getType() == soil) {
            b.setType(crop);
         }
      }, 2L);
   }

   private static boolean isOre(Material type) {
      String name = type.name();
      return name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
   }

   /** Blocks of the same type touching {@code origin} (including diagonals), breadth first. */
   private static List<Block> connected(Block origin, Material type, int limit) {
      Set<Block> seen = new LinkedHashSet<>();
      Deque<Block> queue = new ArrayDeque<>();
      seen.add(origin);
      queue.add(origin);
      while (!queue.isEmpty() && seen.size() < limit + 1) {
         Block current = queue.poll();
         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if ((dx != 0 || dy != 0 || dz != 0) && seen.size() < limit + 1) {
                     Block next = current.getRelative(dx, dy, dz);
                     if (!seen.contains(next) && Bukkit.isOwnedByCurrentRegion(next) && next.getType() == type) {
                        seen.add(next);
                        queue.add(next);
                     }
                  }
               }
            }
         }
      }
      return new ArrayList<>(seen);
   }

   /** The (2r+1)x(2r+1) plane through {@code center} facing the way the player is looking. */
   private static List<Block> plane(Block center, Player player, int radius) {
      Vector dir = player.getEyeLocation().getDirection();
      double ax = Math.abs(dir.getX());
      double ay = Math.abs(dir.getY());
      double az = Math.abs(dir.getZ());
      BlockFace axis = ay >= ax && ay >= az ? BlockFace.UP : ax >= az ? BlockFace.EAST : BlockFace.SOUTH;
      List<Block> blocks = new ArrayList<>();
      for (int a = -radius; a <= radius; a++) {
         for (int b = -radius; b <= radius; b++) {
            blocks.add(switch (axis) {
               case UP -> center.getRelative(a, 0, b);
               case SOUTH -> center.getRelative(a, b, 0);
               default -> center.getRelative(0, b, a);
            });
         }
      }
      return blocks;
   }

   // ---------------------------------------------------------------- combat

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onShoot(EntityShootBowEvent event) {
      if (event.getEntity() instanceof Player && LegendaryItems.defOf(event.getBow()) != null) {
         event.getProjectile().getPersistentDataContainer().set(LegendaryItems.idKey(), PersistentDataType.STRING,
               LegendaryItems.idOf(event.getBow()));
      }
   }

   private static LegendaryDef projectileDef(Entity projectile) {
      String id = projectile.getPersistentDataContainer().get(LegendaryItems.idKey(), PersistentDataType.STRING);
      return id == null ? null : LegendaryItems.service().get(id);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onHit(EntityDamageByEntityEvent event) {
      if (!(event.getEntity() instanceof LivingEntity victim)) {
         return;
      }
      Player attacker;
      LegendaryDef def;
      if (event.getDamager() instanceof Player p && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
         attacker = p;
         def = LegendaryItems.defOf(p.getInventory().getItemInMainHand());
      } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
         attacker = p;
         def = projectileDef(projectile);
      } else {
         return;
      }
      if (def == null || victim.equals(attacker)) {
         return;
      }
      Map<Ability, Integer> abilities = LegendaryItems.abilities(def);
      ThreadLocalRandom random = ThreadLocalRandom.current();

      Integer crit = abilities.get(Ability.CRITICAL);
      if (crit != null && random.nextDouble() < 0.1 * crit) {
         event.setDamage(event.getDamage() * 2.0);
      }
      Integer thunder = abilities.get(Ability.THUNDERLORD);
      if (thunder != null && random.nextDouble() < 0.1 * thunder) {
         victim.getWorld().strikeLightningEffect(victim.getLocation());
         event.setDamage(event.getDamage() + 3.0 + 2.0 * thunder);
      }
      Integer venom = abilities.get(Ability.VENOM);
      if (venom != null) {
         victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60 + 40 * venom, venom - 1));
      }
      Integer wither = abilities.get(Ability.WITHERING);
      if (wither != null) {
         victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60 + 40 * wither, wither - 1));
      }
      Integer frost = abilities.get(Ability.FROST);
      if (frost != null) {
         victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * frost, frost - 1));
         victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), victim.getMaxFreezeTicks() + 20 * frost));
      }
      Integer ignite = abilities.get(Ability.IGNITE);
      if (ignite != null) {
         victim.setFireTicks(Math.max(victim.getFireTicks(), 40 * ignite));
      }
      Integer lifesteal = abilities.get(Ability.LIFESTEAL);
      if (lifesteal != null) {
         double heal = event.getDamage() * 0.05 * lifesteal;
         if (Bukkit.isOwnedByCurrentRegion(attacker)) {
            heal(attacker, heal);
         } else {
            attacker.getScheduler().run(this.plugin, task -> heal(attacker, heal), null);
         }
      }
   }

   private static void heal(Player player, double amount) {
      if (player.isDead()) {
         return;
      }
      AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
      double cap = max == null ? 20.0 : max.getValue();
      player.setHealth(Math.min(cap, player.getHealth() + amount));
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onProjectileLand(ProjectileHitEvent event) {
      Projectile projectile = event.getEntity();
      LegendaryDef def = projectileDef(projectile);
      if (def == null) {
         return;
      }
      Integer level = LegendaryItems.abilities(def).get(Ability.EXPLOSIVE_ARROWS);
      if (level == null) {
         return;
      }
      Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation() : projectile.getLocation();
      UUID owner = projectile.getShooter() instanceof Player p ? p.getUniqueId() : null;
      projectile.getPersistentDataContainer().remove(LegendaryItems.idKey());
      ExplosionGuard.explode(at, 1.0F + level, false, false, owner);
      projectile.remove();
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDeath(EntityDeathEvent event) {
      LivingEntity dead = event.getEntity();
      Player killer = dead.getKiller();
      if (killer == null || !Bukkit.isOwnedByCurrentRegion(killer)) {
         return;
      }
      LegendaryDef def = LegendaryItems.defOf(killer.getInventory().getItemInMainHand());
      if (def == null) {
         return;
      }
      Map<Ability, Integer> abilities = LegendaryItems.abilities(def);
      Integer wisdom = abilities.get(Ability.WISDOM);
      if (wisdom != null) {
         event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1.0 + 0.5 * wisdom)));
      }
      Integer beheading = abilities.get(Ability.BEHEADING);
      if (beheading != null && ThreadLocalRandom.current().nextDouble() < 0.1 * beheading) {
         ItemStack head = head(dead);
         if (head != null) {
            event.getDrops().add(head);
         }
      }
      if (abilities.containsKey(Ability.TELEKINESIS) && !(dead instanceof Player)) {
         List<ItemStack> kept = new ArrayList<>();
         for (ItemStack drop : event.getDrops()) {
            kept.addAll(killer.getInventory().addItem(drop).values());
         }
         event.getDrops().clear();
         event.getDrops().addAll(kept);
         killer.giveExp(event.getDroppedExp());
         event.setDroppedExp(0);
      }
   }

   private static ItemStack head(LivingEntity dead) {
      if (dead instanceof Player victim) {
         ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
         SkullMeta meta = (SkullMeta) skull.getItemMeta();
         meta.setOwningPlayer(victim);
         skull.setItemMeta(meta);
         return skull;
      }
      EntityType type = dead.getType();
      Material head = switch (type) {
         case ZOMBIE -> Material.ZOMBIE_HEAD;
         case SKELETON -> Material.SKELETON_SKULL;
         case CREEPER -> Material.CREEPER_HEAD;
         case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
         case PIGLIN -> Material.PIGLIN_HEAD;
         case ENDER_DRAGON -> Material.DRAGON_HEAD;
         default -> null;
      };
      return head == null ? null : new ItemStack(head);
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPlayerDeath(PlayerDeathEvent event) {
      if (event.getKeepInventory()) {
         return;
      }
      Iterator<ItemStack> drops = event.getDrops().iterator();
      while (drops.hasNext()) {
         ItemStack item = drops.next();
         if (LegendaryItems.level(item, Ability.SOULBOUND) > 0) {
            drops.remove();
            event.getItemsToKeep().add(item);
         }
      }
   }

   // ---------------------------------------------------------------- movement and passives

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onFall(EntityDamageEvent event) {
      if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
            && equippedLevels(player).containsKey(Ability.FEATHERWEIGHT)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onDash(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND
            || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
         return;
      }
      Player player = event.getPlayer();
      int level = LegendaryItems.level(player.getInventory().getItemInMainHand(), Ability.DASH);
      if (level == 0) {
         return;
      }
      long now = System.currentTimeMillis();
      Long last = this.lastDash.get(player.getUniqueId());
      if (last != null && now - last < 750) {
         return; // one dash per click, not one per repeated interact packet
      }
      this.lastDash.put(player.getUniqueId(), now);
      Vector velocity = player.getLocation().getDirection().multiply(0.8 + 0.4 * level);
      velocity.setY(Math.max(velocity.getY(), 0.25));
      player.setVelocity(velocity);
      player.setFallDistance(0F);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastDash.remove(event.getPlayer().getUniqueId());
   }

   /** Highest level of every ability on items in either hand or in an armour slot. */
   private static Map<Ability, Integer> equippedLevels(Player player) {
      Map<Ability, Integer> levels = new EnumMap<>(Ability.class);
      List<ItemStack> items = new ArrayList<>(List.of(player.getInventory().getArmorContents()));
      items.add(player.getInventory().getItemInMainHand());
      items.add(player.getInventory().getItemInOffHand());
      for (ItemStack item : items) {
         LegendaryDef def = LegendaryItems.defOf(item);
         if (def != null) {
            LegendaryItems.abilities(def).forEach((ability, level) -> levels.merge(ability, level, Math::max));
         }
      }
      return levels;
   }

   private void passives(Player player) {
      if (!player.isOnline() || player.isDead()) {
         return;
      }
      Map<Ability, Integer> levels = equippedLevels(player);
      if (levels.isEmpty()) {
         return;
      }
      for (Map.Entry<Ability, Integer> e : levels.entrySet()) {
         PotionEffectType potion = e.getKey().potion();
         if (potion != null) {
            // Night vision flickers below 10 seconds, so it gets a longer refresh.
            int duration = potion == PotionEffectType.NIGHT_VISION ? 300 : 60;
            player.addPotionEffect(new PotionEffect(potion, duration, e.getValue() - 1, true, false, true));
         }
      }
      Integer magnet = levels.get(Ability.MAGNET);
      if (magnet != null) {
         double r = 4.0 * magnet;
         Location to = player.getLocation();
         for (Entity nearby : player.getNearbyEntities(r, r, r)) {
            if (nearby instanceof Item item && item.getPickupDelay() <= 0) {
               item.teleportAsync(to);
            }
         }
      }
   }
}
