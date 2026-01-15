package dev.patric.dungeonsreborn.mobs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.locale.Locales;

public final class MobEggListener implements Listener {
  private final EffectsEngine engine;
  private final MobRegistry registry;
  private final MobYamlRegistry yaml;
  private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

  public MobEggListener(EffectsEngine engine, MobRegistry registry, MobYamlRegistry yaml) {
    this.engine = engine;
    this.registry = registry;
    this.yaml = yaml;
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }
    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    ItemStack item = event.getItem();
    if (item == null || item.getType().isAir()) {
      return;
    }
    MobEggSpec egg = yaml.eggFromItem(item);
    if (egg == null) {
      return;
    }
    Player player = event.getPlayer();
    if (egg.permission() != null && !egg.permission().isBlank() && !player.hasPermission(egg.permission())) {
      player.sendMessage(Locales.component(player, "messages.mobs.egg.missingPermission",
          Locales.placeholders("perm", egg.permission())));
      event.setCancelled(true);
      return;
    }
    if (!tryStartCooldown(player, egg)) {
      event.setCancelled(true);
      return;
    }

    Location spawn = resolveSpawnLocation(event, player);
    if (spawn == null) {
      player.sendMessage(Locales.component(player, "messages.mobs.egg.noTarget"));
      event.setCancelled(true);
      return;
    }
    try {
      registry.spawn(egg.mobId(), spawn, player.getUniqueId());
    } catch (Exception ex) {
      player.sendMessage(Locales.component(player, "messages.mobs.egg.spawnFailed",
          Locales.placeholders("message", ex.getMessage())));
      event.setCancelled(true);
      return;
    }
    consumeEgg(player, event.getHand());
    event.setCancelled(true);
  }

  private boolean tryStartCooldown(Player player, MobEggSpec egg) {
    UUID playerId = player.getUniqueId();
    long cooldownTicks = egg.cooldownTicks();
    if (cooldownTicks <= 0L) {
      return true;
    }
    long now = engine.tickNow();
    Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
    Long until = playerCooldowns.get(egg.id());
    if (until != null && until > now) {
      long remaining = until - now;
      player.sendMessage(Locales.component(player, "messages.mobs.egg.cooldown",
          Locales.placeholders("ticks", String.valueOf(remaining))));
      return false;
    }
    playerCooldowns.put(egg.id(), now + cooldownTicks);
    return true;
  }

  private Location resolveSpawnLocation(PlayerInteractEvent event, Player player) {
    Block clicked = event.getClickedBlock();
    if (clicked != null) {
      return clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
    }
    Block target = player.getTargetBlockExact(6);
    if (target == null) {
      return null;
    }
    return target.getLocation().add(0.5, 1.0, 0.5);
  }

  private void consumeEgg(Player player, EquipmentSlot hand) {
    if (player.getGameMode() == GameMode.CREATIVE) {
      return;
    }
    ItemStack item = hand == EquipmentSlot.HAND
        ? player.getInventory().getItemInMainHand()
        : player.getInventory().getItemInOffHand();
    if (item == null || item.getType().isAir()) {
      return;
    }
    int next = item.getAmount() - 1;
    if (next <= 0) {
      item = null;
    } else {
      item.setAmount(next);
    }
    if (hand == EquipmentSlot.HAND) {
      player.getInventory().setItemInMainHand(item);
    } else {
      player.getInventory().setItemInOffHand(item);
    }
  }
}
