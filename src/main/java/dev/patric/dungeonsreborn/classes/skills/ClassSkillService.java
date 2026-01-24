package dev.patric.dungeonsreborn.classes.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.PlayerProgression;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;

public final class ClassSkillService {
  public record SkillResult(boolean success, Component message) {
  }

  public record ResetPolicy(boolean enabled, int tokenCost, double refundRatio) {
    public ResetPolicy {
      tokenCost = Math.max(0, tokenCost);
      refundRatio = Math.max(0.0, refundRatio);
    }
  }

  public record RespecPolicy(boolean enabled, double tokenMultiplier, double pointMultiplier, double refundRatio,
      int maxTokenCost, int maxPointCost, int maxRefundPoints) {
    public RespecPolicy {
      tokenMultiplier = Math.max(0.0, tokenMultiplier);
      pointMultiplier = Math.max(0.0, pointMultiplier);
      refundRatio = Math.max(0.0, Math.min(1.0, refundRatio));
      maxTokenCost = Math.max(0, maxTokenCost);
      maxPointCost = Math.max(0, maxPointCost);
      maxRefundPoints = Math.max(0, maxRefundPoints);
    }
  }

  private final ClassService classService;
  private final ProgressionService progression;
  private final ClassSkillRepository repository;
  private final ClassSkillPresetRepository presetRepository;
  private final ShopYamlRegistry shops;
  private final Predicate<World> worldAllowed;
  private final Logger logger;
  private final ResetPolicy resetPolicy;
  private final RespecPolicy respecPolicy;

  public ClassSkillService(ClassService classService, ProgressionService progression, ClassSkillRepository repository,
      ClassSkillPresetRepository presetRepository, ShopYamlRegistry shops, Predicate<World> worldAllowed, Logger logger,
      ResetPolicy resetPolicy, RespecPolicy respecPolicy) {
    this.classService = Objects.requireNonNull(classService, "classService");
    this.progression = Objects.requireNonNull(progression, "progression");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.presetRepository = presetRepository;
    this.shops = shops;
    this.worldAllowed = worldAllowed;
    this.logger = Objects.requireNonNull(logger, "logger");
    this.resetPolicy = resetPolicy == null ? new ResetPolicy(false, 0, 1.0) : resetPolicy;
    this.respecPolicy = respecPolicy == null ? new RespecPolicy(true, 1.0, 1.0, 1.0, 0, 0, 0) : respecPolicy;
  }

  public java.util.Map<String, Integer> nodeRanks(UUID uuid, String classId) {
    if (uuid == null || classId == null) {
      return java.util.Map.of();
    }
    return repository.load(uuid, classId);
  }

  public Set<String> unlockedNodes(UUID uuid, String classId) {
    return nodeRanks(uuid, classId).keySet();
  }

  public int rank(UUID uuid, String classId, String nodeId) {
    return repository.rank(uuid, classId, nodeId);
  }

  public List<SkillSynergySpec> activeSynergies(UUID uuid, ClassSpec spec) {
    if (uuid == null || spec == null) {
      return List.of();
    }
    java.util.Map<String, Integer> ranks = nodeRanks(uuid, spec.id());
    if (ranks.isEmpty()) {
      return List.of();
    }
    List<SkillSynergySpec> out = new ArrayList<>();
    for (SkillSynergySpec synergy : spec.skillTreeOrEmpty().synergiesOrEmpty()) {
      if (synergy == null) {
        continue;
      }
      boolean ok = true;
      for (String req : synergy.requiresOrEmpty()) {
        if (req == null || !ranks.containsKey(req)) {
          ok = false;
          break;
        }
      }
      if (ok) {
        out.add(synergy);
      }
    }
    return List.copyOf(out);
  }

  public int skillPoints(Player player) {
    if (player == null) {
      return 0;
    }
    return progression.getOrCreate(player.getUniqueId()).skillPoints();
  }

  public int spentSkillPoints(Player player) {
    if (player == null) {
      return 0;
    }
    return progression.getOrCreate(player.getUniqueId()).skillTreePoints();
  }

  public int totalSkillPoints(Player player) {
    return skillPoints(player) + spentSkillPoints(player);
  }

  public boolean isUnlocked(UUID uuid, String classId, String nodeId) {
    return rank(uuid, classId, nodeId) > 0;
  }

  public List<String> requirements(ClassSpec spec, SkillNodeSpec node) {
    if (spec == null || node == null) {
      return List.of();
    }
    List<String> requires = node.requiresOrEmpty();
    if (!requires.isEmpty()) {
      return requires;
    }
    SkillTreeSpec tree = spec.skillTreeOrEmpty();
    if (tree.edges() == null || tree.edges().isEmpty()) {
      return List.of();
    }
    List<String> derived = new ArrayList<>();
    for (SkillEdgeSpec edge : tree.edges()) {
      if (edge != null && node.id().equals(edge.to())) {
        derived.add(edge.from());
      }
    }
    return derived;
  }

  public SkillResult unlock(Player player, ClassSpec spec, SkillNodeSpec node) {
    if (player == null || spec == null || node == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.invalid"));
    }
    if (!isWorldAllowed(player.getWorld())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.worldDenied"));
    }
    String currentClass = classService.currentClassId(player.getUniqueId());
    if (currentClass == null || !currentClass.equals(spec.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.classRequired"));
    }
    java.util.Map<String, Integer> ranks = nodeRanks(player.getUniqueId(), spec.id());
    int currentRank = ranks.getOrDefault(node.id(), 0);
    int maxRank = node.maxRankOrDefault();
    if (currentRank >= maxRank) {
      return new SkillResult(true, Locales.component(player, "messages.classes.skills.maxRank"));
    }
    List<String> missing = new ArrayList<>();
    for (String req : requirements(spec, node)) {
      if (!ranks.containsKey(req)) {
        missing.add(req);
      }
    }
    if (!missing.isEmpty()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.missingPrereqs",
          Locales.placeholders("nodes", String.join(", ", missing))));
    }
    PlayerProgression progress = progression.getOrCreate(player.getUniqueId());
    if (progress.skillPoints() < node.cost()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.notEnoughPoints"));
    }
    progress.skillPoints(progress.skillPoints() - node.cost());
    progress.skillTreePoints(progress.skillTreePoints() + node.cost());
    repository.setRank(player.getUniqueId(), spec.id(), node.id(), currentRank + 1);
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.unlocked",
        Locales.placeholders("id", node.id())));
  }

  public SkillResult respec(Player player, ClassSpec spec, SkillNodeSpec node) {
    if (player == null || spec == null || node == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.invalid"));
    }
    if (!isWorldAllowed(player.getWorld())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.worldDenied"));
    }
    if (!respecPolicy.enabled()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.disabled"));
    }
    String currentClass = classService.currentClassId(player.getUniqueId());
    if (currentClass == null || !currentClass.equals(spec.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notActiveClass"));
    }
    java.util.Map<String, Integer> ranks = nodeRanks(player.getUniqueId(), spec.id());
    int currentRank = ranks.getOrDefault(node.id(), 0);
    if (currentRank <= 0) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notUnlocked"));
    }
    List<String> dependents = new ArrayList<>();
    for (SkillNodeSpec other : spec.skillTreeOrEmpty().nodes()) {
      if (other == null || other.id() == null || other.id().equals(node.id())) {
        continue;
      }
      if (!ranks.containsKey(other.id())) {
        continue;
      }
      if (requirements(spec, other).contains(node.id())) {
        dependents.add(other.id());
      }
    }
    if (!dependents.isEmpty()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.dependents",
          Locales.placeholders("nodes", String.join(", ", dependents))));
    }
    SkillTreeSpec tree = spec.skillTreeOrEmpty();
    RespecCosts costs = respecCosts(tree);
    int tokenCost = costs.tokenCost();
    int pointCost = costs.pointCost();
    PlayerProgression progress = progression.getOrCreate(player.getUniqueId());
    if (pointCost > 0 && progress.skillPoints() < pointCost) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughPoints"));
    }
    if (tokenCost > 0 && !consumeTokens(player, tokenCost)) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughTokens"));
    }
    repository.remove(player.getUniqueId(), spec.id(), node.id());
    int spent = Math.max(0, node.cost()) * currentRank;
    progress.skillTreePoints(Math.max(0, progress.skillTreePoints() - spent));
    int refund = applyRefund(spent);
    progress.skillPoints(progress.skillPoints() + refund - pointCost);
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.respec.ok",
        Locales.placeholders("id", node.id())));
  }

  public SkillResult resetTree(Player player, ClassSpec spec) {
    if (player == null || spec == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.invalid"));
    }
    if (!resetPolicy.enabled()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.reset.disabled"));
    }
    if (!isWorldAllowed(player.getWorld())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.worldDenied"));
    }
    String currentClass = classService.currentClassId(player.getUniqueId());
    if (currentClass == null || !currentClass.equals(spec.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.reset.notActiveClass"));
    }
    java.util.Map<String, Integer> ranks = nodeRanks(player.getUniqueId(), spec.id());
    if (ranks.isEmpty()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.reset.none"));
    }
    if (resetPolicy.tokenCost() > 0 && !consumeTokens(player, resetPolicy.tokenCost())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughTokens"));
    }
    int totalCost = 0;
    for (SkillNodeSpec node : spec.skillTreeOrEmpty().nodes()) {
      if (node == null || node.id() == null) {
        continue;
      }
      int rank = ranks.getOrDefault(node.id(), 0);
      if (rank <= 0) {
        continue;
      }
      totalCost += Math.max(0, node.cost()) * rank;
    }
    repository.clear(player.getUniqueId(), spec.id());
    PlayerProgression progress = progression.getOrCreate(player.getUniqueId());
    progress.skillTreePoints(Math.max(0, progress.skillTreePoints() - totalCost));
    int refund = (int) Math.floor(totalCost * resetPolicy.refundRatio());
    progress.skillPoints(progress.skillPoints() + refund);
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.reset.ok",
        Locales.placeholders("points", String.valueOf(refund))));
  }

  public List<ClassSkillPreset> presets(UUID uuid, String classId) {
    if (presetRepository == null || uuid == null || classId == null) {
      return List.of();
    }
    return presetRepository.list(uuid, classId);
  }

  public SkillResult savePreset(Player player, ClassSpec spec, String presetId, String name, int maxPerClass) {
    if (player == null || spec == null || presetId == null || presetId.isBlank()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.invalid"));
    }
    if (presetRepository == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.preset.disabled"));
    }
    String currentClass = classService.currentClassId(player.getUniqueId());
    if (currentClass == null || !currentClass.equals(spec.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notActiveClass"));
    }
    List<ClassSkillPreset> existing = presetRepository.list(player.getUniqueId(), spec.id());
    if (maxPerClass > 0 && existing.size() >= maxPerClass) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.preset.limit"));
    }
    java.util.Map<String, Integer> ranks = nodeRanks(player.getUniqueId(), spec.id());
    ClassSkillPreset preset = new ClassSkillPreset(presetId.trim(), name, ranks, System.currentTimeMillis());
    presetRepository.save(player.getUniqueId(), spec.id(), preset);
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.preset.saved",
        Locales.placeholders("preset", preset.id())));
  }

  public SkillResult deletePreset(Player player, ClassSpec spec, String presetId) {
    if (player == null || spec == null || presetId == null || presetId.isBlank()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.invalid"));
    }
    if (presetRepository == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.preset.disabled"));
    }
    presetRepository.delete(player.getUniqueId(), spec.id(), presetId.trim());
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.preset.deleted",
        Locales.placeholders("preset", presetId.trim())));
  }

  public SkillResult applyPreset(Player player, ClassSpec spec, String presetId) {
    if (player == null || spec == null || presetId == null || presetId.isBlank()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.invalid"));
    }
    if (!isWorldAllowed(player.getWorld())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.worldDenied"));
    }
    if (!respecPolicy.enabled()) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.disabled"));
    }
    if (presetRepository == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.preset.disabled"));
    }
    String currentClass = classService.currentClassId(player.getUniqueId());
    if (currentClass == null || !currentClass.equals(spec.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notActiveClass"));
    }
    ClassSkillPreset preset = presetRepository.load(player.getUniqueId(), spec.id(), presetId.trim());
    if (preset == null) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.preset.missing"));
    }
    java.util.Map<String, Integer> targetRanks = normalizePreset(spec, preset.nodes());
    int targetCost = totalCost(spec, targetRanks);
    java.util.Map<String, Integer> currentRanks = nodeRanks(player.getUniqueId(), spec.id());
    int currentCost = totalCost(spec, currentRanks);
    RespecCosts costs = respecCosts(spec.skillTreeOrEmpty());
    PlayerProgression progress = progression.getOrCreate(player.getUniqueId());
    int refund = applyRefund(currentCost);
    int available = progress.skillPoints() + refund - costs.pointCost();
    if (available < targetCost) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughPoints"));
    }
    if (costs.tokenCost() > 0 && !consumeTokens(player, costs.tokenCost())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughTokens"));
    }
    repository.clear(player.getUniqueId(), spec.id());
    for (java.util.Map.Entry<String, Integer> entry : targetRanks.entrySet()) {
      repository.setRank(player.getUniqueId(), spec.id(), entry.getKey(), entry.getValue());
    }
    progress.skillTreePoints(targetCost);
    progress.skillPoints(available - targetCost);
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.preset.applied",
        Locales.placeholders("preset", preset.id())));
  }

  private RespecCosts respecCosts(SkillTreeSpec tree) {
    int tokens = Math.max(0, tree.respecTokens());
    int points = Math.max(0, tree.respecPoints());
    int tokenCost = (int) Math.ceil(tokens * respecPolicy.tokenMultiplier());
    int pointCost = (int) Math.ceil(points * respecPolicy.pointMultiplier());
    if (respecPolicy.maxTokenCost() > 0) {
      tokenCost = Math.min(tokenCost, respecPolicy.maxTokenCost());
    }
    if (respecPolicy.maxPointCost() > 0) {
      pointCost = Math.min(pointCost, respecPolicy.maxPointCost());
    }
    return new RespecCosts(tokenCost, pointCost);
  }

  private int applyRefund(int spent) {
    int refund = (int) Math.floor(Math.max(0, spent) * respecPolicy.refundRatio());
    if (respecPolicy.maxRefundPoints() > 0) {
      refund = Math.min(refund, respecPolicy.maxRefundPoints());
    }
    return refund;
  }

  private java.util.Map<String, Integer> normalizePreset(ClassSpec spec, java.util.Map<String, Integer> raw) {
    if (raw == null || raw.isEmpty()) {
      return java.util.Map.of();
    }
    java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
    java.util.Map<String, SkillNodeSpec> nodeMap = new java.util.HashMap<>();
    for (SkillNodeSpec node : spec.skillTreeOrEmpty().nodes()) {
      if (node != null && node.id() != null) {
        nodeMap.put(node.id(), node);
      }
    }
    for (java.util.Map.Entry<String, Integer> entry : raw.entrySet()) {
      String nodeId = entry.getKey();
      SkillNodeSpec node = nodeMap.get(nodeId);
      if (node == null) {
        continue;
      }
      int rank = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
      int maxRank = node.maxRankOrDefault();
      if (rank > maxRank) {
        rank = maxRank;
      }
      if (rank > 0) {
        out.put(nodeId, rank);
      }
    }
    return java.util.Map.copyOf(out);
  }

  private int totalCost(ClassSpec spec, java.util.Map<String, Integer> ranks) {
    if (spec == null || ranks == null || ranks.isEmpty()) {
      return 0;
    }
    java.util.Map<String, SkillNodeSpec> nodeMap = new java.util.HashMap<>();
    for (SkillNodeSpec node : spec.skillTreeOrEmpty().nodes()) {
      if (node != null && node.id() != null) {
        nodeMap.put(node.id(), node);
      }
    }
    int total = 0;
    for (java.util.Map.Entry<String, Integer> entry : ranks.entrySet()) {
      SkillNodeSpec node = nodeMap.get(entry.getKey());
      if (node == null) {
        continue;
      }
      int rank = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
      if (rank <= 0) {
        continue;
      }
      total += Math.max(0, node.cost()) * rank;
    }
    return total;
  }

  private record RespecCosts(int tokenCost, int pointCost) {
  }

  private boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  private boolean consumeTokens(Player player, int amount) {
    ShopTokenSpec tokenSpec = shops == null ? null : shops.tokenSpec();
    if (player == null || tokenSpec == null || tokenSpec.markerKey() == null) {
      return false;
    }
    int remaining = amount;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && remaining > 0; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!ItemMarkers.has(stack, tokenSpec.markerKey())) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      remaining -= take;
      int left = stack.getAmount() - take;
      if (left <= 0) {
        contents[i] = null;
      } else {
        stack.setAmount(left);
      }
    }
    player.getInventory().setContents(contents);
    if (remaining > 0) {
      logger.warning("[Classes] Token consumption failed for " + player.getName());
      return false;
    }
    return true;
  }
}
