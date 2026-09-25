package net.srv.eloranks.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** A small builder for {placeholder} values. */
public final class Placeholders {
   private final Map<String, String> values = new LinkedHashMap<>();

   public static Placeholders of(String key, Object value) {
      return new Placeholders().with(key, value);
   }

   public Placeholders with(String key, Object value) {
      this.values.put(key, String.valueOf(value));
      return this;
   }

   public Placeholders with(Placeholders other) {
      this.values.putAll(other.values);
      return this;
   }

   public Map<String, String> map() {
      return this.values;
   }
}
