package dev.patric.dungeonsreborn.mobs.ai;

import dev.patric.dungeonsreborn.mobs.MobAiSpec;

public final class MobAiPresetCatalog {
  private MobAiPresetCatalog() {
  }

  public static void apply(MobAiSpec.Builder builder, MobAiProfile profile) {
    if (builder == null || profile == null) {
      return;
    }
    switch (profile) {
      case AGGRESSIVE -> {
        builder.aggroRadius(16.0);
        builder.chaseSpeed(0.32);
        builder.fleeHealthRatio(0.0);
      }
      case DEFENSIVE -> {
        builder.aggroRadius(12.0);
        builder.chaseSpeed(0.25);
        builder.fleeHealthRatio(0.25);
        builder.fleeSpeed(0.34);
      }
      case NEUTRAL -> {
        builder.aggroRadius(10.0);
        builder.chaseSpeed(0.22);
      }
      case PASSIVE -> {
        builder.aggroRadius(0.0);
        builder.chaseSpeed(0.18);
        builder.fleeHealthRatio(0.35);
        builder.fleeSpeed(0.30);
      }
      case SUPPORT -> {
        builder.aggroRadius(10.0);
        builder.chaseSpeed(0.22);
        builder.assistRadius(12.0);
        builder.callForHelpRadius(10.0);
      }
      case SCOUT -> {
        builder.aggroRadius(9.0);
        builder.chaseSpeed(0.34);
        builder.idleWanderRadius(10.0);
      }
      case BERSERKER -> {
        builder.aggroRadius(18.0);
        builder.chaseSpeed(0.36);
        builder.rageHealthRatio(0.75);
        builder.fleeHealthRatio(0.0);
      }
    }
  }
}
