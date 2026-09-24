package net.srv.legendaryadditions.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.srv.legendaryadditions.admin.dimension.AdminDimension;
import net.srv.legendaryadditions.admin.effect.SafeLocations;
import net.srv.legendaryadditions.admin.rod.RodKeys;
import net.srv.legendaryadditions.admin.rod.RodKind;
import net.srv.legendaryadditions.admin.rod.RodListener;
import net.srv.legendaryadditions.admin.rod.RodRegistry;
import net.srv.legendaryadditions.admin.suggestion.SuggestionService;
import net.srv.legendaryadditions.admin.suggestion.data.Results;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionCategory;
import net.srv.legendaryadditions.admin.suggestion.data.SuggestionStatus;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * In-server checks used by CI. Runs only when the server is started with
 * {@code -Dlegendaryadditions.selftest=true}; normal servers never execute any of this.
 * Results are logged as "SELFTEST PASS ..." / "SELFTEST FAIL ..." lines.
 */
public final class SelfTest {
   public static final String PROPERTY = "legendaryadditions.selftest";

   private final Plugin plugin;
   private final Logger log;
   private final List<String> failures = java.util.Collections.synchronizedList(new ArrayList<>());

   public SelfTest(Plugin plugin) {
      this.plugin = plugin;
      this.log = plugin.getLogger();
   }

   public static boolean enabled() {
      return Boolean.getBoolean(PROPERTY);
   }

   public void run(RodRegistry registry, AdminDimension dimension, SuggestionService suggestions) {
      Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> {
         this.rods(registry);
         this.suggestions(suggestions);
         this.dimension(dimension);
      }, 40L);
   }

   private void check(boolean condition, String name) {
      if (condition) {
         this.log.info("SELFTEST PASS " + name);
      } else {
         this.failures.add(name);
         this.log.severe("SELFTEST FAIL " + name);
      }
   }

   private void rods(RodRegistry registry) {
      for (RodKind kind : RodKind.values()) {
         ItemStack reusable = registry.createRod(kind, true);
         RodRegistry.Inspection r = registry.inspect(reusable);
         check(r.status() == RodRegistry.Status.GENUINE && r.kind() == kind && r.reusable(), kind + " reusable rod is genuine");
         check(reusable.getType() == Material.FISHING_ROD && reusable.getAmount() == 1, kind + " reusable is one fishing rod");
         ItemMeta meta = reusable.getItemMeta();
         check(meta.getEnchantLevel(Enchantment.UNBREAKING) == 3 && meta.hasEnchant(Enchantment.MENDING)
               && meta.hasEnchant(Enchantment.VANISHING_CURSE), kind + " reusable has Unbreaking III, Mending, Vanishing");

         if (!kind.hasSingleUse()) {
            check(kind.singleCommand() == null, kind + " has no single-use command");
            continue;
         }
         ItemStack box = registry.createSingleUseShulker(kind);
         ShulkerBox state = (ShulkerBox) ((BlockStateMeta) box.getItemMeta()).getBlockState();
         int genuine = 0;
         for (ItemStack content : state.getInventory().getContents()) {
            RodRegistry.Inspection in = registry.inspect(content);
            if (in.status() == RodRegistry.Status.GENUINE && in.kind() == kind && !in.reusable()) {
               genuine++;
            }
         }
         check(genuine == RodRegistry.SHULKER_SLOTS, kind + " shulker holds 27 genuine single-use rods (found " + genuine + ")");
         ItemStack single = registry.createRod(kind, false);
         check(!single.getItemMeta().hasEnchant(Enchantment.MENDING), kind + " single-use rod has no required enchantments");
      }

      ItemStack vanilla = new ItemStack(Material.FISHING_ROD);
      check(registry.inspect(vanilla).status() == RodRegistry.Status.NORMAL, "vanilla fishing rod is treated as normal");

      ItemStack renamed = new ItemStack(Material.FISHING_ROD);
      ItemMeta renamedMeta = renamed.getItemMeta();
      renamedMeta.customName(registry.createRod(RodKind.NUKE, true).getItemMeta().customName());
      renamedMeta.lore(registry.createRod(RodKind.NUKE, true).getItemMeta().lore());
      renamed.setItemMeta(renamedMeta);
      check(registry.inspect(renamed).status() == RodRegistry.Status.NORMAL, "renamed rod with copied lore has no powers");

      ItemStack forged = registry.createRod(RodKind.ORBITAL, false);
      ItemMeta forgedMeta = forged.getItemMeta();
      forgedMeta.getPersistentDataContainer().set(RodKeys.TYPE, PersistentDataType.STRING, "orbital_reusable");
      forged.setItemMeta(forgedMeta);
      check(registry.inspect(forged).status() == RodRegistry.Status.FORGED, "single-use rod edited to reusable is rejected");

      ItemStack noAuth = registry.createRod(RodKind.NUKE, true);
      ItemMeta noAuthMeta = noAuth.getItemMeta();
      noAuthMeta.getPersistentDataContainer().set(RodKeys.AUTH, PersistentDataType.STRING, "AAAA");
      noAuth.setItemMeta(noAuthMeta);
      check(registry.inspect(noAuth).status() == RodRegistry.Status.FORGED, "rod with a made-up signature is rejected");

      ItemStack stack = registry.createRod(RodKind.ORBITAL, false);
      stack.setAmount(3);
      ItemStack after = RodListener.removeOne(stack);
      check(after != null && after.getAmount() == 2, "consuming a stack of 3 single-use rods leaves 2");
      check(RodListener.removeOne(registry.createRod(RodKind.ORBITAL, false)) == null, "consuming the last rod empties the hand");
   }

   private void suggestions(SuggestionService service) {
      UUID player = UUID.randomUUID();
      UUID admin = UUID.randomUUID();
      service.submit(repo -> {
         Results.Created created = repo.create(player, "SelfTest", "Self-test suggestion", SuggestionCategory.SERVER,
               System.currentTimeMillis(), 0);
         boolean pendingHidden = repo.page(SuggestionStatus.APPROVED, SuggestionCategory.SERVER,
               net.srv.legendaryadditions.admin.suggestion.data.SortOrder.NEWEST, 0, 45, player).items().stream()
               .noneMatch(s -> s.id() == created.id());
         repo.approve(created.id(), SuggestionStatus.PENDING, SuggestionCategory.SERVER, admin, "SelfTest", System.currentTimeMillis());
         Results.VoteResult first = repo.vote(created.id(), player, System.currentTimeMillis());
         Results.VoteResult second = repo.vote(created.id(), player, System.currentTimeMillis());
         int votes = repo.find(created.id(), player).orElseThrow().votes();
         repo.delete(created.id(), SuggestionStatus.APPROVED);
         return pendingHidden && first == Results.VoteResult.VOTED && second == Results.VoteResult.ALREADY_VOTED && votes == 1;
      }).whenComplete((ok, error) -> check(error == null && Boolean.TRUE.equals(ok),
            "suggestion database: pending hidden, one vote per player" + (error != null ? " (" + error + ")" : "")));
   }

   private void dimension(AdminDimension dimension) {
      World world = dimension.world();
      check(world != null, "Admin dimension world is loaded");
      if (world == null) {
         this.finish();
         return;
      }
      World vanillaEnd = Bukkit.getWorld(org.bukkit.NamespacedKey.minecraft("the_end"));
      check(!world.getKey().equals(org.bukkit.NamespacedKey.minecraft("the_end")) && !world.equals(vanillaEnd),
            "Admin dimension (" + world.getKey() + ") is separate from the vanilla End (" + (vanillaEnd == null ? "disabled" : vanillaEnd.getKey()) + ")");
      Location centre = new Location(world, 0.5, 64, 0.5);
      world.getChunkAtAsync(centre).whenComplete((chunk, error) -> Bukkit.getRegionScheduler().execute(this.plugin, centre, () -> {
         check(error == null, "Admin dimension spawn chunk generates");
         Block top = world.getHighestBlockAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES);
         check(top.getType() == Material.END_STONE || top.getY() > world.getMinHeight(),
               "Admin dimension has End terrain at the centre (top block " + top.getType() + " at y=" + top.getY() + ")");
         Location safe = SafeLocations.find(top.getRelative(0, 1, 0), 3, 3, false);
         check(safe != null, "a safe central spawn exists in the Admin dimension");
         this.finish();
      }));
   }

   private void finish() {
      Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, t -> {
         if (this.failures.isEmpty()) {
            this.log.info("SELFTEST COMPLETE: all checks passed");
         } else {
            this.log.severe("SELFTEST COMPLETE: " + this.failures.size() + " failed: " + this.failures);
         }
      }, 20L);
   }
}
