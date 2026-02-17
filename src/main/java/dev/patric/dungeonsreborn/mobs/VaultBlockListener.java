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

public final class VaultBlockListener implements Listener {
  private final MobYamlRegistry yaml;
  private final VaultBlockStore store;
  private final VaultManager vaultManager;
  private final ServiceLogger logger;
  private final boolean ownershipEnabled;
  private final boolean adminOnly;
  private final String adminPermission;

  public VaultBlockListener(MobYamlRegistry yaml, VaultBlockStore store, VaultManager vaultManager,
      ServiceLogger logger, boolean ownershipEnabled, boolean adminOnly, String adminPermission) {
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.store = Objects.requireNonNull(store, "store");
    this.vaultManager = Objects.requireNonNull(vaultManager, "vaultManager");
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
    if (block.getType() != Material.VAULT) {
      return;
    }
    ItemStack item = event.getItemInHand();
    String vaultId = MobSpawnerMarkers.getVaultId(item);
    if (vaultId == null || vaultId.isBlank()) {
      return;
    }
    vaultId = Ids.normalize(vaultId);
    if (yaml.vaultSpec(vaultId) == null) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.command.mobs.spawnerUnknown",
          Locales.placeholders("id", vaultId)));
      return;
    }
    MobSpawnerMarkers.setVaultId(block, vaultId);
    MobSpawnerMarkers.setVaultOwner(block, event.getPlayer().getUniqueId());
    store.upsert(block, vaultId, event.getPlayer().getUniqueId().toString());
    vaultManager.onVaultPlaced(block);
    logger.info("[Mobs] vault: placed id=" + vaultId + " by=" + event.getPlayer().getName());
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (block.getType() != Material.VAULT) {
      return;
    }
    String vaultId = MobSpawnerMarkers.getVaultId(block);
    VaultBlockStore.Entry entry = store.entry(block);
    if ((vaultId == null || vaultId.isBlank()) && entry != null) {
      vaultId = entry.vaultId();
    }
    if (vaultId == null || vaultId.isBlank()) {
      return;
    }
    if (adminOnly && !event.getPlayer().hasPermission(adminPermission)) {
      event.getPlayer().sendMessage(Locales.component(event.getPlayer(), "messages.noPermission"));
      event.setCancelled(true);
      return;
    }
    if (ownershipEnabled && !event.getPlayer().hasPermission(adminPermission)) {
      java.util.UUID ownerId = MobSpawnerMarkers.getVaultOwner(block);
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
    vaultManager.onVaultRemoved(block);
    store.remove(block);
    logger.info("[Mobs] vault: removed id=" + vaultId + " by=" + event.getPlayer().getName());
  }
}
