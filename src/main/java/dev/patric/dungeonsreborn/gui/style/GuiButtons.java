package dev.patric.dungeonsreborn.gui.style;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import net.kyori.adventure.text.Component;

/**
 * Premade "button type" item stacks (10 common presets).
 * <p>
 * These only create the visuals (ItemStack). Use {@code new Button(...)} to add behavior.
 */
public final class GuiButtons {
  private GuiButtons() {
  }

  public enum Type {
    PRIMARY(GuiTheme.PRIMARY, GuiTheme.PRIMARY.defaultMaterial(), "gui.button.primary"),
    SECONDARY(GuiTheme.SECONDARY, GuiTheme.SECONDARY.defaultMaterial(), "gui.button.secondary"),
    CONFIRM(GuiTheme.SUCCESS, Material.LIME_CONCRETE, "gui.button.confirm"),
    CANCEL(GuiTheme.DANGER, Material.RED_CONCRETE, "gui.button.cancel"),
    CLOSE(GuiTheme.DANGER, Material.BARRIER, "gui.button.close"),
    BACK(GuiTheme.NAV, Material.ARROW, "gui.button.back"),
    NEXT(GuiTheme.NAV, Material.ARROW, "gui.button.next"),
    PREV(GuiTheme.NAV, Material.ARROW, "gui.button.prev"),
    INFO(GuiTheme.INFO, Material.PAPER, "gui.button.info"),
    TRASH(GuiTheme.WARNING, Material.LAVA_BUCKET, "gui.button.trash");

    private final GuiTheme theme;
    private final Material material;
    private final String defaultLabelKey;

    Type(GuiTheme theme, Material material, String defaultLabelKey) {
      this.theme = Objects.requireNonNull(theme, "theme");
      this.material = Objects.requireNonNull(material, "material");
      this.defaultLabelKey = Objects.requireNonNull(defaultLabelKey, "defaultLabelKey");
    }

    public GuiTheme theme() {
      return theme;
    }

    public Material material() {
      return material;
    }

    public String defaultLabelKey() {
      return defaultLabelKey;
    }
  }

  public static ItemStack item(Type type) {
    Objects.requireNonNull(type, "type");
    return item(type, GuiI18n.tr(type.defaultLabelKey()), List.of());
  }

  public static ItemStack item(Type type, Component label) {
    return item(type, label, List.of());
  }

  public static ItemStack item(Type type, Component label, List<Component> lore) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(lore, "lore");

    Component name = type.theme().name(label);

    GuiItem builder = GuiItem.of(type.material()).displayName(name);
    if (!lore.isEmpty()) {
      builder.lore(List.copyOf(lore));
    }
    return builder.build();
  }

  public static ItemStack confirm() {
    return item(Type.CONFIRM);
  }

  public static ItemStack cancel() {
    return item(Type.CANCEL);
  }

  public static ItemStack close() {
    return item(Type.CLOSE);
  }

  public static ItemStack back() {
    return item(Type.BACK);
  }

  public static ItemStack next() {
    return item(Type.NEXT);
  }

  public static ItemStack prev() {
    return item(Type.PREV);
  }

  public static ItemStack info() {
    return item(Type.INFO);
  }

  public static ItemStack trash() {
    return item(Type.TRASH);
  }
}
