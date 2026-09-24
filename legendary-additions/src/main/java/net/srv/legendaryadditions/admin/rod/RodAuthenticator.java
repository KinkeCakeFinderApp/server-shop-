package net.srv.legendaryadditions.admin.rod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Server-side HMAC-SHA256 signatures for rods. The secret key lives only in
 * plugins/LegendaryAdditions/rod-secret.key and is never written onto an item, so a rod
 * recreated by hand, by /give, or by copying visible text fails verification.
 */
public final class RodAuthenticator {
   private static final String ALGORITHM = "HmacSHA256";
   private static final SecureRandom RANDOM = new SecureRandom();

   private final byte[] secret;
   private final ThreadLocal<Mac> macs;

   private RodAuthenticator(byte[] secret) {
      this.secret = secret;
      this.macs = ThreadLocal.withInitial(this::newMac);
   }

   public static RodAuthenticator loadOrCreate(Path file) throws IOException {
      byte[] key;
      if (Files.exists(file)) {
         key = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).trim());
         if (key.length < 32) {
            throw new IOException("rod-secret.key is too short; delete it to generate a new one (existing rods will stop working)");
         }
      } else {
         key = new byte[64];
         RANDOM.nextBytes(key);
         Files.createDirectories(file.getParent());
         Files.writeString(file, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII,
               StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
         try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
         } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX file systems (Windows) - the file is still outside any item or config.
         }
      }
      return new RodAuthenticator(key);
   }

   public static String newNonce() {
      byte[] bytes = new byte[16];
      RANDOM.nextBytes(bytes);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   }

   public String sign(String typeId, int version, String nonce, String origin) {
      Mac mac = this.macs.get();
      byte[] digest = mac.doFinal(payload(typeId, version, nonce, origin));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
   }

   public boolean verify(String typeId, int version, String nonce, String origin, String signature) {
      if (typeId == null || nonce == null || origin == null || signature == null) {
         return false;
      }
      byte[] expected = this.macs.get().doFinal(payload(typeId, version, nonce, origin));
      byte[] actual;
      try {
         actual = Base64.getUrlDecoder().decode(signature);
      } catch (IllegalArgumentException ex) {
         return false;
      }
      return MessageDigest.isEqual(expected, actual);
   }

   private static byte[] payload(String typeId, int version, String nonce, String origin) {
      return ("legendaryadditions-rod|" + typeId + '|' + version + '|' + nonce + '|' + origin).getBytes(StandardCharsets.UTF_8);
   }

   private Mac newMac() {
      try {
         Mac mac = Mac.getInstance(ALGORITHM);
         mac.init(new SecretKeySpec(this.secret, ALGORITHM));
         return mac;
      } catch (GeneralSecurityException ex) {
         throw new IllegalStateException("HmacSHA256 unavailable", ex);
      }
   }
}
