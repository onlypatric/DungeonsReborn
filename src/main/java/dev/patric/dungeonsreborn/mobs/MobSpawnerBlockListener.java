package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

import org.bukkit.Location;
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

public final class MobSpawnerBlockListener implements Listener {
  private final MobYamlRegistry yaml;
  private final MobRegistry registry;
  private final MobSpawnManager spawns;
  private final MobSpawnerBlockStore store;
  private final ServiceLogger logger;
  private final boolean ownershipEnabled;
  private final boolean adminOnly;
  private final String adminPermission;

  public MobSpawnerBlockListener(MobYamlRegistry yaml, MobRegistry registry, MobSpawnManager spawns,
      MobSpawnerBlockStore store, ServiceLogger logger, boolean ownershipEnabled, boolean adminOnly,
      String adminPermission) {
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.spawns = Objects.requireNonNull(spawns, "spawns");
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
    ItemStack item = event.getItemInHand();
    String blockId = MobSpawnerMarkers.getSpawnerBlockId(item);
    MobSpawnerBlockSpec blockSpec = null;
    if (blockId != null) {
      blockSpec = yaml.spawnerBlockSpec(blockId);
      if (blockSpec == null) {
        event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.unknownBlockId",
            Locales.placeholders("id", blockId)));
        event.setCancelled(true);
        return;
      }
    }
    String mobId = blockSpec == null ? MobSpawnerMarkers.getSpawnerMobId(item) : blockSpec.mobId();
    if (mobId == null) {
      return;
    }
    mobId = Ids.normalize(mobId);
    if (!registry.has(mobId)) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.unknownMobId",
          Locales.placeholders("id", mobId)));
      event.setCancelled(true);
      return;
    }
    Block block = event.getBlockPlaced();
    if (block.getType() != Material.SPAWNER) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.mustPlaceSpawner"));
      event.setCancelled(true);
      return;
    }
    String desiredId = MobSpawnerMarkers.getSpawnerId(item);
    Location spawnLoc = block.getLocation().add(0.5, 0.0, 0.5);
    String createdId;
    try {
      if (desiredId != null && spawns.hasSpawn(desiredId)) {
        MobSpawnSpec existing = spawns.spawnSpec(desiredId);
        if (existing != null && !existing.mobId().equals(mobId)) {
          event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.mobMismatch",
              Locales.placeholders("mob", existing.mobId())));
          event.setCancelled(true);
          return;
        }
        yaml.relocateSpawn(desiredId, spawnLoc, mobId);
        createdId = desiredId;
      } else {
        if (blockSpec != null) {
          createdId = yaml.createSpawnFromTemplate(blockSpec, desiredId, spawnLoc);
        } else {
          createdId = yaml.createSpawn(desiredId, mobId, spawnLoc);
        }
      }
    } catch (Exception ex) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.createFailed",
          Locales.placeholders("message", ex.getMessage())));
      event.setCancelled(true);
      return;
    }
    MobSpawnerMarkers.setSpawnerId(block, createdId);
    MobSpawnerMarkers.setSpawnerMobId(block, mobId);
    MobSpawnerMarkers.setSpawnerOwner(block, event.getPlayer().getUniqueId());
    store.upsert(block, blockSpec == null ? null : blockSpec.id(), createdId, mobId,
        event.getPlayer().getUniqueId().toString());
    event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.created",
        Locales.placeholders("id", createdId)));
    logger.info("[Mobs] spawner: placed id=" + createdId + " mob=" + mobId);
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (block.getType() != Material.SPAWNER) {
      return;
    }
    if (adminOnly && !event.getPlayer().hasPermission(adminPermission)) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.noPermission"));
      event.setCancelled(true);
      return;
    }
    if (ownershipEnabled && !event.getPlayer().hasPermission(adminPermission)) {
      java.util.UUID ownerId = MobSpawnerMarkers.getSpawnerOwner(block);
      if (ownerId == null) {
        MobSpawnerBlockStore.Entry entry = store.entry(block);
        if (entry != null && entry.ownerId() != null) {
          try {
            ownerId = java.util.UUID.fromString(entry.ownerId());
          } catch (IllegalArgumentException ex) {
          }
        }
      }
      if (ownerId != null && !ownerId.equals(event.getPlayer().getUniqueId())) {
        event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.noPermission"));
        event.setCancelled(true);
        return;
      }
    }
    String spawnId = MobSpawnerMarkers.getSpawnerId(block);
    MobSpawnerBlockStore.Entry entry = store.entry(block);
    if (spawnId == null && entry != null) {
      spawnId = entry.spawnId();
    }
    if (spawnId == null) {
      return;
    }
    String removedId = spawnId;
    int removedMobs = spawns.despawnSpawn(removedId);
    boolean removed = yaml.removeSpawn(removedId);
    store.remove(block);
    if (removed) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.removed",
          Locales.placeholders("id", removedId)));
      logger.info("[Mobs] spawner: removed id=" + removedId + " despawned=" + removedMobs);
    } else {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.mobs.spawner.entryNotFound",
          Locales.placeholders("id", removedId)));
    }
  }
}
