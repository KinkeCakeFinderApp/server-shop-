package net.srv.eloranks.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.srv.eloranks.EloRanksPlugin;
import net.srv.eloranks.config.Messages;
import net.srv.eloranks.config.Settings;
import net.srv.eloranks.data.EloDatabase;
import net.srv.eloranks.data.PlayerRecord;
import net.srv.eloranks.gui.ClickGuard;
import net.srv.eloranks.rank.RankFormat;
import net.srv.eloranks.rank.Tier;
import net.srv.eloranks.reward.InventoryFit;
import net.srv.eloranks.reward.RewardDef;
import net.srv.eloranks.reward.RewardFactory;
import net.srv.eloranks.util.Durations;
import net.srv.eloranks.util.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Kit claims. A claim is:
 * <ol>
 *   <li>checked on the player's thread (unlocked, off cooldown, fits the inventory),</li>
 *   <li>written on the database thread in one transaction that re-checks unlock and cooldown,
 *       stores the claim time and queues the reward as a pending delivery,</li>
 *   <li>delivered on the player's thread; only after the items are in the inventory is the
 *       pending delivery deleted.</li>
 * </ol>
 * Only one claim per player runs at a time ({@link ClickGuard}), and the transaction refuses a
 * second claim inside the cooldown, so double clicks can never give a reward twice. A player who
 * logs out (or a server that stops) between 2 and 3 keeps the pending delivery and gets the reward
 * on their next join, so a reward is never lost.
 */
public final class ClaimService {
   private final EloRanksPlugin plugin;
   private final ClickGuard guard;

   public ClaimService(EloRanksPlugin plugin, ClickGuard guard) {
      this.plugin = plugin;
      this.guard = guard;
   }

   private record Claimed(EloDatabase.ClaimResult result, PlayerRecord record) {
   }

   /** Must run on the player's thread. {@code after} runs on the player's thread when the claim is done. */
   public void claim(Player player, Tier tier, Runnable after) {
      Settings settings = this.plugin.settings();
      Messages messages = this.plugin.messages();
      UUID id = player.getUniqueId();
      PlayerRecord record = this.plugin.data().cached(id);
      if (record == null) {
         messages.send(player, "data-loading");
         return;
      }
      RewardDef reward = settings.reward(tier.rewardId());
      Placeholders p = RankFormat.tier(settings, tier).with("elo", record.elo())
            .with("elo_needed", Math.max(0, tier.elo() - record.elo()))
            .with("cooldown", Durations.format(settings.claimCooldownMillis()));
      if (reward == null) {
         return;
      }
      if (!record.unlocked().contains(tier.id())) {
         messages.send(player, "reward-locked", p);
         return;
      }
      long now = System.currentTimeMillis();
      Long last = record.claims().get(tier.id());
      if (last != null && now - last < settings.claimCooldownMillis()) {
         messages.send(player, "reward-cooldown", p.with("remaining", Durations.format(last + settings.claimCooldownMillis() - now)));
         return;
      }
      int missing = InventoryFit.missingSlots(player.getInventory(), RewardFactory.build(reward));
      if (missing > 0 && settings.fullInventory() == Settings.FullInventory.REFUSE) {
         messages.send(player, "inventory-full", p.with("slots", missing));
         return;
      }
      if (!this.guard.begin(id)) {
         messages.send(player, "claim-busy");
         return;
      }
      this.plugin.data().submit(logic -> {
         EloDatabase.ClaimResult result = logic.db().tryClaim(id, tier.id(), reward.id(), now, settings.claimCooldownMillis());
         return new Claimed(result, logic.db().load(id).orElse(null));
      }).whenComplete((claimed, error) -> Tasks.onEntity(this.plugin, player, () -> {
         if (error != null || claimed == null) {
            this.guard.end(id);
            messages.send(player, "database-error");
            return;
         }
         this.plugin.data().cache(claimed.record());
         switch (claimed.result().status()) {
            case OK -> {
               // The guard stays held until the pending row is deleted.
               this.deliver(player, reward, claimed.result().pendingId(), p, after);
               return;
            }
            case COOLDOWN -> messages.send(player, "reward-cooldown", p.with("remaining",
                  Durations.format(claimed.result().lastClaim() + settings.claimCooldownMillis() - System.currentTimeMillis())));
            case LOCKED -> messages.send(player, "reward-locked", p);
            case UNKNOWN_PLAYER -> messages.send(player, "database-error");
         }
         this.guard.end(id);
         after.run();
      }, () -> this.guard.end(id)));
   }

   /** Player thread; the guard is held. Gives a freshly claimed reward. */
   private void deliver(Player player, RewardDef reward, long pendingId, Placeholders p, Runnable after) {
      Settings settings = this.plugin.settings();
      Messages messages = this.plugin.messages();
      UUID id = player.getUniqueId();
      List<ItemStack> items = RewardFactory.build(reward);
      int missing = InventoryFit.missingSlots(player.getInventory(), items);
      if (missing > 0 && settings.fullInventory() != Settings.FullInventory.DROP) {
         // The inventory filled up while the claim was saved: keep it as a pending delivery.
         messages.send(player, "reward-pending", p);
         this.guard.end(id);
         after.run();
         return;
      }
      this.give(player, items);
      messages.send(player, missing > 0 ? "reward-dropped" : "reward-claimed", p);
      this.plugin.getLogger().info(player.getName() + " claimed " + reward.id() + ".");
      this.finish(player, List.of(pendingId), after);
   }

   /**
    * Delivers queued rewards (claimed with a full inventory, or interrupted by a logout/restart).
    * Player thread.
    *
    * @param onJoin true = automatic attempt on join (only tells the player if something is left)
    */
   public void deliverPending(Player player, boolean onJoin) {
      UUID id = player.getUniqueId();
      if (!this.guard.begin(id)) {
         if (!onJoin) {
            this.plugin.messages().send(player, "claim-busy");
         }
         return;
      }
      this.plugin.data().submit(logic -> logic.db().pending(id)).whenComplete((pending, error) ->
            Tasks.onEntity(this.plugin, player, () -> {
               if (error != null) {
                  this.guard.end(id);
                  this.plugin.messages().send(player, "database-error");
                  return;
               }
               this.deliverPendingNow(player, pending, onJoin);
            }, () -> this.guard.end(id)));
   }

   private void deliverPendingNow(Player player, List<EloDatabase.Pending> pending, boolean onJoin) {
      Settings settings = this.plugin.settings();
      Messages messages = this.plugin.messages();
      List<Long> delivered = new ArrayList<>();
      for (EloDatabase.Pending entry : pending) {
         RewardDef reward = settings.reward(entry.rewardId());
         if (reward == null) {
            this.plugin.getLogger().warning("Pending reward " + entry.rewardId() + " of " + player.getName()
                  + " no longer exists in config.yml (rewards:); it stays pending until the reward is added back.");
            continue;
         }
         List<ItemStack> items = RewardFactory.build(reward);
         int missing = InventoryFit.missingSlots(player.getInventory(), items);
         if (missing > 0) {
            if (settings.fullInventory() != Settings.FullInventory.DROP || onJoin) {
               messages.send(player, onJoin ? "pending-on-join" : "pending-still-full",
                     new Placeholders().with("count", pending.size() - delivered.size())
                           .with("reward_name", reward.name()).with("slots", missing));
               break;
            }
         }
         this.give(player, items);
         delivered.add(entry.id());
      }
      if (!delivered.isEmpty()) {
         messages.send(player, "pending-delivered", Placeholders.of("count", delivered.size()));
      }
      this.finish(player, delivered, () -> { });
   }

   /** Adds items; anything that does not fit (only possible in drop mode) is dropped at the player's feet. */
   private void give(Player player, List<ItemStack> items) {
      player.getInventory().addItem(items.toArray(ItemStack[]::new)).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
   }

   /** Deletes delivered pending rows, refreshes the cache and releases the guard. */
   private void finish(Player player, List<Long> delivered, Runnable after) {
      UUID id = player.getUniqueId();
      this.plugin.data().submit(logic -> {
         for (long pendingId : delivered) {
            if (!logic.db().deletePending(pendingId)) {
               this.plugin.getLogger().warning("Pending reward " + pendingId + " of " + player.getName() + " was already delivered.");
            }
         }
         return logic.db().load(id).orElse(null);
      }).whenComplete((record, error) -> {
         if (error != null) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not mark rewards " + delivered + " of " + player.getName()
                  + " as delivered; they may be delivered again on the next join.", error);
         }
         if (player.isOnline()) {
            this.plugin.data().cache(record);
         }
         this.guard.end(id);
         Tasks.onEntity(this.plugin, player, after, null);
      });
   }
}
