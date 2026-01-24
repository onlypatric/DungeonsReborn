package dev.patric.dungeonsreborn.shops;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

public final class ShopTimeWindowSpec {
  private final Set<DayOfWeek> days;
  private final LocalTime start;
  private final LocalTime end;

  public ShopTimeWindowSpec(Set<DayOfWeek> days, LocalTime start, LocalTime end) {
    this.days = days == null ? Set.of() : Set.copyOf(days);
    this.start = Objects.requireNonNull(start, "start");
    this.end = Objects.requireNonNull(end, "end");
  }

  public Set<DayOfWeek> days() {
    return days;
  }

  public LocalTime start() {
    return start;
  }

  public LocalTime end() {
    return end;
  }

  public boolean matches(Instant instant, ZoneId zoneId) {
    ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
    ZonedDateTime dateTime = ZonedDateTime.ofInstant(instant, zone);
    if (!days.isEmpty() && !days.contains(dateTime.getDayOfWeek())) {
      return false;
    }
    LocalTime time = dateTime.toLocalTime();
    if (start.equals(end)) {
      return true;
    }
    if (start.isBefore(end)) {
      return !time.isBefore(start) && time.isBefore(end);
    }
    return !time.isBefore(start) || time.isBefore(end);
  }
}
