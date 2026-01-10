package dev.patric.dungeonsreborn.gui.style;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import dev.patric.dungeonsreborn.gui.GuiMini;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Small opinionated theme palette for GUI items/buttons.
 * <p>
 * All strings are MiniMessage.
 */
public enum GuiTheme {
  PRIMARY(Material.LIME_CONCRETE, "<gradient:#2ee59d:#00b36b><bold><text></bold></gradient>", "<gray><text></gray>"),
  SECONDARY(Material.BLUE_CONCRETE, "<gradient:#5fa8ff:#2b65ff><bold><text></bold></gradient>", "<gray><text></gray>"),
  SUCCESS(Material.LIME_CONCRETE, "<green><bold><text></bold></green>", "<gray><text></gray>"),
  DANGER(Material.RED_CONCRETE, "<red><bold><text></bold></red>", "<gray><text></gray>"),
  WARNING(Material.ORANGE_CONCRETE, "<gold><bold><text></bold></gold>", "<gray><text></gray>"),
  INFO(Material.LIGHT_BLUE_CONCRETE, "<aqua><bold><text></bold></aqua>", "<gray><text></gray>"),
  NEUTRAL(Material.GRAY_CONCRETE, "<white><bold><text></bold></white>", "<gray><text></gray>"),
  MUTED(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray><text></dark_gray>", "<dark_gray><text></dark_gray>"),
  NAV(Material.ARROW, "<white><bold><text></bold></white>", "<gray><text></gray>"),
  ACCENT(Material.PURPLE_CONCRETE, "<light_purple><bold><text></bold></light_purple>", "<gray><text></gray>");

  private final Material defaultMaterial;
  private final String nameTemplate;
  private final String loreTemplate;

  GuiTheme(Material defaultMaterial, String nameTemplate, String loreTemplate) {
    this.defaultMaterial = Objects.requireNonNull(defaultMaterial, "defaultMaterial");
    this.nameTemplate = Objects.requireNonNull(nameTemplate, "nameTemplate");
    this.loreTemplate = Objects.requireNonNull(loreTemplate, "loreTemplate");
  }

  public Material defaultMaterial() {
    return defaultMaterial;
  }

  public Component name(String text) {
    Objects.requireNonNull(text, "text");
    return GuiMini.mm(nameTemplate, Placeholder.unparsed("text", text));
  }

  public Component name(Component text) {
    Objects.requireNonNull(text, "text");
    return GuiMini.mm(nameTemplate, Placeholder.component("text", text));
  }

  public Component loreLine(String text) {
    Objects.requireNonNull(text, "text");
    return GuiMini.mm(loreTemplate, Placeholder.unparsed("text", text));
  }

  public Component loreLine(Component text) {
    Objects.requireNonNull(text, "text");
    return GuiMini.mm(loreTemplate, Placeholder.component("text", text));
  }

  public List<Component> loreLines(List<String> linesMm, TagResolver... resolvers) {
    Objects.requireNonNull(linesMm, "linesMm");
    return linesMm.stream().map(line -> GuiMini.mm(line, resolvers)).toList();
  }
}
