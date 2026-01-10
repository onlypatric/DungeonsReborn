package dev.patric.dungeonsreborn.effects.registry;

import dev.patric.dungeonsreborn.effects.actions.Action;

public interface ActionType extends NodeType {
  Action build(Params params);
}

