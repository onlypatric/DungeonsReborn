package dev.patric.dungeonsreborn.effects.items;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;

public final class ItemHookListener implements Listener {
  private final EffectsEngine engine;
  private final EffectsYamlAbilities yaml;

  public ItemHookListener(EffectsEngine engine, EffectsYamlAbilities yaml) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.yaml = Objects.requireNonNull(yaml, "yaml");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEquip(PlayerItemHeldEvent event) {
    Player player = event.getPlayer();
    ItemStack item = player.getInventory().getItem(event.getNewSlot());
    triggerHooks(player, item, ItemHookType.ON_EQUIP);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSwap(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getMainHandItem();
    triggerHooks(player, item, ItemHookType.ON_EQUIP);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHit(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player)) {
      return;
    }
    ItemStack item = player.getInventory().getItemInMainHand();
    triggerHooks(player, item, ItemHookType.ON_HIT);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHurt(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    ItemStack item = player.getInventory().getItemInMainHand();
    triggerHooks(player, item, ItemHookType.ON_HURT);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    triggerHooks(player, item, ItemHookType.ON_CONSUME);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    ItemStack item = player.getInventory().getItemInMainHand();
    triggerHooks(player, item, ItemHookType.ON_BLOCK_BREAK);
  }

  private void triggerHooks(Player player, ItemStack item, ItemHookType type) {
    if (item == null || item.getType().isAir()) {
      return;
    }
    List<ItemHookSpec> hooks = yaml.itemHooks(player, item, type);
    if (hooks.isEmpty()) {
      return;
    }
    for (int i = 0; i < hooks.size(); i++) {
      ItemHookSpec hook = hooks.get(i);
      if (!checkCooldown(player, hook, type, i)) {
        continue;
      }
      if (!payConsume(player, item, hook.consumeAmount())) {
        continue;
      }
      if (!payMana(player, hook.manaCost())) {
        continue;
      }
      if (!payDurability(player, item, hook.durabilityCost())) {
        continue;
      }
      runHook(player, hook);
    }
  }

  private boolean checkCooldown(Player player, ItemHookSpec hook, ItemHookType type, int index) {
    long cooldown = hook.cooldownTicks();
    if (cooldown <= 0L) {
      return true;
    }
    String key = "itemhook:" + type.name().toLowerCase(java.util.Locale.ROOT) + ":" + index;
    return engine.tryStartCooldown(player.getUniqueId(), key, cooldown);
  }

  private boolean payMana(Player player, double cost) {
    if (cost <= 0.0) {
      return true;
    }
    ManaProvider provider = engine.manaProvider();
    if (provider == null) {
      return false;
    }
    if (provider.tryConsume(player, cost) != null) {
      return false;
    }
    engine.markManaSpend(player.getUniqueId());
    return true;
  }

  private boolean payConsume(Player player, ItemStack item, int amount) {
    if (amount <= 0) {
      return true;
    }
    if (item == null || item.getType().isAir()) {
      return false;
    }
    ItemStack main = player.getInventory().getItemInMainHand();
    if (canConsume(main, item, amount)) {
      consumeStack(player, true, main, amount);
      return true;
    }
    ItemStack off = player.getInventory().getItemInOffHand();
    if (canConsume(off, item, amount)) {
      consumeStack(player, false, off, amount);
      return true;
    }
    return false;
  }

  private boolean canConsume(ItemStack candidate, ItemStack expected, int amount) {
    return candidate != null
        && !candidate.getType().isAir()
        && candidate.isSimilar(expected)
        && candidate.getAmount() >= amount;
  }

  private void consumeStack(Player player, boolean mainHand, ItemStack stack, int amount) {
    int remaining = stack.getAmount() - amount;
    if (remaining <= 0) {
      if (mainHand) {
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      } else {
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
      }
      return;
    }
    stack.setAmount(remaining);
    if (mainHand) {
      player.getInventory().setItemInMainHand(stack);
    } else {
      player.getInventory().setItemInOffHand(stack);
    }
  }

  private boolean payDurability(Player player, ItemStack item, int damage) {
    if (damage <= 0) {
      return true;
    }
    if (item.getType().getMaxDurability() <= 0) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof Damageable dmg)) {
      return false;
    }
    dmg.setDamage(dmg.getDamage() + damage);
    item.setItemMeta(meta);
    if (dmg.getDamage() >= item.getType().getMaxDurability()) {
      player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }
    return true;
  }

  private void runHook(Player player, ItemHookSpec hook) {
    if (hook.action() != null) {
      engine.castAction("item_hook", player, hook.action());
    }
    for (String abilityId : hook.abilities()) {
      engine.cast(abilityId, player);
    }
  }
}
