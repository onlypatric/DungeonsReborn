package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.patric.dungeonsreborn.effects.actions.Action;

public final class ActionParsers {
  private static final Map<String, ActionParser> REGISTRY = new HashMap<>();

  static {
    CoreActionParsers.register(REGISTRY);
    VarActionParsers.register(REGISTRY);
    ControlActionParsers.register(REGISTRY);
    MessagingActionParsers.register(REGISTRY);
    CinematicActionParsers.register(REGISTRY);
  }

  private ActionParsers() {
  }

  public static Action parse(ActionParserContext ctx, String type, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    if (type == null) {
      return null;
    }
    ActionParser parser = REGISTRY.get(type.toLowerCase(Locale.ROOT));
    if (parser == null) {
      return null;
    }
    return parser.parse(ctx, node, path, includeStack);
  }
}
