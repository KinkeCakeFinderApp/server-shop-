package net.srv.legendaryadditions.admin.effect;

import java.util.concurrent.ThreadLocalRandom;
import net.srv.legendaryadditions.admin.AdminSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Wolf Rod: summons a pack of armoured wolves with Strength II, Regeneration, Fire Resistance and
 * Speed II, tamed to the caster, at the crosshair target.
 */
public final class WolfPackEffect {
   private WolfPackEffect() {
   }

   /** Runs on the caster's region; the target was found inside that same region. */
   public static void launch(Plugin plugin, Player owner, Location target, AdminSettings.WolfRod settings) {
      World world = target.getWorld();
      Location spawn = target.clone().add(0, 1, 0);
      ThreadLocalRandom random = ThreadLocalRandom.current();
      long lifetimeTicks = settings.lifetimeSeconds() * 20L;

      for (int i = 0; i < settings.count(); i++) {
         Location at = spawn.clone().add(random.nextDouble(-1.5, 1.5), 0, random.nextDouble(-1.5, 1.5));
         world.spawn(at, Wolf.class, wolf -> {
            wolf.setTamed(true);
            wolf.setOwner(owner);
            if (settings.wolfArmor()) {
               wolf.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(Material.WOLF_ARMOR));
            }
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, settings.buffTicks(), settings.strengthAmplifier()));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, settings.buffTicks(), 0));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, settings.buffTicks(), settings.speedAmplifier()));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, settings.fireResistanceTicks(), 0));
            ExplosionGuard.tag(wolf, owner.getUniqueId(), false, false);
            if (lifetimeTicks > 0) {
               // Temporary wolves are not saved with the chunk and are removed by their own scheduler.
               wolf.setPersistent(false);
               wolf.getScheduler().runDelayed(plugin, t -> wolf.remove(), null, lifetimeTicks);
            }
         });
      }
      Fx.particle(spawn, Particle.POOF, 40, 1.5, 0.05);
      Fx.sound(spawn, Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.MASTER, 2F, 1.2F);
   }
}
