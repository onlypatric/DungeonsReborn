package dev.patric.dungeonsreborn.effects.costs;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.mana.ManaUiConfig;
import dev.patric.dungeonsreborn.effects.mana.ManaUiSettings;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Costs {
  private Costs() {
  }

  public static Cost mana(double amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    return resource(ManaProvider.DEFAULT_RESOURCE, amount);
  }

  public static Cost resource(String resourceId, double amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    return ctx -> {
      if (!(ctx.caster() instanceof Player player)) {
        return Component.text("Only players can pay this cost.");
      }
      ManaProvider provider = ctx.engine().manaProvider();
      if (provider == null) {
        return Component.text("No resource provider installed.");
      }
      double cost = amount
          * readMultiplier(ctx, "upgrade_mana_mult", 1.0)
          * readMultiplier(ctx, "minion_mana_mult", 1.0)
          + readNumber(ctx, "upgrade_mana_add", 0.0)
          + readNumber(ctx, "minion_mana_add", 0.0);
      if (ManaProvider.DEFAULT_RESOURCE.equals(resourceId)) {
        double itemMultiplier = 0.0;
        double itemAdd = 0.0;
        ItemStack[] items = {
            player.getInventory().getItemInMainHand(),
            player.getInventory().getItemInOffHand(),
            player.getInventory().getHelmet(),
            player.getInventory().getChestplate(),
            player.getInventory().getLeggings(),
            player.getInventory().getBoots()
        };
        for (ItemStack item : items) {
          if (item == null || item.getType().isAir()) {
            continue;
          }
          itemMultiplier += ItemMarkers.getManaCostMultiplier(item);
          itemAdd += ItemMarkers.getManaCostAdd(item);
        }
        if (Double.isFinite(itemMultiplier)) {
          cost *= (1.0 + itemMultiplier);
        }
        if (Double.isFinite(itemAdd)) {
          cost += itemAdd;
        }
      }
      cost *= provider.rules(player, resourceId).costMultiplier();
      if (cost <= 0.0) {
        return null;
      }
      double current = provider.get(player, resourceId);
      double max = provider.getMax(player, resourceId);
      if (current + 1e-9 < cost) {
        return Locales.component(player, "messages.mana.insufficient", Locales.placeholders(
            "resource", displayName(resourceId),
            "current", format(current),
            "max", format(max),
            "cost", format(cost)));
      }
      Component fail = provider.tryConsume(player, resourceId, cost);
      if (fail != null) {
        return fail;
      }
      ctx.engine().markManaSpend(player.getUniqueId());
      if (ctx.engine().isDebugEnabled()) {
        ctx.engine().logger().debug("[Mana] spend player=" + player.getUniqueId()
            + " resource=" + resourceId
            + " cost=" + format(cost)
            + " current=" + format(provider.get(player, resourceId))
            + " max=" + format(provider.getMax(player, resourceId)));
      }

      current = provider.get(player, resourceId);
      max = provider.getMax(player, resourceId);
      emitActionbar(ctx, player, resourceId, current, max);
      emitWarning(ctx, player, resourceId, current, max);
      return null;
    };
  }

  private static void emitActionbar(dev.patric.dungeonsreborn.effects.CastContext ctx, Player player, String resourceId,
      double current, double max) {
    ManaUiConfig config = ctx.engine().manaUiConfig();
    if (!config.actionbar().enabled()) {
      return;
    }
    ManaUiSettings settings = ctx.engine().manaUiSettings();
    if (!settings.enabled(player, ManaUiSettings.Flag.ACTIONBAR)) {
      return;
    }
    String template = config.actionbar().template();
    String rendered = replacePlaceholders(template, resourceId, current, max);
    Component component = MiniMessage.miniMessage().deserialize(rendered);
    player.sendActionBar(component.decorate(TextDecoration.BOLD));
  }

  private static void emitWarning(dev.patric.dungeonsreborn.effects.CastContext ctx, Player player, String resourceId,
      double current, double max) {
    ManaUiConfig config = ctx.engine().manaUiConfig();
    ManaUiConfig.Warnings warnings = config.warnings();
    if (!warnings.enabled()) {
      return;
    }
    ManaUiSettings settings = ctx.engine().manaUiSettings();
    if (!settings.enabled(player, ManaUiSettings.Flag.WARNINGS)) {
      return;
    }
    if (max <= 0.0) {
      return;
    }
    double percent = (current / max) * 100.0;
    if (percent > warnings.thresholdPercent()) {
      return;
    }
    long now = ctx.engine().tickNow();
    if (!settings.tryWarn(player.getUniqueId(), now, warnings.cooldownTicks())) {
      return;
    }
    player.sendMessage(Locales.component(player, warnings.messageKey(), Locales.placeholders(
        "resource", displayName(resourceId),
        "current", format(current),
        "max", format(max),
        "percent", String.valueOf((int) Math.round(percent)))));
  }

  private static double readNumber(dev.patric.dungeonsreborn.effects.CastContext ctx, String key, double fallback) {
    Object value = ctx.variables().get(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String raw) {
      try {
        return Double.parseDouble(raw.trim());
      } catch (Exception ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static double readMultiplier(dev.patric.dungeonsreborn.effects.CastContext ctx, String key, double fallback) {
    double value = readNumber(ctx, key, fallback);
    if (!Double.isFinite(value)) {
      return fallback;
    }
    return value;
  }

  private static String displayName(String resourceId) {
    if (resourceId == null || resourceId.isBlank() || ManaProvider.DEFAULT_RESOURCE.equals(resourceId)) {
      return "Mana";
    }
    String trimmed = resourceId.trim();
    return trimmed.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + trimmed.substring(1);
  }

  public static Cost consumeMainHand(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    return ctx -> {
      if (!Bukkit.isPrimaryThread()) {
        throw new IllegalStateException("Costs.consumeMainHand must be called on the primary thread");
      }
      if (!(ctx.caster() instanceof Player player)) {
        return Component.text("Only players can pay this cost.");
      }
      ItemStack item = player.getInventory().getItemInMainHand();
      if (item == null || item.getType().isAir()) {
        return Component.text("You must hold an item.");
      }
      if (item.getAmount() < amount) {
        return Component.text("Not enough items (" + amount + " required).");
      }
      item.setAmount(item.getAmount() - amount);
      if (item.getAmount() <= 0) {
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      } else {
        player.getInventory().setItemInMainHand(item);
      }
      player.updateInventory();
      return null;
    };
  }

  public static Cost durabilityMainHand(int damageAmount, boolean allowBreak) {
    if (damageAmount <= 0) {
      throw new IllegalArgumentException("damageAmount must be > 0");
    }
    return ctx -> {
      if (!Bukkit.isPrimaryThread()) {
        throw new IllegalStateException("Costs.durabilityMainHand must be called on the primary thread");
      }
      if (!(ctx.caster() instanceof Player player)) {
        return Component.text("Only players can pay this cost.");
      }
      ItemStack item = player.getInventory().getItemInMainHand();
      if (item == null || item.getType().isAir()) {
        return Component.text("You must hold an item.");
      }
      if (item.getType().getMaxDurability() <= 0) {
        return Component.text("That item has no durability.");
      }
      ItemMeta meta = item.getItemMeta();
      if (!(meta instanceof Damageable damageable)) {
        return Component.text("That item has no durability.");
      }
      int max = item.getType().getMaxDurability();
      int current = Math.max(0, damageable.getDamage());
      int next = current + damageAmount;
      if (!allowBreak && next >= max) {
        return Component.text("Not enough durability.");
      }
      damageable.setDamage(Math.min(max, next));
      item.setItemMeta(meta);
      if (next >= max) {
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      } else {
        player.getInventory().setItemInMainHand(item);
      }
      player.updateInventory();
      return null;
    };
  }

  public static Cost composite(Cost... costs) {
    Objects.requireNonNull(costs, "costs");
    for (Cost c : costs) {
      Objects.requireNonNull(c, "cost");
    }
    return ctx -> {
      for (Cost cost : costs) {
        Component fail = cost.tryApply(ctx);
        if (fail != null) {
          return fail;
        }
      }
      return null;
    };
  }

  private static String format(double v) {
    if (Math.abs(v - Math.round(v)) < 1e-9) {
      return String.valueOf((long) Math.round(v));
    }
    return String.format(java.util.Locale.ROOT, "%.2f", v);
  }

  private static String replacePlaceholders(String template, String resourceId, double current, double max) {
    String rendered = template;
    int percent = max <= 0 ? 0 : (int) Math.round((current / max) * 100.0);
    rendered = rendered.replace("{resource}", displayName(resourceId));
    rendered = rendered.replace("{current}", format(current));
    rendered = rendered.replace("{max}", format(max));
    rendered = rendered.replace("{percent}", String.valueOf(percent));
    return rendered;
  }
}
