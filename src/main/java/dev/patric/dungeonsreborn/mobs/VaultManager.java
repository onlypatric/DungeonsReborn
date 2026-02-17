package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.VaultDisplayItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import io.papermc.paper.event.block.VaultChangeStateEvent;

public final class VaultManager implements Listener {
  private static final long DISPLAY_REFRESH_TICKS = 20L * 5L;
  private static final long INTERACTION_GUARD_TICKS = 6L;

  private final EffectsEngine engine;
  private final MobYamlRegistry yaml;
  private final EffectsYamlAbilities yamlAbilities;
  private final VaultBlockStore store;
  private final ServiceLogger logger;
  private final Random rng = new Random();
  private final Map<String, Long> recentRewards = new HashMap<>();

  public VaultManager(EffectsEngine engine, MobYamlRegistry yaml, EffectsYamlAbilities yamlAbilities,
      VaultBlockStore store, ServiceLogger logger) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.yamlAbilities = Objects.requireNonNull(yamlAbilities, "yamlAbilities");
    this.store = Objects.requireNonNull(store, "store");
    this.logger = Objects.requireNonNull(logger, "logger");
    engine.runRepeating(DISPLAY_REFRESH_TICKS, DISPLAY_REFRESH_TICKS, this::refreshDisplays);
  }

  public void onVaultPlaced(Block block) {
    refreshDisplay(block);
  }

  public void onVaultRemoved(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    String prefix = keyFor(block) + ":";
    recentRewards.keySet().removeIf(key -> key.startsWith(prefix));
  }

  @EventHandler(ignoreCancelled = true)
  public void onVaultInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }
    Block block = event.getClickedBlock();
    if (block == null || block.getType() != org.bukkit.Material.VAULT) {
      return;
    }
    String vaultId = MobSpawnerMarkers.getVaultId(block);
    if (vaultId == null || vaultId.isBlank()) {
      VaultBlockStore.Entry entry = store.entry(block);
      if (entry != null) {
        vaultId = entry.vaultId();
      }
    }
    if (vaultId == null || vaultId.isBlank()) {
      return;
    }
    VaultSpec spec = yaml.vaultSpec(vaultId);
    if (spec == null) {
      return;
    }
    event.setCancelled(true);
    Player player = event.getPlayer();
    if (player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5))
        > spec.deactivationRange() * spec.deactivationRange()) {
      return;
    }
    if (player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5))
        > spec.activationRange() * spec.activationRange()) {
      return;
    }
    String dedupeKey = keyFor(block) + ":" + player.getUniqueId();
    long now = engine.tickNow();
    Long last = recentRewards.get(dedupeKey);
    if (last != null && now - last <= INTERACTION_GUARD_TICKS) {
      return;
    }
    org.bukkit.block.BlockState state = block.getState();
    if (!(state instanceof org.bukkit.block.Vault vault)) {
      return;
    }
    UUID playerId = player.getUniqueId();
    if (vault.hasRewardedPlayer(playerId)) {
      return;
    }
    ItemStack hand = event.getItem();
    if (hand == null || hand.getType().isAir()) {
      return;
    }
    String itemId = ItemMarkers.getItemId(hand);
    if (itemId == null || !itemId.equals(spec.keyItem())) {
      return;
    }
    consumeOne(player, event.getHand(), hand);
    boolean ominous = false;
    org.bukkit.block.data.BlockData data = block.getBlockData();
    if (data instanceof org.bukkit.block.data.type.Vault vaultData) {
      ominous = vaultData.isOminous();
    }
    String poolId = ominous ? spec.lootPoolOminous() : spec.lootPoolNormal();
    MobLootSpec loot = yaml.lootPool(poolId);
    if (loot != null) {
      giveLoot(block.getLocation().add(0.5, 1.0, 0.5), player, loot, new HashSet<>());
    }
    vault.addRewardedPlayer(playerId);
    vault.setNextStateUpdateTime(Math.max(vault.getNextStateUpdateTime(), System.currentTimeMillis() + 200L));
    vault.update();
    recentRewards.put(dedupeKey, now);
    refreshDisplay(block);
    logger.info("[Mobs] vault: opened id=" + spec.id() + " player=" + player.getName() + " ominous=" + ominous);
  }

  @EventHandler(ignoreCancelled = true)
  public void onVaultDisplayItem(VaultDisplayItemEvent event) {
    Block block = event.getBlock();
    String vaultId = resolveVaultId(block);
    if (vaultId == null) {
      return;
    }
    VaultSpec spec = yaml.vaultSpec(vaultId);
    if (spec == null) {
      return;
    }
    ItemStack display = pickDisplayItem(spec);
    if (display != null) {
      event.setDisplayItem(display);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onVaultStateChange(VaultChangeStateEvent event) {
    Block block = event.getBlock();
    if (block != null) {
      refreshDisplay(block);
    }
  }

  private void refreshDisplays() {
    for (VaultBlockStore.Entry entry : store.entries()) {
      if (entry == null) {
        continue;
      }
      org.bukkit.World world = Bukkit.getWorld(entry.world());
      if (world == null) {
        continue;
      }
      Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
      refreshDisplay(block);
    }
  }

  private void refreshDisplay(Block block) {
    if (block == null || block.getType() != org.bukkit.Material.VAULT) {
      return;
    }
    String vaultId = resolveVaultId(block);
    if (vaultId == null) {
      return;
    }
    VaultSpec spec = yaml.vaultSpec(vaultId);
    if (spec == null) {
      return;
    }
    org.bukkit.block.BlockState state = block.getState();
    if (!(state instanceof org.bukkit.block.Vault vault)) {
      return;
    }
    ItemStack display = pickDisplayItem(spec);
    if (display == null) {
      return;
    }
    vault.setDisplayedItem(display);
    vault.update();
  }

  private String resolveVaultId(Block block) {
    String vaultId = MobSpawnerMarkers.getVaultId(block);
    if ((vaultId == null || vaultId.isBlank()) && store.entry(block) != null) {
      vaultId = store.entry(block).vaultId();
    }
    if (vaultId == null || vaultId.isBlank()) {
      return null;
    }
    return vaultId;
  }

  private ItemStack pickDisplayItem(VaultSpec spec) {
    List<VaultDisplayItemEntry> pool = spec.displayedItemPool();
    if (pool != null && !pool.isEmpty()) {
      String id = pickWeightedItem(pool);
      if (id != null) {
        ItemStack item = yamlAbilities.itemTemplate(id);
        if (item != null) {
          return item;
        }
      }
    }
    return yamlAbilities.itemTemplate(spec.keyItem());
  }

  private String pickWeightedItem(List<VaultDisplayItemEntry> pool) {
    double total = 0.0;
    for (VaultDisplayItemEntry entry : pool) {
      total += Math.max(0.0, entry.weight());
    }
    if (total <= 0.0) {
      return pool.get(0).itemId();
    }
    double roll = ThreadLocalRandom.current().nextDouble() * total;
    double acc = 0.0;
    for (VaultDisplayItemEntry entry : pool) {
      acc += Math.max(0.0, entry.weight());
      if (roll <= acc) {
        return entry.itemId();
      }
    }
    return pool.get(pool.size() - 1).itemId();
  }

  private void consumeOne(Player player, EquipmentSlot handSlot, ItemStack stack) {
    if (stack.getAmount() <= 1) {
      if (handSlot == EquipmentSlot.OFF_HAND) {
        player.getInventory().setItemInOffHand(null);
      } else {
        player.getInventory().setItemInMainHand(null);
      }
      return;
    }
    stack.setAmount(stack.getAmount() - 1);
    if (handSlot == EquipmentSlot.OFF_HAND) {
      player.getInventory().setItemInOffHand(stack);
    } else {
      player.getInventory().setItemInMainHand(stack);
    }
  }

  private void giveLoot(Location loc, Player player, MobLootSpec loot, Set<String> visitedPools) {
    for (MobDropSpec drop : loot.guaranteed()) {
      giveDrop(loc, player, drop);
    }
    int rolls = loot.rolls() + loot.bonusRolls();
    for (int i = 0; i < rolls; i++) {
      for (MobDropSpec drop : loot.drops()) {
        giveDrop(loc, player, drop);
      }
    }
    for (MobLootPoolRef ref : loot.pools()) {
      if (ref == null || !visitedPools.add(ref.poolId())) {
        continue;
      }
      if (ref.chance() < 1.0 && rng.nextDouble() > ref.chance()) {
        continue;
      }
      MobLootSpec nested = yaml.lootPool(ref.poolId());
      if (nested != null) {
        giveLoot(loc, player, nested, visitedPools);
      }
    }
  }

  private void giveDrop(Location loc, Player player, MobDropSpec drop) {
    int amount = drop.rollAmount(rng);
    if (amount <= 0) {
      return;
    }
    ItemStack stack = drop.item().clone();
    stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), amount)));
    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
    for (ItemStack leftover : leftovers.values()) {
      if (leftover != null && !leftover.getType().isAir()) {
        loc.getWorld().dropItemNaturally(loc, leftover);
      }
    }
  }

  private static String keyFor(Block block) {
    return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
  }
}
