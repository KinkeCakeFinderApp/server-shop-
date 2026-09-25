package net.srv.legendaryadditions.admin.effect;

import java.util.EnumMap;
import java.util.Map;
import net.srv.legendaryadditions.admin.rod.RodKind;
import org.bukkit.plugin.Plugin;

/** Maps every rod kind to its effect. Every kind must be present; this is checked at startup. */
public final class RodEffects {
   private final Map<RodKind, RodEffect> effects = new EnumMap<>(RodKind.class);

   public RodEffects(Plugin plugin) {
      this.effects.put(RodKind.ORBITAL, (caster, target, s) -> {
         if (s.stab().tnt()) {
            StabColumnEffect.launch(plugin, target.center(), s.stab(), caster.getUniqueId());
         } else {
            StrikeEffect.launch(plugin, target.center(), s.orbital(), StrikeEffect.Style.ORBITAL, caster.getUniqueId());
         }
         return true;
      });
      this.effects.put(RodKind.NUKE, (caster, target, s) -> {
         if (s.nukeRings().rings()) {
            NukeRingsEffect.launch(plugin, target.center(), s.nukeRings(), caster.getUniqueId());
         } else {
            StrikeEffect.launch(plugin, target.center(), s.nuke(), StrikeEffect.Style.NUKE, caster.getUniqueId());
         }
         return true;
      });
      this.effects.put(RodKind.TELEPORT, (caster, target, s) -> TeleportEffect.teleport(plugin, caster, target, s.teleport()));
      this.effects.put(RodKind.LAW_NUKE, (caster, target, s) -> {
         LawNukeEffect.launch(plugin, target.center(), s.lawNuke(), caster.getUniqueId());
         return true;
      });
      this.effects.put(RodKind.WITHER_NUKE, (caster, target, s) -> {
         WitherNukeEffect.launch(plugin, target.center(), s.witherNuke(), caster.getUniqueId());
         return true;
      });
      this.effects.put(RodKind.WOLF_ROD, (caster, target, s) -> {
         WolfPackEffect.launch(plugin, caster, target.center(), s.wolfRod());
         return true;
      });
      this.effects.put(RodKind.ARROW_ROD, (caster, target, s) -> {
         ArrowRainEffect.launch(plugin, caster, target.center(), s.arrowRod());
         return true;
      });
      for (RodKind kind : RodKind.values()) {
         if (!this.effects.containsKey(kind)) {
            throw new IllegalStateException("No effect registered for " + kind);
         }
      }
   }

   public RodEffect get(RodKind kind) {
      return this.effects.get(kind);
   }
}
