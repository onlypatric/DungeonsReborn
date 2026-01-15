package dev.patric.dungeonsreborn.locale;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public final class Locales {
  private static volatile LocaleService service;

  private Locales() {
  }

  public static void install(LocaleService localeService) {
    service = Objects.requireNonNull(localeService, "localeService");
  }

  public static LocaleService service() {
    return service;
  }

  public static Component component(Player player, String key) {
    return component(player, key, Map.of());
  }

  public static Component component(Player player, String key, Map<String, String> placeholders) {
    LocaleService localeService = service;
    if (localeService == null) {
      return Component.text(key == null ? "" : key);
    }
    return localeService.component(player, key, placeholders);
  }

  public static String text(Player player, String key) {
    return text(player, key, Map.of());
  }

  public static String text(Player player, String key, Map<String, String> placeholders) {
    LocaleService localeService = service;
    if (localeService == null) {
      return key == null ? "" : key;
    }
    return localeService.text(player, key, placeholders);
  }

  public static Map<String, String> placeholders(Object... pairs) {
    Map<String, String> out = new HashMap<>();
    if (pairs == null) {
      return out;
    }
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      Object key = pairs[i];
      Object value = pairs[i + 1];
      if (key == null) {
        continue;
      }
      out.put(key.toString(), value == null ? "" : value.toString());
    }
    return out;
  }
}
