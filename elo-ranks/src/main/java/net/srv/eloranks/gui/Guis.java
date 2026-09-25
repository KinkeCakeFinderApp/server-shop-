package net.srv.eloranks.gui;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.srv.eloranks.EloRanksPlugin;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.data.EloDatabase;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.rank.RankFormat;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.reward.RewardDef;
import net.srv.eloranks.util.Durations;
import net.srv.eloranks.util.Placeholders;
import net.srv.eloranks.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The main ranking GUI (/rank), the kits GUI (/kits) and the leaderboard (/leaderboard). Every
 * screen is built from cached data only; nothing here touches the database. Player thread only.
 */
public final class Guis {
   private final EloRanksPlugin plugin;

   public Guis(EloRanksPlugin plugin) {
      this.plugin = plugin;
   }

   private PlayerRecord record(Player player) {
      PlayerRecord record = this.plugin.data().cached(player.getUniqueId());
      if (record == null) {
         this.plugin.messages().send(player, "data-loading");
         this.plugin.ranks().join(player);
      }
      return record;
   }

   // ------------------------------------------------------------------ main

   public void openMain(Player player) {
      PlayerRecord record = this.record(player);
      if (record == null) {
         return;
      }
      Settings settings = this.plugin.settings();
      Settings.Gui gui = settings.gui();
      Placeholders p = RankFormat.of(settings, record, System.currentTimeMillis());
      Menu menu = new Menu(player.getUniqueId(), gui.mainRows(), Text.chat(gui.mainTitle(), p.map()));

      boolean maxTier = settings.ladder().nextAfter(record.elo()).isEmpty();
      Settings.Button profile = gui.profile();
      ItemStack head = this.item(profile, p, maxTier ? gui.profileMaxLore() : profile.lore(), Map.of());
      if (head.getItemMeta() instanceof SkullMeta skull) {
         skull.setPlayerProfile(player.getPlayerProfile());
         head.setItemMeta(skull);
      }
      menu.set(profile.slot(), head);
      menu.set(gui.kits().slot(), this.item(gui.kits(), p), (who, click) -> this.openKits(who));
      menu.set(gui.leaderboard().slot(), this.item(gui.leaderboard(), p), (who, click) -> this.openLeaderboard(who, 0));
      menu.set(gui.close().slot(), this.item(gui.close(), p), (who, click) -> who.closeInventory());
      this.fill(menu, gui);
      player.openInventory(menu.getInventory());
   }

   // ------------------------------------------------------------------ kits

   public void openKits(Player player) {
      PlayerRecord record = this.record(player);
      if (record == null) {
         return;
      }
      Settings settings = this.plugin.settings();
      Settings.Gui gui = settings.gui();
      long now = System.currentTimeMillis();
      Placeholders base = RankFormat.of(settings, record, now);
      Menu menu = new Menu(player.getUniqueId(), gui.kitsRows(), Text.chat(gui.kitsTitle(), base.map()));

      for (Tier tier : settings.ladder().ascending()) {
         RewardDef reward = settings.reward(tier.rewardId());
         Placeholders p = new Placeholders().with(base).with(RankFormat.tier(settings, tier))
               .with("elo_needed", Math.max(0, tier.elo() - record.elo()));
         Map<String, List<String>> multi = Map.of("reward_description", reward.description());
         Long last = record.claims().get(tier.id());
         ItemStack item;
         if (!record.unlocked().contains(tier.id())) {
            item = this.item(gui.locked(), p, gui.locked().lore(), multi, reward.icon());
         } else if (last != null && now - last < settings.claimCooldownMillis()) {
            long next = last + settings.claimCooldownMillis();
            p.with("last_claim", gui.dateFormat().format(java.time.Instant.ofEpochMilli(last)))
                  .with("remaining", Durations.format(next - now))
                  .with("next_claim", gui.dateFormat().format(java.time.Instant.ofEpochMilli(next)));
            item = this.item(gui.cooldown(), p, gui.cooldown().lore(), multi, reward.icon());
         } else {
            item = this.item(gui.available(), p, gui.available().lore(), multi, reward.icon());
         }
         menu.set(tier.slot(), item, (who, click) -> this.plugin.claims().claim(who, tier, () -> {
            if (who.getOpenInventory().getTopInventory().getHolder(false) == menu) {
               this.openKits(who);
            }
         }));
      }
      menu.set(gui.kitsBack().slot(), this.item(gui.kitsBack(), base), (who, click) -> this.openMain(who));
      if (record.pending() > 0) {
         Placeholders p = new Placeholders().with(base).with("count", record.pending());
         menu.set(gui.pending().slot(), this.item(gui.pending(), p), (who, click) -> {
            who.closeInventory();
            this.plugin.claims().deliverPending(who, false);
         });
      }
      this.fill(menu, gui);
      player.openInventory(menu.getInventory());
   }

   // ------------------------------------------------------------------ leaderboard

   public void openLeaderboard(Player player, int requestedPage) {
      Settings settings = this.plugin.settings();
      Settings.Gui gui = settings.gui();
      List<EloDatabase.LeaderboardRow> rows = this.plugin.leaderboard().rows();
      List<Integer> slots = gui.entrySlots();
      int pages = Math.max(1, (rows.size() + slots.size() - 1) / slots.size());
      int page = Math.max(0, Math.min(pages - 1, requestedPage));
      Placeholders pageInfo = new Placeholders().with("page", page + 1).with("pages", pages).with("player", player.getName());
      Menu menu = new Menu(player.getUniqueId(), gui.boardRows(), Text.chat(gui.boardTitle(), pageInfo.map()));

      if (rows.isEmpty()) {
         menu.set(gui.empty().slot(), this.item(gui.empty(), pageInfo));
      }
      for (int i = 0; i < slots.size(); i++) {
         int index = page * slots.size() + i;
         if (index >= rows.size()) {
            break;
         }
         EloDatabase.LeaderboardRow row = rows.get(index);
         Optional<Tier> tier = RankFormat.displayed(settings, row.elo(), row.unlocked());
         Placeholders p = new Placeholders().with(pageInfo).with("position", index + 1).with("player", row.name())
               .with("elo", row.elo()).with("tier_name", RankFormat.name(settings, tier))
               .with("tier_color", RankFormat.color(settings, tier));
         Settings.Button entry = gui.entry();
         if (gui.playerHeads()) {
            entry = new Settings.Button(-1, Material.PLAYER_HEAD, entry.name(), entry.lore(), entry.glow());
         }
         ItemStack item = this.item(entry, p);
         if (gui.playerHeads() && item.getItemMeta() instanceof SkullMeta skull) {
            skull.setPlayerProfile(Bukkit.createProfile(row.uuid(), row.name()));
            item.setItemMeta(skull);
         }
         menu.set(slots.get(i), item);
      }
      if (page > 0) {
         menu.set(gui.previous().slot(), this.item(gui.previous(), pageInfo), (who, click) -> this.openLeaderboard(who, page - 1));
      }
      if (page < pages - 1) {
         menu.set(gui.next().slot(), this.item(gui.next(), pageInfo), (who, click) -> this.openLeaderboard(who, page + 1));
      }
      menu.set(gui.boardBack().slot(), this.item(gui.boardBack(), pageInfo), (who, click) -> this.openMain(who));
      this.fill(menu, gui);
      player.openInventory(menu.getInventory());
   }

   // ------------------------------------------------------------------ items

   private void fill(Menu menu, Settings.Gui gui) {
      if (gui.filler()) {
         ItemStack filler = ItemStack.of(gui.fillerMaterial());
         ItemMeta meta = filler.getItemMeta();
         meta.setHideTooltip(true);
         filler.setItemMeta(meta);
         menu.fill(filler);
      }
   }

   private ItemStack item(Settings.Button button, Placeholders p) {
      return this.item(button, p, button.lore(), Map.of());
   }

   private ItemStack item(Settings.Button button, Placeholders p, List<String> lore, Map<String, List<String>> multi) {
      return this.item(button, p, lore, multi, button.material());
   }

   /** @param fallback used when the button has no material of its own (kit "available" = the reward icon) */
   private ItemStack item(Settings.Button button, Placeholders p, List<String> lore, Map<String, List<String>> multi,
                          Material fallback) {
      Material material = button.material() != null ? button.material() : fallback;
      ItemStack item = ItemStack.of(material == null ? Material.PAPER : material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Text.item(button.name(), p.map()));
      meta.lore(Text.lore(lore, p.map(), multi));
      if (button.glow()) {
         meta.setEnchantmentGlintOverride(true);
      }
      meta.addItemFlags(ItemFlag.values());
      item.setItemMeta(meta);
      return item;
   }
}
