package dev.patric.dungeonsreborn.mobs.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import net.kyori.adventure.text.Component;

public final class MobDebugOverlayService {
  private static final long UPDATE_TICKS = 10L;
  private static final double SEARCH_RADIUS = 32.0;

  private static final class DebugSession {
    private String mobId;
    private UUID entityId;
  }

  private final MobRegistry registry;
  private final Map<UUID, DebugSession> sessions = new HashMap<>();

  public MobDebugOverlayService(dev.patric.dungeonsreborn.effects.EffectsEngine engine, MobRegistry registry) {
    Objects.requireNonNull(engine, "engine").runRepeating(UPDATE_TICKS, UPDATE_TICKS, this::tick);
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public boolean toggle(Player player, String mobId) {
    if (player == null || mobId == null) {
      return false;
    }
    DebugSession existing = sessions.get(player.getUniqueId());
    if (existing != null && mobId.equals(existing.mobId)) {
      sessions.remove(player.getUniqueId());
      player.sendActionBar(Component.text("Mob debug overlay disabled."));
      return false;
    }
    DebugSession session = new DebugSession();
    session.mobId = mobId;
    session.entityId = null;
    sessions.put(player.getUniqueId(), session);
    player.sendActionBar(Component.text("Mob debug overlay enabled."));
    return true;
  }

  public boolean isEnabled(Player player, String mobId) {
    if (player == null || mobId == null) {
      return false;
    }
    DebugSession session = sessions.get(player.getUniqueId());
    return session != null && mobId.equals(session.mobId);
  }

  private void tick() {
    if (sessions.isEmpty()) {
      return;
    }
    for (var entry : new HashMap<>(sessions).entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      if (player == null || !player.isOnline()) {
        sessions.remove(entry.getKey());
        continue;
      }
      DebugSession session = entry.getValue();
      LivingEntity entity = resolveEntity(player, session);
      if (entity == null) {
        player.sendActionBar(Component.text("No nearby mob: " + session.mobId));
        continue;
      }
      MobRegistry.MobDebugSnapshot snapshot = registry.debugSnapshot(entity);
      if (snapshot == null) {
        player.sendActionBar(Component.text("Mob debug unavailable."));
        continue;
      }
      player.sendActionBar(buildOverlay(snapshot));
    }
  }

  private LivingEntity resolveEntity(Player player, DebugSession session) {
    if (session.entityId != null) {
      var entity = Bukkit.getEntity(session.entityId);
      if (entity instanceof LivingEntity living) {
        String mobId = MobMarkers.getMobId(living);
        if (mobId != null && mobId.equals(session.mobId) && living.isValid()) {
          return living;
        }
      }
      session.entityId = null;
    }
    Location origin = player.getLocation();
    LivingEntity candidate = registry.findClosestOwnedMob(session.mobId, player.getUniqueId(), origin, SEARCH_RADIUS);
    if (candidate != null) {
      session.entityId = candidate.getUniqueId();
    }
    return candidate;
  }

  private Component buildOverlay(MobRegistry.MobDebugSnapshot snapshot) {
    String target = snapshot.targetName() == null ? "none" : snapshot.targetName();
    String cooldown = snapshot.cooldownSummary() == null ? "none" : snapshot.cooldownSummary();
    String pathInfo = snapshot.pathInfo() == null ? "-" : snapshot.pathInfo();
    String phase = snapshot.phaseId() == null ? "-" : snapshot.phaseId();
    String state = snapshot.behaviorState() == null ? "-" : snapshot.behaviorState();
    String age = snapshot.stateAgeTicks() <= 0 ? "-" : String.valueOf(snapshot.stateAgeTicks());
    return Component.text("mob=" + snapshot.mobId()
        + " state=" + state
        + " phase=" + phase
        + " age=" + age
        + " target=" + target
        + " cd=" + cooldown
        + " path=" + pathInfo);
  }
}
