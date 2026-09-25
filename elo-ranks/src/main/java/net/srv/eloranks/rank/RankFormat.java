package net.srv.eloranks.rank;

import java.util.Optional;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.util.Placeholders;

/** Tier names, colours and the shared {placeholders} for a player's ranking. */
public final class RankFormat {
   private RankFormat() {
   }

   /** The tier shown for a player, per displayed-tier. */
   public static Optional<Tier> displayed(Settings settings, int elo, java.util.Set<String> unlocked) {
      TierLadder ladder = settings.ladder();
      Optional<Tier> current = ladder.tierFor(elo);
      if (settings.displayedTier() == Settings.DisplayedTier.CURRENT) {
         return current;
      }
      Optional<Tier> highest = ladder.ascending().stream().filter(t -> unlocked.contains(t.id())).reduce((a, b) -> b);
      return ladder.rankIndex(highest) > ladder.rankIndex(current) ? highest : current;
   }

   public static Optional<Tier> displayed(Settings settings, PlayerRecord record) {
      return displayed(settings, record.elo(), record.unlocked());
   }

   public static String name(Settings settings, Optional<Tier> tier) {
      return tier.map(Tier::name).orElse(settings.unrankedName());
   }

   public static String color(Settings settings, Optional<Tier> tier) {
      return tier.map(Tier::color).orElse(settings.unrankedColor());
   }

   /** Tiers whose reward the player could claim right now. */
   public static int rewardsAvailable(Settings settings, PlayerRecord record, long now) {
      int count = 0;
      for (Tier tier : settings.ladder().ascending()) {
         Long last = record.claims().get(tier.id());
         if (record.unlocked().contains(tier.id()) && (last == null || now - last >= settings.claimCooldownMillis())) {
            count++;
         }
      }
      return count;
   }

   public static String progressBar(Settings settings, double progress) {
      Settings.ProgressBar bar = settings.gui().bar();
      int filled = (int) Math.round(progress * bar.length());
      return bar.filled().repeat(filled) + bar.empty().repeat(bar.length() - filled);
   }

   /** {player} {elo} {tier_name} {tier_color} {next_*} {elo_needed} {progress_*} {rewards_available} and stats. */
   public static Placeholders of(Settings settings, PlayerRecord record, long now) {
      Optional<Tier> tier = displayed(settings, record);
      Optional<Tier> next = settings.ladder().nextAfter(record.elo());
      double progress = settings.ladder().progress(record.elo());
      String max = settings.raw().getString("messages.max-tier", "MAX TIER");
      return new Placeholders()
            .with("player", record.name())
            .with("elo", record.elo())
            .with("tier_name", name(settings, tier))
            .with("tier_color", color(settings, tier))
            .with("tier_number", tier.map(Tier::number).map(String::valueOf).orElse("-"))
            .with("next_tier_name", next.map(Tier::name).orElse(max))
            .with("next_tier_color", next.map(Tier::color).orElse("&6"))
            .with("next_tier_elo", next.map(t -> String.valueOf(t.elo())).orElse("-"))
            .with("elo_needed", next.map(t -> String.valueOf(Math.max(0, t.elo() - record.elo()))).orElse("0"))
            .with("progress_bar", progressBar(settings, progress))
            .with("progress_percent", (int) Math.floor(progress * 100))
            .with("rewards_available", rewardsAvailable(settings, record, now))
            .with("kills", record.kills())
            .with("deaths", record.deaths())
            .with("elo_gained", record.eloGained())
            .with("elo_lost", record.eloLost());
   }

   public static Placeholders tier(Settings settings, Tier tier) {
      var reward = settings.reward(tier.rewardId());
      return new Placeholders()
            .with("tier_name", tier.name())
            .with("tier_color", tier.color())
            .with("tier_number", tier.number())
            .with("required_elo", tier.elo())
            .with("reward_name", reward == null ? tier.rewardId() : reward.name());
   }
}
