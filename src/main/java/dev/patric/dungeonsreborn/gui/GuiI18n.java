package dev.patric.dungeonsreborn.gui;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
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
  private static volatile Locale defaultLocale = Locale.US;
  private static final java.util.regex.Pattern LEGACY_PLACEHOLDER =
      java.util.regex.Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

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
    LocaleService service = Locales.service();
    if (service != null) {
      return Locale.forLanguageTag(service.defaultLocale());
    }
    return defaultLocale;
  }

  public static Component tr(Player player, String key, TagResolver... resolvers) {
    return tr(locale(player), key, resolvers);
  }

  public static String str(Player player, String key) {
    return str(locale(player), key);
  }

  public static Component tr(String key, TagResolver... resolvers) {
    return tr(defaultLocale, key, resolvers);
  }

  public static Component tr(Locale locale, String key, TagResolver... resolvers) {
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(key, "key");
    LocaleService service = Locales.service();
    if (service != null) {
      String localeTag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
      String template = service.text(localeTag, key, java.util.Map.of());
      return GuiMini.mm(normalizeTemplate(template), resolvers);
    }
    String template = str(locale, key);
    return GuiMini.mm(normalizeTemplate(template), resolvers);
  }

  public static String str(Locale locale, String key) {
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(key, "key");
    LocaleService service = Locales.service();
    if (service != null) {
      String localeTag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
      return service.text(localeTag, key, java.util.Map.of());
    }
    return "<gray>" + key + "</gray>";
  }

  private static String normalizeTemplate(String template) {
    if (template == null || template.isBlank()) {
      return template;
    }
    return LEGACY_PLACEHOLDER.matcher(template).replaceAll("<$1>");
  }
}
