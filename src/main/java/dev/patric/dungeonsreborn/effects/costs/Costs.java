package dev.patric.dungeonsreborn.effects.costs;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

public final class Costs {
  private Costs() {
  }

  public static Cost mana(double amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    return ctx -> {
      if (!(ctx.caster() instanceof Player player)) {
        return Component.text("Only players can pay this cost.");
      }
      ManaProvider provider = ctx.engine().manaProvider();
      if (provider == null) {
        return Component.text("No mana provider installed.");
      }
      Component fail = provider.tryConsume(player, amount);
      if (fail != null) {
        return fail;
      }

      double current = provider.get(player);
      double max = provider.getMax(player);
      player.sendActionBar(Component.text("§bMana: §f" + format(current) + "§7/§f" + format(max)).decorate(TextDecoration.BOLD));
      return null;
    };
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
}
