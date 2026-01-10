package dev.patric.dungeonsreborn.gui;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Lightweight localization helper backed by {@link ResourceBundle}.
 * <p>
 * Values are MiniMessage strings.
 * <p>
 * Add translations by creating resource bundles at:
 * <ul>
 * <li>{@code src/main/resources/dev/patric/dungeonsreborn/gui/lang/gui.properties}</li>
 * <li>{@code .../gui_en_us.properties}, {@code .../gui_fr_fr.properties}, ...</li>
 * </ul>
 */
public final class GuiI18n {
  private static final String BASE_NAME = "dev.patric.dungeonsreborn.gui.lang.gui";
  private static final ConcurrentHashMap<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
  private static volatile Locale defaultLocale = Locale.US;

  private GuiI18n() {
  }

  public static void setDefaultLocale(Locale locale) {
    defaultLocale = Objects.requireNonNull(locale, "locale");
  }

  public static Locale defaultLocale() {
    return defaultLocale;
  }

  public static Locale locale(Player player) {
    Objects.requireNonNull(player, "player");
    Locale locale = player.locale();
    if (locale == null) {
      return defaultLocale;
    }
    return locale;
  }

  public static Component tr(Player player, String key, TagResolver... resolvers) {
    return tr(locale(player), key, resolvers);
  }

  public static Component tr(String key, TagResolver... resolvers) {
    return tr(defaultLocale, key, resolvers);
  }

  public static Component tr(Locale locale, String key, TagResolver... resolvers) {
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(key, "key");
    String template = str(locale, key);
    return GuiMini.mm(template, resolvers);
  }

  public static String str(Locale locale, String key) {
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(key, "key");

    ResourceBundle bundle = bundle(locale);
    if (bundle != null && bundle.containsKey(key)) {
      return bundle.getString(key);
    }

    // Fallback to default locale bundle.
    ResourceBundle fallback = bundle(defaultLocale);
    if (fallback != null && fallback.containsKey(key)) {
      return fallback.getString(key);
    }

    // Last-resort: show the key.
    return "<gray>" + key + "</gray>";
  }

  private static ResourceBundle bundle(Locale locale) {
    String cacheKey = locale.toLanguageTag();
    return bundleCache.computeIfAbsent(cacheKey, k -> {
      try {
        return ResourceBundle.getBundle(BASE_NAME, locale, GuiI18n.class.getClassLoader());
      } catch (MissingResourceException ex) {
        return null;
      }
    });
  }
}
