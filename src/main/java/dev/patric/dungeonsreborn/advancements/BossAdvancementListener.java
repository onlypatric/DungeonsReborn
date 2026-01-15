package dev.patric.dungeonsreborn.advancements;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpec;

public final class BossAdvancementListener implements Listener {
  private final MobRegistry mobRegistry;
  private final AdvancementService advancements;

  public BossAdvancementListener(MobRegistry mobRegistry, AdvancementService advancements) {
    this.mobRegistry = mobRegistry;
    this.advancements = advancements;
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    String mobId = MobMarkers.getMobId(entity);
    if (mobId == null) {
      return;
    }
    Player killer = entity.getKiller();
    if (killer == null) {
      return;
    }
    MobSpec spec = mobRegistry.get(mobId);
    if (spec == null) {
      return;
    }
    if (spec.bossBar() == null && spec.bossBroadcast() == null) {
      return;
    }
    advancements.recordBossKill(spec, mobId, killer);
  }
}
