package dev.patric.dungeonsreborn.effects.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.attribute.Attribute;

import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.damage.DamageAmountMode;
import dev.patric.dungeonsreborn.effects.damage.DamageCause;

public final class DamagePipeline {
  private final List<DamagePhase> phases;

  public DamagePipeline(List<DamagePhase> phases) {
    this.phases = List.copyOf(Objects.requireNonNull(phases, "phases"));
  }

  public static DamagePipeline defaults() {
    ArrayList<DamagePhase> phases = new ArrayList<>();
    phases.add(new PreFilterPhase());
    phases.add(new BaseResolvePhase());
    phases.add(new OffensePhase());
    phases.add(new DefensePhase());
    phases.add(new PenetrationPhase());
    phases.add(new VulnerabilityPhase());
    phases.add(new CapsFloorsPhase());
    phases.add(new FinalApplyPhase());
    phases.add(new PostHitPhase());
    return new DamagePipeline(phases);
  }

  public DamagePacket process(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
    for (DamagePhase phase : phases) {
      if (packet.cancelled()) {
        return packet;
      }
      phase.apply(engine, ctx, packet);
      packet.stage(phase.id(), packet.amount());
    }
    return packet;
  }

  private static final class PreFilterPhase implements DamagePhase {
    @Override
    public String id() {
      return "PRE_FILTER";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      if (packet.amount() <= 0.0) {
        packet.cancel();
      }
    }
  }

  private static final class BaseResolvePhase implements DamagePhase {
    @Override
    public String id() {
      return "BASE_RESOLVE";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      if (packet.spec().mode() == DamageAmountMode.PERCENT_MAX_HEALTH) {
        double pct = packet.amount();
        if (pct > 1.0) {
          pct /= 100.0;
        }
        double max = dev.patric.dungeonsreborn.effects.actions.EntityActions.resolveMaxHealth(packet.victim());
        packet.setAmount(max * Math.max(0.0, pct));
      }
    }
  }

  private static final class OffensePhase implements DamagePhase {
    @Override
    public String id() {
      return "OFFENSE";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      double amount = packet.amount();
      double critChance = packet.spec().critChance();
      if (critChance > 1.0) {
        critChance /= 100.0;
      }
      if (critChance > 0.0 && ctx.rng().nextDouble() < critChance) {
        amount *= Math.max(1.0, packet.spec().critMultiplier());
        packet.markCritical();
      }
      packet.setAmount(amount);
    }
  }

  private static final class DefensePhase implements DamagePhase {
    @Override
    public String id() {
      return "DEFENSE";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      if (packet.spec().mode() == DamageAmountMode.TRUE) {
        return;
      }
      double amount = packet.amount();
      var armor = packet.victim().getAttribute(Attribute.ARMOR);
      if (armor != null) {
        double rawArmor = Math.max(0.0, armor.getValue());
        double mitigation = Math.min(0.8, rawArmor / (rawArmor + 100.0));
        amount *= (1.0 - mitigation);
      }
      packet.setAmount(amount);
    }
  }

  private static final class PenetrationPhase implements DamagePhase {
    @Override
    public String id() {
      return "PENETRATION";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      if (packet.spec().mode() == DamageAmountMode.TRUE) {
        return;
      }
      double amount = packet.amount();
      double armorPenFlat = Math.max(0.0, packet.spec().armorPenFlat());
      double armorPenPct = packet.spec().armorPenPct();
      if (armorPenPct > 1.0) {
        armorPenPct /= 100.0;
      }
      if (armorPenFlat > 0.0 || armorPenPct > 0.0) {
        var armor = packet.victim().getAttribute(Attribute.ARMOR);
        if (armor != null) {
          double effective = Math.max(0.0, armor.getValue() - armorPenFlat);
          effective *= Math.max(0.0, 1.0 - Math.max(0.0, armorPenPct));
          double mitigation = Math.min(0.8, effective / (effective + 100.0));
          double baseline = Math.min(0.8, Math.max(0.0, armor.getValue()) / (Math.max(0.0, armor.getValue()) + 100.0));
          double ratio = (1.0 - mitigation) / Math.max(0.05, 1.0 - baseline);
          amount *= ratio;
        }
      }
      if (packet.resolvedType() != null && !packet.spec().ignoreResistance()) {
        double multiplier = engine.resistanceMultiplier(packet.victim().getUniqueId(), packet.resolvedType());
        double resistPen = packet.spec().resistPenPct();
        if (resistPen > 1.0) {
          resistPen /= 100.0;
        }
        if (resistPen > 0.0) {
          multiplier = 1.0 - ((1.0 - multiplier) * Math.max(0.0, 1.0 - resistPen));
        }
        amount *= multiplier;
      }
      packet.setAmount(amount);
    }
  }

  private static final class VulnerabilityPhase implements DamagePhase {
    @Override
    public String id() {
      return "VULNERABILITY";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      String tag = packet.spec().vulnerabilityTag();
      if (tag == null || tag.isBlank()) {
        return;
      }
      if (packet.victim().getScoreboardTags().contains(tag)) {
        packet.setAmount(packet.amount() * 1.15);
      }
    }
  }

  private static final class CapsFloorsPhase implements DamagePhase {
    @Override
    public String id() {
      return "CAPS_FLOORS";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      double amount = packet.amount();
      if (packet.spec().cap() > 0.0) {
        amount = Math.min(amount, packet.spec().cap());
      }
      if (packet.spec().maxPercent() > 0.0) {
        double pct = packet.spec().maxPercent();
        if (pct > 1.0) {
          pct /= 100.0;
        }
        double max = dev.patric.dungeonsreborn.effects.actions.EntityActions.resolveMaxHealth(packet.victim());
        amount = Math.min(amount, max * Math.max(0.0, pct));
      }
      if (packet.spec().minDamageFloor() > 0.0) {
        amount = Math.max(packet.spec().minDamageFloor(), amount);
      }
      packet.setAmount(amount);
    }
  }

  private static final class FinalApplyPhase implements DamagePhase {
    @Override
    public String id() {
      return "FINAL_APPLY";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      if (packet.amount() <= 0.0) {
        packet.cancel();
      }
    }
  }

  private static final class PostHitPhase implements DamagePhase {
    @Override
    public String id() {
      return "POST_HIT";
    }

    @Override
    public void apply(EffectsEngine engine, CastContext ctx, DamagePacket packet) {
      if (packet.spec().cause() == DamageCause.DOT) {
        // DOT lifecycle events are emitted by engine runtime hooks.
      }
    }
  }
}
