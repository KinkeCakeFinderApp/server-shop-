package net.srv.legendaryadditions.admin.rod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RodAuthenticatorTest {
   @TempDir
   Path dir;

   @Test
   void signatureVerifiesAndSurvivesReload() throws Exception {
      Path key = this.dir.resolve("rod-secret.key");
      RodAuthenticator first = RodAuthenticator.loadOrCreate(key);
      String nonce = RodAuthenticator.newNonce();
      String sig = first.sign("orbital_single", 1, nonce);
      assertTrue(first.verify("orbital_single", 1, nonce, sig));
      // Same key file after a restart -> old rods still valid.
      assertTrue(RodAuthenticator.loadOrCreate(key).verify("orbital_single", 1, nonce, sig));
   }

   @Test
   void tamperingOrForeignKeysFail() throws Exception {
      RodAuthenticator auth = RodAuthenticator.loadOrCreate(this.dir.resolve("a.key"));
      RodAuthenticator other = RodAuthenticator.loadOrCreate(this.dir.resolve("b.key"));
      String nonce = RodAuthenticator.newNonce();
      String sig = auth.sign("nuke_single", 1, nonce);
      assertFalse(auth.verify("nuke_reusable", 1, nonce, sig), "type swapped");
      assertFalse(auth.verify("nuke_single", 2, nonce, sig), "version swapped");
      assertFalse(auth.verify("nuke_single", 1, RodAuthenticator.newNonce(), sig), "nonce swapped");
      assertFalse(auth.verify("nuke_single", 1, nonce, "not-base64!!"), "garbage signature");
      assertFalse(auth.verify("nuke_single", 1, nonce, null), "missing signature");
      assertFalse(other.verify("nuke_single", 1, nonce, sig), "signed with another server's key");
      assertNotEquals(sig, auth.sign("nuke_single", 1, RodAuthenticator.newNonce()));
   }

   @Test
   void everyTypeIdRoundTrips() {
      for (RodKind kind : RodKind.values()) {
         RodKind.Variant reusable = RodKind.fromTypeId(kind.typeId(true));
         assertEquals(kind, reusable.kind());
         assertTrue(reusable.reusable());
         if (kind.hasSingleUse()) {
            assertEquals(kind, RodKind.fromTypeId(kind.typeId(false)).kind());
         } else {
            assertNull(RodKind.fromTypeId(kind.typeId(false)), "teleport has no single-use variant");
         }
      }
      assertEquals("wolfrod_single", RodKind.WOLF_ROD.typeId(false));
      assertEquals("arrowrod_reusable", RodKind.ARROW_ROD.typeId(true));
      assertEquals("teleport_reusable", RodKind.TELEPORT.typeId(true));
      assertNull(RodKind.fromTypeId("orbital_single_fake"));
   }
}
