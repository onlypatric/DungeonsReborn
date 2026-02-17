package dev.patric.dungeonsreborn.gui.style;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItems;
import net.kyori.adventure.text.Component;

public final class GuiButtons {
  public enum Type {
    BACK,
    CLOSE,
    NEXT,
    PREV,
    FIRST,
    LAST,
    PAGE,
    SEARCH,
    CLEAR,
    CONFIRM,
    CANCEL,
    SAVE,
    PREVIEW,
    TEST,
    RESET,
    HOME,
    REFRESH,
    INFO,
    HELP,
    WARNING,
    ERROR,
    FILTER_ON,
    FILTER_OFF,
    SORT_ASC,
    SORT_DESC
  }

  private GuiButtons() {
  }

  public static ItemStack item(Type type, Component title) {
    return item(type, title, List.of(), true);
  }

  public static ItemStack item(Type type, Component title, List<Component> lore) {
    return item(type, title, lore, true);
  }

  public static ItemStack item(Type type, Component title, List<Component> lore, boolean hideItemFlags) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(lore, "lore");
    return GuiItems.head(headId(type), title, lore, hideItemFlags);
  }

  private static String headId(Type type) {
    return switch (type) {
      case BACK -> "LEFT";
      case CLOSE -> "CANCEL";
      case NEXT -> "RIGHT";
      case PREV -> "LEFT";
      case FIRST -> "NAV_FIRST";
      case LAST -> "NAV_LAST";
      case PAGE -> "NAV_PAGE";
      case SEARCH -> "NAV_SEARCH";
      case CLEAR -> "ICON_CLEAR";
      case CONFIRM -> "CONFIRM";
      case CANCEL -> "CANCEL";
      case SAVE -> "ACTION_SAVE";
      case PREVIEW -> "ACTION_PREVIEW";
      case TEST -> "ACTION_TEST";
      case RESET -> "ACTION_RESET";
      case HOME -> "NAV_HOME";
      case REFRESH -> "NAV_REFRESH";
      case INFO -> "NAV_INFO";
      case HELP -> "NAV_HELP";
      case WARNING -> "NAV_WARNING";
      case ERROR -> "NAV_ERROR";
      case FILTER_ON -> "FILTER_ON";
      case FILTER_OFF -> "FILTER_OFF";
      case SORT_ASC -> "SORT_ASC";
      case SORT_DESC -> "SORT_DESC";
    };
  }
}
