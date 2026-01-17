package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import dev.patric.dungeonsreborn.util.TextStyles;

public final class MobSpawnerItems {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
        meta.displayName(TextStyles.noItalic(meta.displayName()));
        meta.lore(TextStyles.noItalic(lore));
        base.setItemMeta(meta);
      }
    }
    base = MobSpawnerMarkers.setSpawnerMobId(base, spec.mobId());
    base = MobSpawnerMarkers.setSpawnerBlockId(base, spec.id());
    return base;
  }

  public static ItemStack decorateSpawnerItem(ItemStack base, MobSpawnerBlockSpec spec, MobSpec mobSpec) {
    if (base == null || spec == null) {
      return base;
    }
    ItemStack item = base.clone();
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    String mobLabel = spec.mobId();
    if (mobSpec != null && mobSpec.displayName() != null) {
      mobLabel = PLAIN.serialize(mobSpec.displayName());
    }
    meta.displayName(TextStyles.noItalic(MobText.parse("<gold>" + mobLabel + " Spawner</gold>")));
    List<Component> lore = new ArrayList<>();
    lore.add(TextStyles.noItalic(MobText.parse("<gray>Mob:</gray> <white>" + mobLabel + "</white>")));
    lore.add(TextStyles.noItalic(MobText.parse("<dark_gray>Id:</dark_gray> <gray>" + spec.mobId() + "</gray>")));
    MobSpawnerTemplate template = spec.template();
    if (template != null) {
      if (template.maxAlive() != null) {
        lore.add(TextStyles.noItalic(MobText.parse("<gray>Max Alive:</gray> <white>" + template.maxAlive() + "</white>")));
      }
      if (template.respawnTicks() != null) {
        long seconds = Math.max(0L, template.respawnTicks()) / 20L;
        lore.add(TextStyles.noItalic(MobText.parse("<gray>Respawn:</gray> <white>" + seconds + "s</white>")));
      }
      if (template.radius() != null) {
        lore.add(TextStyles.noItalic(MobText.parse("<gray>Spawn Radius:</gray> <white>" + template.radius() + "</white>")));
      }
      if (template.attackRadius() != null) {
        lore.add(TextStyles.noItalic(MobText.parse("<gray>Attack Radius:</gray> <white>" + template.attackRadius() + "</white>")));
      }
    }
    lore.add(TextStyles.noItalic(MobText.parse("<dark_gray>Place to create a spawner.</dark_gray>")));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  public static ItemStack createSpawnerItem(String mobId, String spawnId) {
    Objects.requireNonNull(mobId, "mobId");
    ItemStack item = new ItemStack(Material.SPAWNER);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(TextStyles.noItalic(MobText.parse("<gold>Mob Spawner</gold>")));
      List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
      lore.add(TextStyles.noItalic(MobText.parse("<gray>Mob: <white>" + mobId + "</white></gray>")));
      if (spawnId != null && !spawnId.isBlank()) {
        lore.add(TextStyles.noItalic(MobText.parse("<gray>Id: <white>" + spawnId + "</white></gray>")));
      }
      lore.add(TextStyles.noItalic(MobText.parse("<dark_gray>Place to create a spawner.</dark_gray>")));
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
