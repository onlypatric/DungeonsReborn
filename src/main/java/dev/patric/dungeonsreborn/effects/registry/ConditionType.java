package dev.patric.dungeonsreborn.effects.registry;

import dev.patric.dungeonsreborn.effects.conditions.Condition;

public interface ConditionType extends NodeType {
  Condition build(Params params);
}

