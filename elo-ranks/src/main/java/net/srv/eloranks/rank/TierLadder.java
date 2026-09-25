package net.srv.eloranks.rank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The tiers ordered from the lowest ELO requirement (first unlock) to the highest. */
public final class TierLadder {
   private final List<Tier> ascending;

   public TierLadder(Collection<Tier> tiers) {
      List<Tier> sorted = new ArrayList<>(tiers);
      sorted.sort(Comparator.comparingInt(Tier::elo));
      this.ascending = List.copyOf(sorted);
   }

   public List<Tier> ascending() {
      return this.ascending;
   }

   /** The highest tier whose requirement {@code elo} reaches, or empty when unranked. */
   public Optional<Tier> tierFor(int elo) {
      Tier found = null;
      for (Tier tier : this.ascending) {
         if (elo >= tier.elo()) {
            found = tier;
         }
      }
      return Optional.ofNullable(found);
   }

   /** The next tier above {@code elo}, or empty at the top tier. */
   public Optional<Tier> nextAfter(int elo) {
      for (Tier tier : this.ascending) {
         if (elo < tier.elo()) {
            return Optional.of(tier);
         }
      }
      return Optional.empty();
   }

   /** Every tier {@code elo} reaches. */
   public List<Tier> reachedBy(int elo) {
      return this.ascending.stream().filter(t -> elo >= t.elo()).toList();
   }

   public Optional<Tier> byId(String id) {
      return this.ascending.stream().filter(t -> t.id().equals(id)).findFirst();
   }

   /** Accepts a tier number (1), "t1", "tier1", "tier-1" or a tier id. */
   public Optional<Tier> parse(String input) {
      String value = input.toLowerCase(Locale.ROOT).replace("_", "-");
      Optional<Tier> byId = this.byId(value);
      if (byId.isPresent()) {
         return byId;
      }
      String digits = value.replaceFirst("^(tier-?|t)", "");
      try {
         int number = Integer.parseInt(digits);
         return this.ascending.stream().filter(t -> t.number() == number).findFirst();
      } catch (NumberFormatException ex) {
         return Optional.empty();
      }
   }

   /** The position of {@code tier} counted from the bottom (0 = first unlock). -1 = unranked. */
   public int rankIndex(Optional<Tier> tier) {
      return tier.map(this.ascending::indexOf).orElse(-1);
   }

   /** Progress from the current tier's requirement (or 0) toward the next tier, 0..1. */
   public double progress(int elo) {
      Optional<Tier> next = this.nextAfter(elo);
      if (next.isEmpty()) {
         return 1.0;
      }
      int from = this.tierFor(elo).map(Tier::elo).orElse(0);
      int span = next.get().elo() - from;
      return span <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (elo - from) / (double) span));
   }
}
