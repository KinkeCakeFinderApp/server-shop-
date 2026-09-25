package net.srv.eloranks.listener;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.srv.eloranks.EloRanksPlugin;
import net.srv.eloranks.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Decides which deaths are legitimate PvP kills:
 * <ul>
 *   <li>a player killed by another player (melee, projectiles, anything the game credits);</li>
 *   <li>optionally, a player who dies of anything (fall, lava, void...) shortly after being hit
 *       by another player - credited to that player (pvp.credit-last-attacker);</li>
 *   <li>optionally, a player who disconnects shortly after being hit (pvp.combat-log).</li>
 * </ul>
 * Suicides, mob and environment deaths without a recent player hit never change ELO.
 */
public final class CombatListener implements Listener {
   private record Hit(UUID attacker, String attackerName, long at) {
   }

   private final EloRanksPlugin plugin;
   private final Map<UUID, Hit> lastHit = new ConcurrentHashMap<>();

   public CombatListener(EloRanksPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      if (!(event.getEntity() instanceof Player victim)) {
         return;
      }
      Entity causing = event.getDamageSource().getCausingEntity();
      if (causing instanceof Player attacker && !attacker.getUniqueId().equals(victim.getUniqueId())) {
         this.lastHit.put(victim.getUniqueId(), new Hit(attacker.getUniqueId(), attacker.getName(), System.currentTimeMillis()));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      Player victim = event.getPlayer();
      Hit hit = this.lastHit.remove(victim.getUniqueId());
      Settings settings = this.plugin.settings();
      if (!settings.pvp().countsIn(victim.getWorld().getName())) {
         return;
      }
      Player killer = victim.getKiller();
      if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
         this.count(killer.getUniqueId(), killer.getName(), victim);
         return;
      }
      long window = settings.pvp().creditLastAttackerMillis();
      if (hit != null && window > 0 && System.currentTimeMillis() - hit.at() <= window) {
         this.count(hit.attacker(), hit.attackerName(), victim);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      Player victim = event.getPlayer();
      Hit hit = this.lastHit.remove(victim.getUniqueId());
      this.lastHit.values().removeIf(h -> h.attacker().equals(victim.getUniqueId()));
      long window = this.plugin.settings().pvp().combatLogMillis();
      if (hit == null || window <= 0 || Bukkit.isStopping() || victim.isDead()
            || event.getReason() != PlayerQuitEvent.QuitReason.DISCONNECTED
            || System.currentTimeMillis() - hit.at() > window
            || !this.plugin.settings().pvp().countsIn(victim.getWorld().getName())) {
         return;
      }
      this.plugin.getLogger().info(victim.getName() + " disconnected in combat; counted as killed by " + hit.attackerName() + ".");
      this.count(hit.attacker(), hit.attackerName(), victim);
   }

   private void count(UUID killerId, String killerName, Player victim) {
      Player killer = Bukkit.getPlayer(killerId);
      boolean sameIp = killer != null && sameAddress(killer.getAddress(), victim.getAddress());
      this.plugin.ranks().kill(killerId, killerName, victim.getUniqueId(), victim.getName(), sameIp);
   }

   private static boolean sameAddress(InetSocketAddress a, InetSocketAddress b) {
      return a != null && b != null && a.getAddress() != null && b.getAddress() != null
            && a.getAddress().equals(b.getAddress());
   }
}
