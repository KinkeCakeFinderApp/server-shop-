package net.srv.eloranks.luckperms;

import java.util.Optional;
import java.util.UUID;
import net.srv.eloranks.rank.Tier;

/** Shows a player's tier next to their name. */
public interface RankDisplay {
   /** @param tier the displayed tier, empty = unranked */
   void apply(UUID player, Optional<Tier> tier);

   /** Does nothing (LuckPerms missing or luckperms.mode: none). */
   RankDisplay NONE = (player, tier) -> { };
}
