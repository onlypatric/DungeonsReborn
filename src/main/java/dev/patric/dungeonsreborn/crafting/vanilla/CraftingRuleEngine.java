package dev.patric.dungeonsreborn.crafting.vanilla;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingHookSpec;
import dev.patric.dungeonsreborn.crafting.CraftingOutputSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRequirementSpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.quests.QuestService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class CraftingRuleEngine {
  public enum Phase {
    PREVIEW,
    PRE_COMMIT
  }

  public record CheckResult(boolean allowed, Component message) {
    public static CheckResult ok() {
      return new CheckResult(true, null);
    }

    public static CheckResult denied(Component message) {
      return new CheckResult(false, message);
    }
  }

  private final DungeonsRebornPlugin plugin;
  private final CraftingDiscoveryService discovery;
  private final CraftingCooldownStore cooldowns;
  private final Random random = new Random();

  public CraftingRuleEngine(DungeonsRebornPlugin plugin, CraftingDiscoveryService discovery, CraftingCooldownStore cooldowns) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.discovery = Objects.requireNonNull(discovery, "discovery");
    this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
  }

  public CheckResult check(Player player, CraftingRecipeSpec spec, Phase phase) {
    if (player == null || spec == null) {
      return CheckResult.denied(Component.text("Invalid crafting state."));
    }
    if (!discovery.isAvailable(player, spec)) {
      return CheckResult.denied(Component.text("Recipe not unlocked."));
    }
    for (String permission : spec.permissions()) {
      if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
        return CheckResult.denied(Component.text("Missing permission: " + permission));
      }
    }
    for (CraftingRequirementSpec requirement : spec.requirements()) {
      CheckResult result = checkRequirement(player, requirement);
      if (!result.allowed()) {
        return result;
      }
    }
    if (cooldowns.isCoolingDown(player.getUniqueId(), spec.id())) {
      return CheckResult.denied(Component.text("Recipe is on cooldown."));
    }
    CraftingHookSpec hooks = spec.hooks();
    if (hooks != null) {
      CraftingHookSpec.Hook hook = phase == Phase.PREVIEW ? hooks.preview() : hooks.pre();
      CheckResult hookResult = evaluateHook(player, hook, phase == Phase.PRE_COMMIT);
      if (!hookResult.allowed()) {
        return hookResult;
      }
    }
    return CheckResult.ok();
  }

  public void runPostHook(Player player, CraftingRecipeSpec spec) {
    if (player == null || spec == null || spec.hooks() == null) {
      return;
    }
    evaluateHook(player, spec.hooks().post(), true);
  }

  public ItemStack primaryOutput(CraftingRecipeTemplate template) {
    if (template == null) {
      return null;
    }
    List<CraftingOutputSpec> outputs = template.spec().outputs();
    for (int i = 0; i < outputs.size() && i < template.outputTemplates().size(); i++) {
      CraftingOutputSpec output = outputs.get(i);
      ItemStack item = template.outputTemplates().get(i);
      if (output == null || item == null || output.byproduct()) {
        continue;
      }
      return item.clone();
    }
    ItemStack fallback = template.outputTemplate();
    return fallback == null ? null : fallback.clone();
  }

  public List<ItemStack> rollGrantedOutputs(Player player, CraftingRecipeTemplate template) {
    List<ItemStack> granted = new ArrayList<>();
    if (template == null) {
      return granted;
    }
    List<CraftingOutputSpec> specs = template.spec().outputs();
    List<ItemStack> resolved = template.outputTemplates();
    Map<String, List<Integer>> pools = new LinkedHashMap<>();
    for (int i = 0; i < specs.size() && i < resolved.size(); i++) {
      CraftingOutputSpec spec = specs.get(i);
      if (spec == null || spec.pool() == null) {
        continue;
      }
      pools.computeIfAbsent(spec.pool(), key -> new ArrayList<>()).add(i);
    }

    boolean[] selected = new boolean[Math.min(specs.size(), resolved.size())];
    for (int i = 0; i < selected.length; i++) {
      CraftingOutputSpec spec = specs.get(i);
      if (spec != null && spec.pool() == null) {
        selected[i] = true;
      }
    }
    for (List<Integer> poolEntries : pools.values()) {
      int pick = weightedPick(specs, poolEntries);
      if (pick >= 0 && pick < selected.length) {
        selected[pick] = true;
      }
    }

    for (int i = 0; i < selected.length; i++) {
      if (!selected[i]) {
        continue;
      }
      CraftingOutputSpec spec = specs.get(i);
      ItemStack templateItem = resolved.get(i);
      if (spec == null || templateItem == null) {
        continue;
      }
      if (spec.chance() < 1.0 && random.nextDouble() > spec.chance()) {
        continue;
      }
      int amount = applyScale(player, spec, spec.rollAmount(random));
      if (amount <= 0) {
        continue;
      }
      ItemStack clone = templateItem.clone();
      clone.setAmount(amount);
      granted.add(clone);
    }
    return granted;
  }

  private int weightedPick(List<CraftingOutputSpec> outputs, List<Integer> entries) {
    int total = 0;
    for (int index : entries) {
      CraftingOutputSpec spec = outputs.get(index);
      total += spec == null ? 0 : Math.max(1, spec.weight());
    }
    if (total <= 0) {
      return -1;
    }
    int roll = random.nextInt(total);
    int cursor = 0;
    for (int index : entries) {
      CraftingOutputSpec spec = outputs.get(index);
      int weight = spec == null ? 0 : Math.max(1, spec.weight());
      cursor += weight;
      if (roll < cursor) {
        return index;
      }
    }
    return -1;
  }

  private int applyScale(Player player, CraftingOutputSpec spec, int amount) {
    double scaled = amount;
    int add = 0;
    for (CraftingOutputSpec.OutputScaleRule rule : spec.scaleRules()) {
      if (rule == null) {
        continue;
      }
      if (rule.permission() != null && !rule.permission().isBlank() && (player == null || !player.hasPermission(rule.permission()))) {
        continue;
      }
      scaled *= rule.multiplier();
      add += rule.add();
    }
    return Math.max(1, (int) Math.round(scaled) + add);
  }

  private CheckResult checkRequirement(Player player, CraftingRequirementSpec requirement) {
    if (requirement == null || player == null) {
      return CheckResult.ok();
    }
    return switch (requirement.type()) {
      case PERMISSION -> player.hasPermission(requirement.permission())
          ? CheckResult.ok()
          : fail(requirement.message(), "Missing permission.");
      case LEVEL -> checkLevel(player, requirement);
      case CUSTOM_XP -> checkCustomXp(player, requirement);
      case QUEST -> checkQuest(player, requirement);
      case CLASS -> checkClass(player, requirement);
      case REGION -> checkRegion(player, requirement);
    };
  }

  private CheckResult checkLevel(Player player, CraftingRequirementSpec requirement) {
    ProgressionService progression = plugin.progressionService();
    if (progression == null) {
      return fail(requirement.message(), "Progression system unavailable.");
    }
    progression.syncFromPlayer(player);
    int current = progression.getOrCreate(player.getUniqueId()).level();
    return current >= requirement.minLevel()
        ? CheckResult.ok()
        : fail(requirement.message(), "Required level: " + requirement.minLevel());
  }

  private CheckResult checkCustomXp(Player player, CraftingRequirementSpec requirement) {
    CustomXpService service = plugin.customXpService();
    if (service == null) {
      return fail(requirement.message(), "Custom XP unavailable.");
    }
    var profile = service.getOrCreate(player.getUniqueId());
    if (profile.level() < requirement.minCustomLevel()) {
      return fail(requirement.message(), "Required custom level: " + requirement.minCustomLevel());
    }
    return profile.points() >= requirement.minCustomPoints()
        ? CheckResult.ok()
        : fail(requirement.message(), "Required custom XP: " + requirement.minCustomPoints());
  }

  private CheckResult checkQuest(Player player, CraftingRequirementSpec requirement) {
    QuestService quests = plugin.questService();
    if (quests == null) {
      return fail(requirement.message(), "Quest system unavailable.");
    }
    QuestService.QuestEntryStatus current = quests.statusFor(player, requirement.questId());
    boolean ok = switch (requirement.questStatus()) {
      case ACTIVE -> current == QuestService.QuestEntryStatus.ACTIVE;
      case AVAILABLE -> current == QuestService.QuestEntryStatus.AVAILABLE;
      case COMPLETED -> current == QuestService.QuestEntryStatus.COMPLETED;
      case COOLDOWN -> current == QuestService.QuestEntryStatus.COOLDOWN;
      case LOCKED -> current == QuestService.QuestEntryStatus.LOCKED;
      case null -> true;
    };
    return ok ? CheckResult.ok() : fail(requirement.message(), "Quest requirement not met.");
  }

  private CheckResult checkClass(Player player, CraftingRequirementSpec requirement) {
    ClassService classes = plugin.classService();
    if (classes == null) {
      return fail(requirement.message(), "Class system unavailable.");
    }
    String current = classes.currentClassId(player.getUniqueId());
    if (current == null) {
      return fail(requirement.message(), "Class requirement not met.");
    }
    for (String classId : requirement.classIds()) {
      if (classId != null && classId.equalsIgnoreCase(current)) {
        return CheckResult.ok();
      }
    }
    return fail(requirement.message(), "Class requirement not met.");
  }

  private CheckResult checkRegion(Player player, CraftingRequirementSpec requirement) {
    if (requirement.regions().isEmpty()) {
      return CheckResult.ok();
    }
    for (var region : requirement.regions()) {
      if (region != null && region.contains(player.getLocation())) {
        return CheckResult.ok();
      }
    }
    return fail(requirement.message(), "You are not in a valid region.");
  }

  private CheckResult evaluateHook(Player player, CraftingHookSpec.Hook hook, boolean executeAbilities) {
    if (hook == null || player == null) {
      return CheckResult.ok();
    }
    if (executeAbilities) {
      EffectsEngine engine = plugin.effectsEngine();
      if (engine != null) {
        for (String abilityId : hook.abilities()) {
          if (abilityId == null || abilityId.isBlank()) {
            continue;
          }
          try {
            engine.cast(abilityId, player);
          } catch (Exception ignored) {
          }
        }
      }
    }
    if (!hook.deny()) {
      return CheckResult.ok();
    }
    return fail(hook.message(), "Craft blocked by hook.");
  }

  private CheckResult fail(String customMessage, String fallback) {
    String message = (customMessage == null || customMessage.isBlank()) ? fallback : customMessage;
    return CheckResult.denied(Component.text(message));
  }
}
