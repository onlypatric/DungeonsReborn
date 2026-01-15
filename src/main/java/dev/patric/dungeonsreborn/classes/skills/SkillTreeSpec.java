package dev.patric.dungeonsreborn.classes.skills;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SkillTreeSpec(List<SkillNodeSpec> nodes, List<SkillEdgeSpec> edges,
    int respecTokens, int respecPoints) {
  public static SkillTreeSpec empty() {
    return new SkillTreeSpec(List.of(), List.of(), 0, 0);
  }

  public Map<String, SkillNodeSpec> nodeIndex() {
    Map<String, SkillNodeSpec> out = new LinkedHashMap<>();
    if (nodes != null) {
      for (SkillNodeSpec node : nodes) {
        if (node != null && node.id() != null) {
          out.put(node.id(), node);
        }
      }
    }
    return out;
  }
}
