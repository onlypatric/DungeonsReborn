package dev.patric.dungeonsreborn.quests;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;

public final class QuestService {
  public record QuestAcceptResult(boolean success, Component message) {
  }

  public enum QuestEntryStatus {
    ACTIVE,
    AVAILABLE,
    COMPLETED,
    COOLDOWN,
    LOCKED
  }

  public record QuestLogEntry(QuestSpec spec, QuestPlayerQuest state, QuestEntryStatus status, Component statusLine) {
  }

  private final QuestYamlRegistry registry;
  private final QuestRepository repository;
  private final ProgressionService progression;
  private final CustomXpService customXpService;
  private final ShopYamlRegistry shopRegistry;
  private final Function<String, ItemStack> itemResolver;
  private final Predicate<World> worldAllowed;
  private final Map<UUID, Map<String, QuestPlayerQuest>> cache = new ConcurrentHashMap<>();

  public QuestService(QuestYamlRegistry registry,
                      QuestRepository repository,
                      ProgressionService progression,
                      CustomXpService customXpService,
                      ShopYamlRegistry shopRegistry,
                      Function<String, ItemStack> itemResolver,
                      Predicate<World> worldAllowed) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.progression = Objects.requireNonNull(progression, "progression");
    this.customXpService = customXpService;
    this.shopRegistry = shopRegistry;
    this.itemResolver = itemResolver;
    this.worldAllowed = worldAllowed;
  }

  public QuestYamlRegistry registry() {
    return registry;
  }

  public void load(Player player) {
    if (player == null) {
      return;
    }
    cache.put(player.getUniqueId(), new HashMap<>(repository.load(player.getUniqueId())));
  }

  public void loadOnlinePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      load(player);
    }
  }

  public void unload(UUID playerId) {
    if (playerId == null) {
      return;
    }
    cache.remove(playerId);
  }

  public QuestAcceptResult accept(Player player, String questId) {
    if (player == null) {
      return new QuestAcceptResult(false, Locales.component(null, "messages.quests.accept.playersOnly"));
    }
    if (!isWorldAllowed(player.getWorld())) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.worldDenied"));
    }
    if (questId == null || questId.isBlank()) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.missingId"));
    }
    QuestSpec spec = registry.quest(questId);
    if (spec == null) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.unknown",
          Locales.placeholders("id", questId)));
    }
    if (!spec.enabled()) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.disabled"));
    }
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    QuestPlayerQuest existing = quests.get(spec.id());
    long now = System.currentTimeMillis();
    if (existing != null && existing.status() == QuestStatus.ACTIVE) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.active"));
    }
    if (existing != null && existing.status() == QuestStatus.COMPLETED) {
      if (!isRepeatable(spec)) {
        return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.completed"));
      }
      if (existing.isCooldownActive(now)) {
        String remaining = formatDuration(existing.cooldownUntil() - now);
        return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.cooldown",
            Locales.placeholders("time", remaining)));
      }
    }
    Component lockReason = requirementsMessage(player, spec);
    if (lockReason != null) {
      return new QuestAcceptResult(false, lockReason);
    }
    QuestPlayerQuest quest = existing == null
        ? new QuestPlayerQuest(spec.id(), QuestStatus.ACTIVE, now, 0L, 0L, new int[0])
        : existing;
    quest.status(QuestStatus.ACTIVE);
    quest.startedAt(now);
    quest.completedAt(0L);
    quest.cooldownUntil(0L);
    quest.resetProgress(spec.objectives().size());
    repository.upsertQuest(player.getUniqueId(), quest);
    quests.put(spec.id(), quest);
    return new QuestAcceptResult(true, Locales.component(player, "messages.quests.accept.ok",
        Locales.placeholders("quest", spec.name())));
  }

  public List<QuestLogEntry> logEntries(Player player) {
    if (player == null) {
      return List.of();
    }
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    long now = System.currentTimeMillis();
    List<QuestLogEntry> entries = new ArrayList<>();
    for (QuestSpec spec : registry.quests().values()) {
      if (!spec.enabled()) {
        QuestPlayerQuest state = quests.get(spec.id());
        if (state == null || state.status() != QuestStatus.ACTIVE) {
          continue;
        }
      }
      QuestPlayerQuest state = quests.get(spec.id());
      QuestEntryStatus status;
      Component statusLine;
      if (state != null && state.status() == QuestStatus.ACTIVE) {
        status = QuestEntryStatus.ACTIVE;
        statusLine = Locales.component(player, "messages.quests.status.active");
      } else if (state != null && state.status() == QuestStatus.COMPLETED) {
        if (state.isCooldownActive(now)) {
          status = QuestEntryStatus.COOLDOWN;
          statusLine = Locales.component(player, "messages.quests.status.cooldown",
              Locales.placeholders("time", formatDuration(state.cooldownUntil() - now)));
        } else if (isRepeatable(spec)) {
          status = QuestEntryStatus.AVAILABLE;
          statusLine = Locales.component(player, "messages.quests.status.available");
        } else {
          status = QuestEntryStatus.COMPLETED;
          statusLine = Locales.component(player, "messages.quests.status.completed");
        }
      } else {
        Component lockReason = requirementsMessage(player, spec);
        if (lockReason == null) {
          status = QuestEntryStatus.AVAILABLE;
          statusLine = Locales.component(player, "messages.quests.status.available");
        } else {
          status = QuestEntryStatus.LOCKED;
          statusLine = lockReason;
        }
      }
      entries.add(new QuestLogEntry(spec, state, status, statusLine));
    }
    entries.sort(Comparator.comparing((QuestLogEntry entry) -> entry.status().ordinal())
        .thenComparing(entry -> entry.spec().name().toLowerCase(Locale.ROOT)));
    return entries;
  }

  public QuestLogEntry entryFor(Player player, QuestSpec spec) {
    if (player == null || spec == null) {
      return null;
    }
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    QuestPlayerQuest state = quests.get(spec.id());
    long now = System.currentTimeMillis();
    QuestEntryStatus status;
    Component statusLine;
    if (state != null && state.status() == QuestStatus.ACTIVE) {
      status = QuestEntryStatus.ACTIVE;
      statusLine = Locales.component(player, "messages.quests.status.active");
    } else if (state != null && state.status() == QuestStatus.COMPLETED) {
      if (state.isCooldownActive(now)) {
        status = QuestEntryStatus.COOLDOWN;
        statusLine = Locales.component(player, "messages.quests.status.cooldown",
            Locales.placeholders("time", formatDuration(state.cooldownUntil() - now)));
      } else if (isRepeatable(spec)) {
        status = QuestEntryStatus.AVAILABLE;
        statusLine = Locales.component(player, "messages.quests.status.available");
      } else {
        status = QuestEntryStatus.COMPLETED;
        statusLine = Locales.component(player, "messages.quests.status.completed");
      }
    } else {
      Component lockReason = requirementsMessage(player, spec);
      if (lockReason == null) {
        status = QuestEntryStatus.AVAILABLE;
        statusLine = Locales.component(player, "messages.quests.status.available");
      } else {
        status = QuestEntryStatus.LOCKED;
        statusLine = lockReason;
      }
    }
    return new QuestLogEntry(spec, state, status, statusLine);
  }

  public List<String> questIdsForGiver(Player player, QuestGiverSpec giver) {
    if (giver == null) {
      return List.of();
    }
    if (giver.mode() != QuestGiverMode.RANDOM_POOL) {
      return giver.questIds();
    }
    List<String> pool = giver.pool();
    if (pool.isEmpty()) {
      return List.of();
    }
    int poolSize = giver.poolSize();
    if (poolSize <= 0 || poolSize >= pool.size()) {
      return pool;
    }
    long seed = 0L;
    if (player != null) {
      UUID id = player.getUniqueId();
      seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }
    seed ^= giver.id().hashCode();
    List<String> copy = new ArrayList<>(pool);
    Collections.shuffle(copy, new Random(seed));
    return copy.subList(0, poolSize);
  }

  public void handleKill(Player player, String mobId, EntityType entityType) {
    if (player == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.kill(mobId, entityType));
  }

  public void handleItemUse(Player player, ItemStack item) {
    if (player == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    if (item == null || item.getType().isAir()) {
      return;
    }
    String itemId = ItemMarkers.getItemId(item);
    handleObjective(player, ObjectiveContext.useItem(itemId, item.getType()));
  }

  public void handleVisit(Player player, Location location) {
    if (player == null || location == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.visit(location));
  }

  public void handleCraft(Player player, String recipeId, List<ItemStack> outputs) {
    if (player == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.craft(recipeId, outputs));
  }

  private void handleObjective(Player player, ObjectiveContext context) {
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    if (quests.isEmpty()) {
      return;
    }
    for (QuestPlayerQuest quest : quests.values()) {
      if (quest == null || quest.status() != QuestStatus.ACTIVE) {
        continue;
      }
      QuestSpec spec = registry.quest(quest.questId());
      if (spec == null) {
        continue;
      }
      boolean progressed = false;
      List<QuestObjectiveSpec> objectives = spec.objectives();
      for (int i = 0; i < objectives.size(); i++) {
        QuestObjectiveSpec objective = objectives.get(i);
        if (!matchesObjective(objective, context)) {
          continue;
        }
        int current = quest.progress(i);
        int required = Math.max(1, objective.count());
        if (current >= required) {
          continue;
        }
        int next = Math.min(required, current + context.increment);
        quest.progress(i, next);
        repository.setProgress(player.getUniqueId(), quest.questId(), i, next);
        sendProgressHint(player, spec, objective, next, required);
        progressed = true;
      }
      if (progressed && isQuestComplete(quest, objectives)) {
        completeQuest(player, quest, spec);
      }
    }
  }

  private boolean matchesObjective(QuestObjectiveSpec objective, ObjectiveContext context) {
    return switch (objective.type()) {
      case KILL_MOB -> {
        if (context.mobId != null && objective.mobId() != null) {
          yield context.mobId.equals(objective.mobId());
        }
        if (objective.entityType() != null && context.entityType != null) {
          yield objective.entityType() == context.entityType;
        }
        yield false;
      }
      case USE_ITEM -> {
        if (objective.itemId() != null && context.itemId != null) {
          yield objective.itemId().equals(context.itemId);
        }
        if (objective.material() != null && context.material != null) {
          yield objective.material() == context.material;
        }
        yield false;
      }
      case VISIT_REGION -> objective.region() != null && context.location != null
          && objective.region().contains(context.location);
      case CRAFT_ITEM -> {
        if (objective.recipeId() != null && context.recipeId != null) {
          yield objective.recipeId().equals(context.recipeId);
        }
        if (objective.itemId() != null && context.outputItemIds.contains(objective.itemId())) {
          yield true;
        }
        if (objective.material() != null && context.outputMaterials.contains(objective.material())) {
          yield true;
        }
        yield false;
      }
    };
  }

  private void completeQuest(Player player, QuestPlayerQuest quest, QuestSpec spec) {
    long now = System.currentTimeMillis();
    quest.status(QuestStatus.COMPLETED);
    quest.completedAt(now);
    if (spec.rotation() != null && spec.rotation() != QuestRotation.NONE) {
      quest.cooldownUntil(nextRotationMillis(spec.rotation()));
    } else if (spec.cooldownSeconds() > 0) {
      quest.cooldownUntil(now + spec.cooldownSeconds() * 1000L);
    } else {
      quest.cooldownUntil(0L);
    }
    repository.upsertQuest(player.getUniqueId(), quest);
    applyRewards(player, spec);
    player.sendMessage(Locales.component(player, "messages.quests.complete.player",
        Locales.placeholders("quest", spec.name())));
  }

  private void applyRewards(Player player, QuestSpec spec) {
    QuestRewards rewards = spec.rewards();
    if (rewards == null) {
      return;
    }
    if (rewards.xp() > 0) {
      if (customXpService != null) {
        customXpService.awardXp(player, rewards.xp());
      } else {
        progression.awardForQuest(player, rewards.xp(), spec.id());
      }
    }
    giveTokens(player, rewards.tokens(), rewards.compressed(), rewards.pallet());
    for (QuestRewardItem reward : rewards.items()) {
      ItemStack item = resolveRewardItem(reward);
      if (item == null) {
        continue;
      }
      int remaining = reward.amount();
      int maxStack = Math.max(1, item.getMaxStackSize());
      while (remaining > 0) {
        int amount = Math.min(maxStack, remaining);
        ItemStack stack = item.clone();
        stack.setAmount(amount);
        giveItemOrDrop(player, stack);
        remaining -= amount;
      }
    }
  }

  private ItemStack resolveRewardItem(QuestRewardItem reward) {
    if (reward == null) {
      return null;
    }
    return switch (reward.type()) {
      case ITEM_ID -> itemResolver == null || reward.itemId() == null ? null : itemResolver.apply(reward.itemId());
      case MATERIAL -> reward.material() == null ? null : new ItemStack(reward.material());
      case ITEMSTACK -> reward.item() == null ? null : reward.item().clone();
    };
  }

  private void giveTokens(Player player, int tokens, int compressed, int pallet) {
    if (shopRegistry == null) {
      return;
    }
    if (pallet > 0) {
      ItemStack stack = shopRegistry.resolveTokenItem("pallet");
      giveTokenStacks(player, stack, pallet);
    }
    if (compressed > 0) {
      ItemStack stack = shopRegistry.resolveTokenItem("compressed");
      giveTokenStacks(player, stack, compressed);
    }
    if (tokens > 0) {
      ItemStack stack = shopRegistry.resolveTokenItem("token");
      giveTokenStacks(player, stack, tokens);
    }
  }

  private void giveTokenStacks(Player player, ItemStack template, int amount) {
    if (player == null || template == null || amount <= 0) {
      return;
    }
    int remaining = amount;
    int maxStack = Math.max(1, template.getMaxStackSize());
    while (remaining > 0) {
      int stackAmount = Math.min(maxStack, remaining);
      ItemStack stack = template.clone();
      stack.setAmount(stackAmount);
      giveItemOrDrop(player, stack);
      remaining -= stackAmount;
    }
  }

  private void giveItemOrDrop(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) {
      return;
    }
    var leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItem(player.getLocation(), stack);
      }
    }
  }

  public List<Component> describeObjectives(Player player, QuestSpec spec, QuestPlayerQuest quest) {
    List<Component> out = new ArrayList<>();
    if (spec == null) {
      return out;
    }
    List<QuestObjectiveSpec> objectives = spec.objectives();
    for (int i = 0; i < objectives.size(); i++) {
      QuestObjectiveSpec objective = objectives.get(i);
      int current = quest == null ? 0 : quest.progress(i);
      int required = Math.max(1, objective.count());
      out.add(GuiMini.mm("<gray>- " + describeObjective(player, objective, current, required) + "</gray>"));
    }
    return out;
  }

  private String describeObjective(Player player, QuestObjectiveSpec objective, int current, int required) {
    return switch (objective.type()) {
      case KILL_MOB -> Locales.text(player, "messages.quests.objective.kill",
          Locales.placeholders("target", targetLabel(player, objective.mobId(), objective.entityType()),
              "current", current, "required", required));
      case USE_ITEM -> Locales.text(player, "messages.quests.objective.use",
          Locales.placeholders("target", targetLabel(player, objective.itemId(), objective.material()),
              "current", current, "required", required));
      case VISIT_REGION -> Locales.text(player, "messages.quests.objective.visit",
          Locales.placeholders("current", current, "required", required));
      case CRAFT_ITEM -> Locales.text(player, "messages.quests.objective.craft",
          Locales.placeholders("target", craftTargetLabel(player, objective),
              "current", current, "required", required));
    };
  }

  private String targetLabel(Player player, String id, Object fallback) {
    if (id != null && !id.isBlank()) {
      return id;
    }
    return fallback == null
        ? Locales.text(player, "messages.quests.objective.target")
        : fallback.toString();
  }

  private String craftTargetLabel(Player player, QuestObjectiveSpec objective) {
    if (objective.recipeId() != null) {
      return Locales.text(player, "messages.quests.objective.recipe",
          Locales.placeholders("id", objective.recipeId()));
    }
    if (objective.itemId() != null) {
      return objective.itemId();
    }
    if (objective.material() != null) {
      return objective.material().name().toLowerCase(Locale.ROOT);
    }
    return Locales.text(player, "messages.quests.objective.item");
  }

  private void sendProgressHint(Player player, QuestSpec spec, QuestObjectiveSpec objective, int current, int required) {
    if (player == null || spec == null || objective == null) {
      return;
    }
    String label = describeObjective(player, objective, current, required);
    player.sendMessage(Locales.component(player, "messages.quests.progress",
        Locales.placeholders("quest", spec.name(), "objective", label)));
  }

  private boolean isQuestComplete(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives) {
    for (int i = 0; i < objectives.size(); i++) {
      QuestObjectiveSpec objective = objectives.get(i);
      int required = Math.max(1, objective.count());
      if (quest.progress(i) < required) {
        return false;
      }
    }
    return true;
  }

  private Map<String, QuestPlayerQuest> questsFor(UUID playerId) {
    if (playerId == null) {
      return Map.of();
    }
    return cache.computeIfAbsent(playerId, repository::load);
  }

  private boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  private Component requirementsMessage(Player player, QuestSpec spec) {
    QuestRequirements req = spec.requirements();
    if (req == null) {
      return null;
    }
    if (req.level() > 0) {
      int level = progression.getOrCreate(player.getUniqueId()).level();
      if (level < req.level()) {
        return Locales.component(player, "messages.quests.require.level",
            Locales.placeholders("level", req.level()));
      }
    }
    if (req.quests() != null && !req.quests().isEmpty()) {
      Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
      List<String> missing = new ArrayList<>();
      for (String questId : req.quests()) {
        QuestPlayerQuest state = quests.get(questId);
        if (state == null || state.status() != QuestStatus.COMPLETED) {
          missing.add(questId);
        }
      }
      if (!missing.isEmpty()) {
        return Locales.component(player, "messages.quests.require.quests",
            Locales.placeholders("quests", String.join(", ", missing)));
      }
    }
    return null;
  }

  private boolean isRepeatable(QuestSpec spec) {
    if (spec == null) {
      return false;
    }
    if (spec.rotation() != null && spec.rotation() != QuestRotation.NONE) {
      return true;
    }
    return spec.cooldownSeconds() > 0;
  }

  private long nextRotationMillis(QuestRotation rotation) {
    ZoneId zone = ZoneId.systemDefault();
    ZonedDateTime now = ZonedDateTime.now(zone);
    return switch (rotation) {
      case DAILY -> now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
      case WEEKLY -> {
        LocalDate next = now.toLocalDate();
        DayOfWeek day = next.getDayOfWeek();
        int daysUntil = (DayOfWeek.MONDAY.getValue() - day.getValue() + 7) % 7;
        if (daysUntil == 0) {
          daysUntil = 7;
        }
        next = next.plusDays(daysUntil);
        yield next.atStartOfDay(zone).toInstant().toEpochMilli();
      }
      case NONE -> 0L;
    };
  }

  private static String formatDuration(long millis) {
    long seconds = Math.max(0L, millis / 1000L);
    long minutes = seconds / 60L;
    long hours = minutes / 60L;
    if (hours > 0) {
      return hours + "h " + (minutes % 60L) + "m";
    }
    if (minutes > 0) {
      return minutes + "m " + (seconds % 60L) + "s";
    }
    return seconds + "s";
  }

  private record ObjectiveContext(
      String mobId,
      EntityType entityType,
      String itemId,
      Material material,
      Location location,
      String recipeId,
      int increment,
      List<String> outputItemIds,
      List<Material> outputMaterials
  ) {
    static ObjectiveContext kill(String mobId, EntityType entityType) {
      return new ObjectiveContext(mobId, entityType, null, null, null, null, 1, List.of(), List.of());
    }

    static ObjectiveContext useItem(String itemId, Material material) {
      return new ObjectiveContext(null, null, itemId, material, null, null, 1, List.of(), List.of());
    }

    static ObjectiveContext visit(Location location) {
      return new ObjectiveContext(null, null, null, null, location, null, 1, List.of(), List.of());
    }

    static ObjectiveContext craft(String recipeId, List<ItemStack> outputs) {
      List<String> itemIds = new ArrayList<>();
      List<Material> materials = new ArrayList<>();
      if (outputs != null) {
        for (ItemStack stack : outputs) {
          if (stack == null || stack.getType().isAir()) {
            continue;
          }
          String itemId = ItemMarkers.getItemId(stack);
          if (itemId != null) {
            itemIds.add(itemId);
          }
          materials.add(stack.getType());
        }
      }
      return new ObjectiveContext(null, null, null, null, null, recipeId, 1, itemIds, materials);
    }
  }
}
