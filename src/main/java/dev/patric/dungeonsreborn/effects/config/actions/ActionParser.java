package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.Map;

import dev.patric.dungeonsreborn.effects.actions.Action;

@FunctionalInterface
public interface ActionParser {
  Action parse(ActionParserContext ctx, Map<String, Object> node, String path, java.util.ArrayDeque<String> includeStack);
}
