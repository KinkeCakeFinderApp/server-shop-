package net.srv.legendaryadditions.admin.suggestion.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SuggestionTextTest {
   @Test
   void cleansAndValidates() {
      SuggestionText.Checked ok = SuggestionText.check("  &cAdd   a\tnew  \u0007 rod  ", 5, 20);
      assertTrue(ok.ok());
      assertEquals("Add a new rod", ok.text());
      assertFalse(SuggestionText.check("hi", 5, 20).ok());
      assertFalse(SuggestionText.check("x".repeat(21), 5, 20).ok());
      assertFalse(SuggestionText.check(null, 5, 20).ok());
   }

   @Test
   void wrapsLongText() {
      List<String> lines = SuggestionText.wrap("aaaa bbbb cccc " + "d".repeat(25), 10);
      assertEquals(List.of("aaaa bbbb", "cccc", "dddddddddd", "dddddddddd", "ddddd"), lines);
      for (String line : lines) {
         assertTrue(line.length() <= 10);
      }
   }
}
