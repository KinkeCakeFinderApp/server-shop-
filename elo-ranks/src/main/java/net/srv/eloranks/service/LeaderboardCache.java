package net.srv.eloranks.service;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.srv.eloranks.EloRanksPlugin;
import net.srv.eloranks.data.EloDatabase;
import org.bukkit.Bukkit;

/**
 * The leaderboard, rebuilt from the database every leaderboard.refresh-interval (and after admin
 * changes) so opening the GUI never queries or sorts anything.
 */
public final class LeaderboardCache {
   private final EloRanksPlugin plugin;
   private final AtomicBoolean refreshing = new AtomicBoolean();
   private volatile List<EloDatabase.LeaderboardRow> rows = List.of();
   private volatile long updatedAt;
   private ScheduledTask task;

   public LeaderboardCache(EloRanksPlugin plugin) {
      this.plugin = plugin;
   }

   public synchronized void start() {
      this.stop();
      long every = this.plugin.settings().leaderboardRefreshMillis();
      this.task = Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, t -> this.refresh(), 0, every, TimeUnit.MILLISECONDS);
   }

   public synchronized void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   public void refresh() {
      if (!this.refreshing.compareAndSet(false, true)) {
         return;
      }
      int limit = this.plugin.settings().leaderboardMaxEntries();
      this.plugin.data().submit(logic -> logic.db().top(limit)).whenComplete((top, error) -> {
         if (top != null) {
            this.rows = top;
            this.updatedAt = System.currentTimeMillis();
         }
         this.refreshing.set(false);
      });
   }

   public List<EloDatabase.LeaderboardRow> rows() {
      return this.rows;
   }

   public long updatedAt() {
      return this.updatedAt;
   }
}
