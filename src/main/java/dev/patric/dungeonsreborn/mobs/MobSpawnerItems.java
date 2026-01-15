package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MobSpawnerItems {
  private MobSpawnerItems() {
  }

  public static ItemStack createSpawnerBlockItem(MobSpawnerBlockSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec is null");
    }
    ItemStack base = spec.item() == null ? new ItemStack(Material.SPAWNER) : spec.item().clone();
    if (spec.item() == null) {
      ItemMeta meta = base.getItemMeta();
      if (meta != null) {
        meta.displayName(MobText.parse("<gold>Mob Spawner</gold>"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MobText.parse("<gray>Mob: <white>" + spec.mobId() + "</white></gray>"));
        lore.add(MobText.parse("<dark_gray>Place to create a spawner.</dark_gray>"));
        meta.lore(lore);
        base.setItemMeta(meta);
      }
    }
    base = MobSpawnerMarkers.setSpawnerMobId(base, spec.mobId());
    base = MobSpawnerMarkers.setSpawnerBlockId(base, spec.id());
    return base;
  }

  public static ItemStack createSpawnerItem(String mobId, String spawnId) {
    Objects.requireNonNull(mobId, "mobId");
    ItemStack item = new ItemStack(Material.SPAWNER);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(MobText.parse("<gold>Mob Spawner</gold>"));
      List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
      lore.add(MobText.parse("<gray>Mob: <white>" + mobId + "</white></gray>"));
      if (spawnId != null && !spawnId.isBlank()) {
        lore.add(MobText.parse("<gray>Id: <white>" + spawnId + "</white></gray>"));
      }
      lore.add(MobText.parse("<dark_gray>Place to create a spawner.</dark_gray>"));
      meta.lore(lore);
      item.setItemMeta(meta);
    }
    MobSpawnerMarkers.setSpawnerMobId(item, mobId);
    if (spawnId != null && !spawnId.isBlank()) {
      MobSpawnerMarkers.setSpawnerId(item, spawnId);
    }
    return item;
  }
}
