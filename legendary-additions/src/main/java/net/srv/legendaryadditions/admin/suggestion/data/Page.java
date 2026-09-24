package net.srv.legendaryadditions.admin.suggestion.data;

import java.util.List;

/** One page of results plus the total so the GUI knows whether a next page exists. */
public record Page<T>(List<T> items, int page, int pageSize, int total) {
   public boolean hasPrevious() {
      return this.page > 0;
   }

   public boolean hasNext() {
      return (long) (this.page + 1) * this.pageSize < this.total;
   }

   public int pageCount() {
      return Math.max(1, (this.total + this.pageSize - 1) / this.pageSize);
   }
}
