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

  private final ClassService classService;
  private final ProgressionService progression;
  private final ClassSkillRepository repository;
  private final ShopYamlRegistry shops;
  private final Predicate<World> worldAllowed;
  private final Logger logger;

  public ClassSkillService(ClassService classService, ProgressionService progression, ClassSkillRepository repository,
      ShopYamlRegistry shops, Predicate<World> worldAllowed, Logger logger) {
    this.classService = Objects.requireNonNull(classService, "classService");
    this.progression = Objects.requireNonNull(progression, "progression");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.shops = shops;
    this.worldAllowed = worldAllowed;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public Set<String> unlockedNodes(UUID uuid, String classId) {
    if (uuid == null || classId == null) {
      return Set.of();
    }
    return repository.load(uuid, classId);
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
    return unlockedNodes(uuid, classId).contains(nodeId);
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
    Set<String> unlocked = unlockedNodes(player.getUniqueId(), spec.id());
    if (unlocked.contains(node.id())) {
      return new SkillResult(true, Locales.component(player, "messages.classes.skills.alreadyUnlocked"));
    }
    List<String> missing = new ArrayList<>();
    for (String req : requirements(spec, node)) {
      if (!unlocked.contains(req)) {
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
    repository.add(player.getUniqueId(), spec.id(), node.id());
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
    String currentClass = classService.currentClassId(player.getUniqueId());
    if (currentClass == null || !currentClass.equals(spec.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notActiveClass"));
    }
    Set<String> unlocked = unlockedNodes(player.getUniqueId(), spec.id());
    if (!unlocked.contains(node.id())) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notUnlocked"));
    }
    List<String> dependents = new ArrayList<>();
    for (SkillNodeSpec other : spec.skillTreeOrEmpty().nodes()) {
      if (other == null || other.id() == null || other.id().equals(node.id())) {
        continue;
      }
      if (!unlocked.contains(other.id())) {
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
    int tokenCost = Math.max(0, tree.respecTokens());
    int pointCost = Math.max(0, tree.respecPoints());
    PlayerProgression progress = progression.getOrCreate(player.getUniqueId());
    if (pointCost > 0 && progress.skillPoints() < pointCost) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughPoints"));
    }
    if (tokenCost > 0 && !consumeTokens(player, tokenCost)) {
      return new SkillResult(false, Locales.component(player, "messages.classes.skills.respec.notEnoughTokens"));
    }
    repository.remove(player.getUniqueId(), spec.id(), node.id());
    progress.skillTreePoints(Math.max(0, progress.skillTreePoints() - node.cost()));
    int refund = node.cost();
    progress.skillPoints(progress.skillPoints() + refund - pointCost);
    return new SkillResult(true, Locales.component(player, "messages.classes.skills.respec.ok",
        Locales.placeholders("id", node.id())));
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
