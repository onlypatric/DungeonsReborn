package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class TrialSpawnerBlockListener implements Listener {
  private final MobYamlRegistry yaml;
  private final TrialSpawnerManager trialManager;
  private final TrialSpawnerBlockStore store;
  private final ServiceLogger logger;
  private final boolean ownershipEnabled;
  private final boolean adminOnly;
  private final String adminPermission;

  public TrialSpawnerBlockListener(MobYamlRegistry yaml, TrialSpawnerManager trialManager, TrialSpawnerBlockStore store,
      ServiceLogger logger, boolean ownershipEnabled, boolean adminOnly, String adminPermission) {
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.trialManager = Objects.requireNonNull(trialManager, "trialManager");
    this.store = Objects.requireNonNull(store, "store");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.ownershipEnabled = ownershipEnabled;
    this.adminOnly = adminOnly;
    this.adminPermission = adminPermission == null ? "" : adminPermission;
  }

  @EventHandler(ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    if (adminOnly && !event.getPlayer().hasPermission(adminPermission)) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.noPermission"));
      event.setCancelled(true);
      return;
    }
    Block block = event.getBlockPlaced();
    if (block.getType() != Material.TRIAL_SPAWNER) {
      return;
    }
    ItemStack item = event.getItemInHand();
    String trialSpawnerId = MobSpawnerMarkers.getTrialSpawnerId(item);
    if (trialSpawnerId == null || trialSpawnerId.isBlank()) {
      return;
    }
    trialSpawnerId = Ids.normalize(trialSpawnerId);
    if (yaml.trialSpawnerSpec(trialSpawnerId) == null) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.command.mobs.spawnerUnknown",
          Locales.placeholders("id", trialSpawnerId)));
      return;
    }
    MobSpawnerMarkers.setTrialSpawnerId(block, trialSpawnerId);
    MobSpawnerMarkers.setTrialSpawnerOwner(block, event.getPlayer().getUniqueId());
    store.upsert(block, trialSpawnerId, event.getPlayer().getUniqueId().toString());
    trialManager.onTrialSpawnerPlaced(block);
    logger.info("[Mobs] trial-spawner: placed id=" + trialSpawnerId + " by=" + event.getPlayer().getName());
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (block.getType() != Material.TRIAL_SPAWNER) {
      return;
    }
    String trialSpawnerId = MobSpawnerMarkers.getTrialSpawnerId(block);
    TrialSpawnerBlockStore.Entry entry = store.entry(block);
    if ((trialSpawnerId == null || trialSpawnerId.isBlank()) && entry != null) {
      trialSpawnerId = entry.trialSpawnerId();
    }
    if (trialSpawnerId == null || trialSpawnerId.isBlank()) {
      return;
    }
    if (adminOnly && !event.getPlayer().hasPermission(adminPermission)) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.noPermission"));
      event.setCancelled(true);
      return;
    }
    if (ownershipEnabled && !event.getPlayer().hasPermission(adminPermission)) {
      java.util.UUID ownerId = MobSpawnerMarkers.getTrialSpawnerOwner(block);
      if (ownerId == null && entry != null && entry.ownerId() != null) {
        try {
          ownerId = java.util.UUID.fromString(entry.ownerId());
        } catch (IllegalArgumentException ignored) {
        }
      }
      if (ownerId != null && !ownerId.equals(event.getPlayer().getUniqueId())) {
        event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.noPermission"));
        event.setCancelled(true);
        return;
      }
    }
    trialManager.onTrialSpawnerRemoved(block);
    store.remove(block);
    logger.info("[Mobs] trial-spawner: removed id=" + trialSpawnerId + " by=" + event.getPlayer().getName());
  }
}
