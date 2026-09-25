package net.srv.eloranks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.srv.eloranks.util.Durations;
import org.junit.jupiter.api.Test;

class DurationsTest {
   @Test
   void parses() {
      assertEquals(48L * 3_600_000, Durations.parse("48h"));
      assertEquals(2L * 86_400_000, Durations.parse("2d"));
      assertEquals(36L * 3_600_000, Durations.parse("1d12h"));
      assertEquals(90_000, Durations.parse("1m 30s"));
      assertEquals(15_000, Durations.parse("15"));
      assertEquals(0, Durations.parse("0s"));
      assertThrows(IllegalArgumentException.class, () -> Durations.parse("48 hours"));
      assertThrows(IllegalArgumentException.class, () -> Durations.parse("h"));
      assertThrows(IllegalArgumentException.class, () -> Durations.parse(""));
   }

   @Test
   void formats() {
      assertEquals("2d 0h 0m", Durations.format(48L * 3_600_000));
      assertEquals("3h 5m", Durations.format(3L * 3_600_000 + 5 * 60_000));
      assertEquals("12m 30s", Durations.format(750_000));
      assertEquals("1s", Durations.format(1));
      assertEquals("0s", Durations.format(-5));
   }
}
