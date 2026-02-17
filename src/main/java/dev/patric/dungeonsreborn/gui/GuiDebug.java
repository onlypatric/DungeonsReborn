package dev.patric.dungeonsreborn.gui;

import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;

public final class GuiDebug {
  private GuiDebug() {
  }

  public static boolean indexCountsEnabled() {
    try {
      return JavaPlugin.getPlugin(DungeonsRebornPlugin.class)
          .getConfig()
          .getBoolean("debug.gui.indexCounts", false);
    } catch (IllegalStateException ex) {
      return false;
    }
  }

  public static boolean logIndexOnce(boolean alreadyLogged, String menu, int count) {
    return logIndexOnce(alreadyLogged, menu, count, null);
  }

  public static boolean logIndexOnce(boolean alreadyLogged, String menu, int count, String detail) {
    if (alreadyLogged || !indexCountsEnabled()) {
      return alreadyLogged;
    }
    DungeonsRebornPlugin plugin = JavaPlugin.getPlugin(DungeonsRebornPlugin.class);
    String suffix = (detail == null || detail.isBlank()) ? "" : " " + detail;
    plugin.getLogger().info("[GUI] index=" + menu + " count=" + count + suffix);
    return true;
  }
}
