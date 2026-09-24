package net.srv.legendaryadditions.admin.rod;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Creates genuine rods and recognises them again. */
public final class RodRegistry {
   public static final int SHULKER_SLOTS = 27;

   private final RodAuthenticator authenticator;

   public RodRegistry(RodAuthenticator authenticator) {
      this.authenticator = authenticator;
   }

   public enum Status {
      /** No rod data at all - vanilla fishing rod behaviour applies. */
      NORMAL,
      /** Carries rod data but fails the server-side signature check. */
      FORGED,
      GENUINE
   }

   public record Inspection(Status status, RodKind kind, boolean reusable, String nonce) {
      static final Inspection NORMAL = new Inspection(Status.NORMAL, null, false, null);
      static final Inspection FORGED = new Inspection(Status.FORGED, null, false, null);
   }

   public ItemStack createRod(RodKind kind, boolean reusable) {
      if (!reusable && !kind.hasSingleUse()) {
         throw new IllegalArgumentException(kind + " has no single-use variant");
      }
      ItemStack rod = new ItemStack(Material.FISHING_ROD);
      ItemMeta meta = rod.getItemMeta();
      meta.customName(Component.text(kind.displayName() + (reusable ? "" : " (Single-Use)"), kind.color())
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
      List<Component> lore = new ArrayList<>();
      lore.add(line(kind.description() + ".", NamedTextColor.GRAY));
      lore.add(line(reusable ? "Reusable - no cooldown." : "Single-use - consumed when it fires.", NamedTextColor.DARK_GRAY));
      meta.lore(lore);
      if (reusable) {
         meta.addEnchant(Enchantment.UNBREAKING, 3, true);
         meta.addEnchant(Enchantment.MENDING, 1, true);
         meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
      }

      String typeId = kind.typeId(reusable);
      String nonce = RodAuthenticator.newNonce();
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(RodKeys.TYPE, PersistentDataType.STRING, typeId);
      pdc.set(RodKeys.VERSION, PersistentDataType.INTEGER, RodKeys.CURRENT_VERSION);
      pdc.set(RodKeys.NONCE, PersistentDataType.STRING, nonce);
      pdc.set(RodKeys.AUTH, PersistentDataType.STRING, this.authenticator.sign(typeId, RodKeys.CURRENT_VERSION, nonce));
      rod.setItemMeta(meta);
      return rod;
   }

   /** A shulker box with all 27 slots holding individually signed single-use rods. */
   public ItemStack createSingleUseShulker(RodKind kind) {
      ItemStack box = new ItemStack(kind.shulkerMaterial());
      BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
      ShulkerBox state = (ShulkerBox) meta.getBlockState();
      for (int slot = 0; slot < SHULKER_SLOTS; slot++) {
         state.getInventory().setItem(slot, this.createRod(kind, false));
      }
      meta.setBlockState(state);
      meta.customName(Component.text(kind.displayName() + " Supply (" + SHULKER_SLOTS + ")", kind.color())
            .decoration(TextDecoration.ITALIC, false));
      box.setItemMeta(meta);
      return box;
   }

   public Inspection inspect(ItemStack item) {
      if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
         return Inspection.NORMAL;
      }
      PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
      String typeId = pdc.get(RodKeys.TYPE, PersistentDataType.STRING);
      if (typeId == null) {
         return Inspection.NORMAL;
      }
      Integer version = pdc.get(RodKeys.VERSION, PersistentDataType.INTEGER);
      String nonce = pdc.get(RodKeys.NONCE, PersistentDataType.STRING);
      String auth = pdc.get(RodKeys.AUTH, PersistentDataType.STRING);
      RodKind.Variant variant = RodKind.fromTypeId(typeId);
      if (variant == null || version == null || !this.authenticator.verify(typeId, version, nonce, auth)) {
         return Inspection.FORGED;
      }
      return new Inspection(Status.GENUINE, variant.kind(), variant.reusable(), nonce);
   }

   private static Component line(String text, NamedTextColor color) {
      return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
   }
}
