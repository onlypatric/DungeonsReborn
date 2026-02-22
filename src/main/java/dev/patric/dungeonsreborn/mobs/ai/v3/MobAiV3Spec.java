package dev.patric.dungeonsreborn.mobs.ai.v3;

import dev.patric.dungeonsreborn.mobs.MobAiSpec;
import dev.patric.dungeonsreborn.mobs.MobAiGoalSpec;
import dev.patric.dungeonsreborn.mobs.ai.MobAiProfile;

import java.util.List;

public record MobAiV3Spec(
    MobAiProfile profile,
    double aggroRadius,
    double fleeHealthRatio,
    double fleeSpeed,
    double rageHealthRatio,
    double chaseSpeed,
    double kiteMinRange,
    double kiteSpeed,
    double callForHelpRadius,
    double assistRadius,
    long stateTransitionCooldownTicks,
    boolean openDoors,
    boolean breakDoors,
    boolean avoidLava,
    boolean avoidWater,
    boolean avoidPowderSnow,
    boolean avoidCactus,
    boolean avoidSunlight,
    List<MobAiGoalSpec> goals) {

  public static MobAiV3Spec from(MobAiSpec spec) {
    if (spec == null) {
      return new MobAiV3Spec(
          MobAiProfile.NEUTRAL,
          12.0,
          0.0,
          0.35,
          0.0,
          0.25,
          0.0,
          0.3,
          0.0,
          0.0,
          10L,
          false,
          false,
          true,
          false,
          true,
          true,
          false,
          List.of());
    }
    return new MobAiV3Spec(
        spec.profile(),
        spec.aggroRadius(),
        spec.fleeHealthRatio(),
        spec.fleeSpeed(),
        spec.rageHealthRatio(),
        spec.chaseSpeed(),
        spec.kiteMinRange(),
        spec.kiteSpeed(),
        spec.callForHelpRadius(),
        spec.assistRadius(),
        spec.stateTransitionCooldownTicks(),
        spec.openDoors(),
        spec.breakDoors(),
        spec.avoidLava(),
        spec.avoidWater(),
        spec.avoidPowderSnow(),
        spec.avoidCactus(),
        spec.avoidSunlight(),
        spec.goals());
  }
}

