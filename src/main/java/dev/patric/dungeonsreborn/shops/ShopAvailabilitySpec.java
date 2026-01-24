package dev.patric.dungeonsreborn.shops;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public final class ShopAvailabilitySpec {
  private final ZoneId zoneId;
  private final List<ShopTimeWindowSpec> windows;

  public ShopAvailabilitySpec(ZoneId zoneId, List<ShopTimeWindowSpec> windows) {
    this.zoneId = zoneId;
    this.windows = windows == null ? List.of() : List.copyOf(windows);
  }

  public ZoneId zoneId() {
    return zoneId;
  }

  public List<ShopTimeWindowSpec> windows() {
    return windows;
  }

  public boolean isAvailable(Instant instant) {
    if (windows.isEmpty()) {
      return true;
    }
    ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
    Instant now = Objects.requireNonNull(instant, "instant");
    for (ShopTimeWindowSpec window : windows) {
      if (window != null && window.matches(now, zone)) {
        return true;
      }
    }
    return false;
  }

  public boolean isAvailableNow() {
    return isAvailable(Instant.now());
  }
}
