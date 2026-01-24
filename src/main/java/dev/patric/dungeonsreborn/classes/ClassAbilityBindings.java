package dev.patric.dungeonsreborn.classes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.EquipmentSlot;

import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.classes.skills.SkillAbilitySpec;
import dev.patric.dungeonsreborn.classes.skills.SkillAbilityTrigger;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeSpec;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.EventBinding;
import dev.patric.dungeonsreborn.effects.integration.EventTrigger;
import dev.patric.dungeonsreborn.effects.integration.InteractBinding;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.PassiveBinding;

public final class ClassAbilityBindings {
  private final ClassYamlRegistry registry;
  private final ClassService classService;
  private final ClassSkillService skills;
  private final EffectsBindings bindings;
  private final List<String> interactIds = new ArrayList<>();
  private final List<String> passiveIds = new ArrayList<>();
  private final List<String> eventIds = new ArrayList<>();

  public ClassAbilityBindings(ClassYamlRegistry registry, ClassService classService, ClassSkillService skills,
      EffectsBindings bindings) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.classService = Objects.requireNonNull(classService, "classService");
    this.skills = Objects.requireNonNull(skills, "skills");
    this.bindings = Objects.requireNonNull(bindings, "bindings");
  }

  public void reload() {
    clear();
    for (ClassSpec spec : registry.classes().values()) {
      for (SkillNodeSpec node : spec.skillTreeOrEmpty().nodes()) {
        if (node == null) {
          continue;
        }
        SkillAbilitySpec ability = node.abilityOrNull();
        if (ability == null) {
          continue;
        }
        register(spec, node, ability);
      }
    }
  }

  private void clear() {
    for (String id : interactIds) {
      bindings.unregister(id);
    }
    for (String id : passiveIds) {
      bindings.unregisterPassive(id);
    }
    for (String id : eventIds) {
      bindings.unregisterEvent(id);
    }
    interactIds.clear();
    passiveIds.clear();
    eventIds.clear();
  }

  private void register(ClassSpec spec, SkillNodeSpec node, SkillAbilitySpec ability) {
    String bindingId = "class:" + spec.id() + ":" + node.id();
    ItemMatcher matcher = (player, item) -> {
      if (player == null) {
        return false;
      }
      String current = classService.currentClassId(player.getUniqueId());
      if (current == null || !current.equals(spec.id())) {
        return false;
      }
      return skills.rank(player.getUniqueId(), spec.id(), node.id()) > 0;
    };

    if (ability.trigger() == SkillAbilityTrigger.PASSIVE) {
      PassiveBinding binding = new PassiveBinding(
          bindingId,
          Ids.normalize(ability.abilityId()),
          matcher,
          ability.requireSneaking(),
          ability.requiredPermission(),
          ability.periodTicks(),
          EnumSet.of(EquipmentSlot.HAND));
      bindings.registerPassive(binding);
      passiveIds.add(bindingId);
      return;
    }

    if (ability.trigger() == SkillAbilityTrigger.ON_HIT
        || ability.trigger() == SkillAbilityTrigger.ON_KILL
        || ability.trigger() == SkillAbilityTrigger.ON_DODGE
        || ability.trigger() == SkillAbilityTrigger.ON_SPRINT) {
      EventTrigger trigger = switch (ability.trigger()) {
        case ON_HIT -> EventTrigger.ON_HIT;
        case ON_KILL -> EventTrigger.ON_KILL;
        case ON_DODGE -> EventTrigger.ON_DODGE;
        case ON_SPRINT -> EventTrigger.ON_SPRINT;
        default -> EventTrigger.ON_HIT;
      };
      EventBinding binding = new EventBinding(
          bindingId,
          Ids.normalize(ability.abilityId()),
          trigger,
          player -> matcher.matches(player, null),
          ability.requireSneaking(),
          ability.requiredPermission());
      bindings.registerEvent(binding);
      eventIds.add(bindingId);
      return;
    }

    InteractTrigger trigger = ability.trigger() == SkillAbilityTrigger.LEFT_CLICK
        ? InteractTrigger.LEFT_CLICK
        : InteractTrigger.RIGHT_CLICK;
    InteractBinding binding = InteractBinding.builder(bindingId)
        .trigger(trigger)
        .ability(Ids.normalize(ability.abilityId()))
        .item(matcher)
        .requireSneaking(ability.requireSneaking())
        .permission(ability.requiredPermission())
        .cancelEvent(ability.cancelEvent())
        .build();
    bindings.register(binding);
    interactIds.add(bindingId);
  }
}
