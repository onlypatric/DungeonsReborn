package dev.patric.dungeonsreborn.quests;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.Random;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.mana.ManaSourcesConfig;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyAssistRules;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.shops.ShopAvailabilitySpec;
import dev.patric.dungeonsreborn.shops.ShopFactionService;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

public final class QuestService {
  public record QuestAcceptResult(boolean success, Component message) {
  }

  public enum QuestEntryStatus {
    ACTIVE,
    AVAILABLE,
    FAILED,
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
  private final ManaProvider manaProvider;
  private final ManaSourcesConfig.QuestSource questSource;
  private final CraftingDiscoveryService craftingDiscovery;
  private final MobRegistry mobRegistry;
  private final PartyService partyService;
  private PartyAssistRules partyAssistRules = new PartyAssistRules(0.0, 0.0, 0.0);
  private QuestRewardShareMode partyRewardMode = QuestRewardShareMode.NONE;
  private boolean partyRewardRequireAssist = true;
  private boolean partyLockRequireLeader = true;
  private boolean partyLockAutoAcceptMembers = true;
  private boolean partyLockShareCompletion = true;
  private ClassService classService;
  private ClassSkillService classSkillService;
  private ShopFactionService factionService;
  private final Map<UUID, Map<String, QuestPlayerQuest>> cache = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, long[]>> progressThrottle = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  public QuestService(QuestYamlRegistry registry,
                      QuestRepository repository,
                      ProgressionService progression,
                      CustomXpService customXpService,
                      ShopYamlRegistry shopRegistry,
                      Function<String, ItemStack> itemResolver,
                      Predicate<World> worldAllowed,
                      ManaProvider manaProvider,
                      ManaSourcesConfig.QuestSource questSource,
                      CraftingDiscoveryService craftingDiscovery,
                      MobRegistry mobRegistry,
                      PartyService partyService) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.progression = Objects.requireNonNull(progression, "progression");
    this.customXpService = customXpService;
    this.shopRegistry = shopRegistry;
    this.itemResolver = itemResolver;
    this.worldAllowed = worldAllowed;
    this.manaProvider = manaProvider;
    this.questSource = questSource == null ? new ManaSourcesConfig.QuestSource(true, ManaProvider.DEFAULT_RESOURCE) : questSource;
    this.craftingDiscovery = craftingDiscovery;
    this.mobRegistry = mobRegistry;
    this.partyService = partyService;
  }

  public void setRequirementServices(ClassService classService, ClassSkillService classSkillService,
      ShopFactionService factionService) {
    this.classService = classService;
    this.classSkillService = classSkillService;
    this.factionService = factionService;
  }

  public void setPartyRules(PartyAssistRules assistRules,
      QuestRewardShareMode rewardMode,
      boolean rewardRequireAssist,
      boolean lockRequireLeader,
      boolean lockAutoAcceptMembers,
      boolean lockShareCompletion) {
    this.partyAssistRules = assistRules == null ? new PartyAssistRules(0.0, 0.0, 0.0) : assistRules;
    this.partyRewardMode = rewardMode == null ? QuestRewardShareMode.NONE : rewardMode;
    this.partyRewardRequireAssist = rewardRequireAssist;
    this.partyLockRequireLeader = lockRequireLeader;
    this.partyLockAutoAcceptMembers = lockAutoAcceptMembers;
    this.partyLockShareCompletion = lockShareCompletion;
  }

  public QuestYamlRegistry registry() {
    return registry;
  }

  public CustomXpService customXpService() {
    return customXpService;
  }

  public ManaProvider manaProvider() {
    return manaProvider;
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
    progressThrottle.remove(playerId);
    lastActive.remove(playerId);
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
    QuestAcceptResult result = acceptInternal(player, spec, false);
    if (result.success() && spec.partyLocked() && partyLockAutoAcceptMembers) {
      autoAcceptPartyMembers(player, spec);
    }
    return result;
  }

  private QuestAcceptResult acceptInternal(Player player, QuestSpec spec, boolean bypassLeaderLock) {
    if (spec == null) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.unknown",
          Locales.placeholders("id", "unknown")));
    }
    if (!spec.enabled()) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.disabled"));
    }
    if (spec.partyLocked() && !bypassLeaderLock) {
      Component lockMessage = partyLockMessage(player, spec);
      if (lockMessage != null) {
        return new QuestAcceptResult(false, lockMessage);
      }
    }
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    QuestPlayerQuest existing = quests.get(spec.id());
    long now = System.currentTimeMillis();
    if (existing != null && existing.status() == QuestStatus.ACTIVE) {
      return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.active"));
    }
    if (existing != null && existing.status() == QuestStatus.FAILED) {
      if (existing.isCooldownActive(now)) {
        String remaining = formatDuration(existing.cooldownUntil() - now);
        return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.cooldown",
            Locales.placeholders("time", remaining)));
      }
    }
    if (existing != null && existing.status() == QuestStatus.COMPLETED) {
      if (!isRepeatable(spec)) {
        return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.completed"));
      }
      RepeatLimitState repeatLimit = repeatLimitState(player, existing, spec, now);
      if (repeatLimit.limited()) {
        return new QuestAcceptResult(false, Locales.component(player, "messages.quests.accept.repeatLimit",
            Locales.placeholders("time", formatDuration(repeatLimit.nextResetAt() - now))));
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
        ? new QuestPlayerQuest(spec.id(), QuestStatus.ACTIVE, now, 0L, 0L, 0, 0, 0L, 0L, new int[0])
        : existing;
    quest.status(QuestStatus.ACTIVE);
    quest.startedAt(now);
    quest.completedAt(0L);
    quest.cooldownUntil(0L);
    quest.resetProgress(spec.objectives().size());
    repository.upsertQuest(player.getUniqueId(), quest);
    quests.put(spec.id(), quest);
    QuestAuditLog.get().record("accept", spec.id(), player.getName());
    return new QuestAcceptResult(true, Locales.component(player, "messages.quests.accept.ok",
        Locales.placeholders("quest", spec.name())));
  }

  private void autoAcceptPartyMembers(Player leader, QuestSpec spec) {
    if (partyService == null || leader == null || spec == null) {
      return;
    }
    Party party = partyService.partyOf(leader);
    if (party == null || party.size() <= 1) {
      return;
    }
    if (partyLockRequireLeader && !party.leader().equals(leader.getUniqueId())) {
      return;
    }
    for (UUID memberId : party.members()) {
      if (memberId == null || memberId.equals(leader.getUniqueId())) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      QuestAcceptResult result = acceptInternal(member, spec, true);
      if (result.success()) {
        member.sendMessage(result.message());
      }
    }
  }

  public List<QuestLogEntry> logEntries(Player player) {
    return logEntries(player, QuestLogFilter.none());
  }

  public List<QuestLogEntry> logEntries(Player player, QuestLogFilter filter) {
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
      } else if (state != null && state.status() == QuestStatus.FAILED) {
        if (state.isCooldownActive(now)) {
          status = QuestEntryStatus.COOLDOWN;
          statusLine = Locales.component(player, "messages.quests.status.cooldown",
              Locales.placeholders("time", formatDuration(state.cooldownUntil() - now)));
        } else {
          status = QuestEntryStatus.FAILED;
          statusLine = Locales.component(player, "messages.quests.status.failed");
        }
      } else if (state != null && state.status() == QuestStatus.COMPLETED) {
        RepeatLimitState repeatLimit = repeatLimitState(player, state, spec, now);
        if (repeatLimit.limited()) {
          status = QuestEntryStatus.COOLDOWN;
          statusLine = Locales.component(player, "messages.quests.status.repeatLimit",
              Locales.placeholders("time", formatDuration(repeatLimit.nextResetAt() - now)));
        } else if (state.isCooldownActive(now)) {
          status = QuestEntryStatus.COOLDOWN;
          statusLine = Locales.component(player, "messages.quests.status.cooldown",
              Locales.placeholders("time", formatDuration(state.cooldownUntil() - now)));
        } else if (isRepeatable(spec)) {
          Component rotationLock = rotationPoolMessage(player, spec);
          if (rotationLock != null) {
            status = QuestEntryStatus.LOCKED;
            statusLine = rotationLock;
          } else {
            status = QuestEntryStatus.AVAILABLE;
            statusLine = Locales.component(player, "messages.quests.status.available");
          }
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
      QuestLogEntry entry = new QuestLogEntry(spec, state, status, statusLine);
      VisibilityState visibility = visibilityState(player, entry);
      if (!visibility.showInLog) {
        continue;
      }
      if (filter != null && !matchesFilter(spec, entry, filter, visibility)) {
        continue;
      }
      entries.add(entry);
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
    } else if (state != null && state.status() == QuestStatus.FAILED) {
      if (state.isCooldownActive(now)) {
        status = QuestEntryStatus.COOLDOWN;
        statusLine = Locales.component(player, "messages.quests.status.cooldown",
            Locales.placeholders("time", formatDuration(state.cooldownUntil() - now)));
      } else {
        status = QuestEntryStatus.FAILED;
        statusLine = Locales.component(player, "messages.quests.status.failed");
      }
    } else if (state != null && state.status() == QuestStatus.COMPLETED) {
      RepeatLimitState repeatLimit = repeatLimitState(player, state, spec, now);
      if (repeatLimit.limited()) {
        status = QuestEntryStatus.COOLDOWN;
        statusLine = Locales.component(player, "messages.quests.status.repeatLimit",
            Locales.placeholders("time", formatDuration(repeatLimit.nextResetAt() - now)));
      } else if (state.isCooldownActive(now)) {
        status = QuestEntryStatus.COOLDOWN;
        statusLine = Locales.component(player, "messages.quests.status.cooldown",
            Locales.placeholders("time", formatDuration(state.cooldownUntil() - now)));
      } else if (isRepeatable(spec)) {
        Component rotationLock = rotationPoolMessage(player, spec);
        if (rotationLock != null) {
          status = QuestEntryStatus.LOCKED;
          statusLine = rotationLock;
        } else {
          Component partyLock = partyLockMessage(player, spec);
          if (partyLock != null) {
            status = QuestEntryStatus.LOCKED;
            statusLine = partyLock;
          } else {
            status = QuestEntryStatus.AVAILABLE;
            statusLine = Locales.component(player, "messages.quests.status.available");
          }
        }
      } else {
        status = QuestEntryStatus.COMPLETED;
        statusLine = Locales.component(player, "messages.quests.status.completed");
      }
    } else {
      Component lockReason = requirementsMessage(player, spec);
      if (lockReason == null) {
        Component partyLock = partyLockMessage(player, spec);
        if (partyLock != null) {
          status = QuestEntryStatus.LOCKED;
          statusLine = partyLock;
        } else {
          status = QuestEntryStatus.AVAILABLE;
          statusLine = Locales.component(player, "messages.quests.status.available");
        }
      } else {
        status = QuestEntryStatus.LOCKED;
        statusLine = lockReason;
      }
    }
    return new QuestLogEntry(spec, state, status, statusLine);
  }

  public boolean isHidden(Player player, QuestLogEntry entry) {
    if (player == null || entry == null) {
      return false;
    }
    return visibilityState(player, entry).hidden;
  }

  public List<String> hintLines(QuestSpec spec) {
    if (spec == null || spec.visibility() == null) {
      return List.of();
    }
    return spec.visibility().hints();
  }

  public QuestEntryStatus statusFor(Player player, String questId) {
    if (player == null || questId == null) {
      return QuestEntryStatus.LOCKED;
    }
    QuestSpec spec = registry.quest(questId);
    if (spec == null) {
      return QuestEntryStatus.LOCKED;
    }
    QuestLogEntry entry = entryFor(player, spec);
    return entry == null ? QuestEntryStatus.LOCKED : entry.status();
  }

  public List<String> questIdsForGiver(Player player, QuestGiverSpec giver) {
    if (giver == null) {
      return List.of();
    }
    List<String> base;
    if (giver.mode() != QuestGiverMode.RANDOM_POOL) {
      base = giver.questIds();
    } else {
      List<String> pool = giver.pool();
      if (pool.isEmpty()) {
        base = List.of();
      } else {
        int poolSize = giver.poolSize();
        if (poolSize <= 0 || poolSize >= pool.size()) {
          base = pool;
        } else {
          long seed = 0L;
          if (player != null) {
            UUID id = player.getUniqueId();
            seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
          }
          seed ^= giver.id().hashCode();
          List<String> copy = new ArrayList<>(pool);
          Collections.shuffle(copy, new Random(seed));
          base = copy.subList(0, poolSize);
        }
      }
    }
    if (base.isEmpty()) {
      return base;
    }
    QuestGiverFilter filter = giver.filter();
    if (player == null || filter == null) {
      return base;
    }
    List<String> filtered = new ArrayList<>();
    for (String questId : base) {
      QuestSpec spec = registry.quest(questId);
      if (spec == null) {
        continue;
      }
      QuestLogEntry entry = entryFor(player, spec);
      QuestEntryStatus status = entry == null ? QuestEntryStatus.LOCKED : entry.status();
      boolean readyToTurnIn = entry != null && isReadyToTurnIn(spec, entry.state());
      VisibilityState visibility = visibilityState(player, entry);
      if (!visibility.showInGiver) {
        continue;
      }
      if (filter.allows(status, readyToTurnIn)) {
        filtered.add(questId);
      }
    }
    return filtered;
  }

  public List<String> giverDialogue(Player player, QuestGiverSpec giver) {
    if (giver == null) {
      return List.of();
    }
    QuestDialogueTree tree = giver.dialogueTree();
    if (tree != null && !tree.nodes().isEmpty()) {
      List<String> lines = dialogueFromTree(player, tree);
      if (!lines.isEmpty()) {
        return lines;
      }
    }
    if (player != null) {
      DialogueState state = dialogueState(player, giver);
      List<String> chosen = switch (state) {
        case TURN_IN -> giver.turnInDialogue();
        case ACTIVE -> giver.activeDialogue();
        case ACCEPT -> giver.acceptDialogue();
        case COMPLETED -> giver.completedDialogue();
        default -> List.of();
      };
      if (chosen != null && !chosen.isEmpty()) {
        return chosen;
      }
    }
    return giver.dialogue();
  }

  public void handleKill(Player player, LivingEntity entity) {
    if (player == null || entity == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    String mobId = MobMarkers.getMobId(entity);
    EntityType entityType = entity.getType();
    String mobTier = null;
    String mobPhase = null;
    if (mobRegistry != null && mobId != null) {
      MobSpec spec = mobRegistry.get(mobId);
      if (spec != null) {
        mobTier = spec.tier();
      }
      mobPhase = mobRegistry.getPhaseId(entity);
    }
    String mobVariant = MobMarkers.getVariant(entity);
    String mobTrait = MobMarkers.getTrait(entity);
    handleObjective(player, ObjectiveContext.kill(player, mobId, entityType, mobTier, mobPhase, mobVariant, mobTrait));
  }

  public void handleItemUse(Player player, ItemStack item) {
    if (player == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    if (item == null || item.getType().isAir()) {
      return;
    }
    String itemId = ItemMarkers.getItemId(item);
    handleObjective(player, ObjectiveContext.useItem(player, itemId, item.getType(), item));
  }

  public void handleVisit(Player player, Location location) {
    if (player == null || location == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.visit(player, location));
  }

  public void handleMovement(Player player, Location location) {
    if (player == null || location == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    markActive(player, System.currentTimeMillis());
    handleFailChecks(player, location);
  }

  public void handleDeath(Player player) {
    if (player == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    if (quests.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    for (QuestPlayerQuest quest : quests.values()) {
      if (quest == null || quest.status() != QuestStatus.ACTIVE) {
        continue;
      }
      QuestSpec spec = registry.quest(quest.questId());
      if (spec == null || spec.fail() == null) {
        continue;
      }
      QuestFailSpec fail = spec.fail();
      if (fail.failOnDeath()) {
        failQuest(player, quest, spec, now, Locales.component(player, "messages.quests.fail.death",
            Locales.placeholders("quest", spec.name())));
      }
    }
  }

  public void handleCraft(Player player, String recipeId, List<ItemStack> outputs) {
    if (player == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.craft(player, recipeId, outputs));
  }

  public void handleBlockBreak(Player player, Material material) {
    if (player == null || material == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.breakBlock(player, material));
  }

  public void handleBlockPlace(Player player, Material material) {
    if (player == null || material == null || !isWorldAllowed(player.getWorld())) {
      return;
    }
    handleObjective(player, ObjectiveContext.placeBlock(player, material));
  }

  private void handleObjective(Player player, ObjectiveContext context) {
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    if (quests.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    markActive(player, now);
    for (QuestPlayerQuest quest : quests.values()) {
      if (quest == null || quest.status() != QuestStatus.ACTIVE) {
        continue;
      }
      QuestSpec spec = registry.quest(quest.questId());
      if (spec == null) {
        continue;
      }
      Location checkLocation = context.location() == null ? player.getLocation() : context.location();
      if (maybeFailQuest(player, quest, spec, checkLocation, now)) {
        continue;
      }
      boolean progressed = false;
      List<QuestObjectiveSpec> objectives = spec.objectives();
      GroupIndex groups = GroupIndex.of(objectives);
      int currentStage = currentStage(quest, objectives, groups);
      for (int i = 0; i < objectives.size(); i++) {
        QuestObjectiveSpec objective = objectives.get(i);
        if (!isObjectiveActive(quest, objectives, groups, currentStage, i)) {
          continue;
        }
        if (isObjectiveExpired(quest, objective, now)) {
          continue;
        }
        if (!matchesObjective(objective, context)) {
          continue;
        }
        int current = quest.progress(i);
        int required = Math.max(1, objective.count());
        if (current >= required) {
          continue;
        }
        int next = Math.min(required, current + context.increment());
        quest.progress(i, next);
        repository.setProgress(player.getUniqueId(), quest.questId(), i, next);
        sendProgressHint(player, spec, quest.questId(), i, objective, next, required, now);
        shareProgressWithParty(player, spec, quest.questId(), i, objective, context, required, now);
        progressed = true;
      }
      if (isQuestComplete(quest, objectives)) {
        Component turnInLock = turnInRequirementsMessage(player, spec);
        if (turnInLock == null) {
          completeQuest(player, quest, spec);
        } else if (progressed) {
          player.sendMessage(turnInLock);
        }
      }
    }
  }

  private boolean matchesObjective(QuestObjectiveSpec objective, ObjectiveContext context) {
    if (!matchesPartyRole(objective.partyRole(), context.player())) {
      return false;
    }
    return switch (objective.type()) {
      case KILL_MOB -> matchesMobObjective(objective, context);
      case USE_ITEM -> matchesItemObjective(objective, context.item());
      case VISIT_REGION -> matchesRegionObjective(objective, context.location());
      case CRAFT_ITEM -> {
        if (objective.recipeId() != null && context.recipeId() != null) {
          yield objective.recipeId().equals(context.recipeId());
        }
        if (objective.itemId() != null && context.outputItemIds().contains(objective.itemId())) {
          yield true;
        }
        if (objective.material() != null && context.outputMaterials().contains(objective.material())) {
          yield true;
        }
        if (matchesCraftItemFilters(objective, context.outputItems())) {
          yield true;
        }
        yield false;
      }
      case BREAK_BLOCK -> context.blockMaterial() != null
          && (objective.material() == null || objective.material() == context.blockMaterial());
      case PLACE_BLOCK -> context.blockMaterial() != null
          && (objective.material() == null || objective.material() == context.blockMaterial());
    };
  }

  private boolean matchesMobObjective(QuestObjectiveSpec objective, ObjectiveContext context) {
    if (objective.mobId() != null && !objective.mobId().equals(context.mobId())) {
      return false;
    }
    if (objective.entityType() != null && objective.entityType() != context.entityType()) {
      return false;
    }
    if (objective.mobTier() != null && (context.mobTier() == null
        || !objective.mobTier().equalsIgnoreCase(context.mobTier()))) {
      return false;
    }
    if (objective.mobPhase() != null && (context.mobPhase() == null
        || !objective.mobPhase().equalsIgnoreCase(context.mobPhase()))) {
      return false;
    }
    if (objective.mobVariant() != null && (context.mobVariant() == null
        || !objective.mobVariant().equalsIgnoreCase(context.mobVariant()))) {
      return false;
    }
    if (objective.mobTrait() != null && (context.mobTrait() == null
        || !objective.mobTrait().equalsIgnoreCase(context.mobTrait()))) {
      return false;
    }
    if (objective.mobTags() != null && !objective.mobTags().isEmpty()) {
      String variant = context.mobVariant() == null ? "" : context.mobVariant().toLowerCase(Locale.ROOT);
      String trait = context.mobTrait() == null ? "" : context.mobTrait().toLowerCase(Locale.ROOT);
      String tier = context.mobTier() == null ? "" : context.mobTier().toLowerCase(Locale.ROOT);
      for (String tag : objective.mobTags()) {
        if (tag == null || tag.isBlank()) {
          continue;
        }
        String needle = tag.toLowerCase(Locale.ROOT);
        if (needle.equals(variant) || needle.equals(trait) || needle.equals(tier)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private boolean matchesItemObjective(QuestObjectiveSpec objective, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    if (objective.itemId() != null) {
      String actualId = ItemMarkers.getItemId(item);
      if (actualId == null || !objective.itemId().equals(actualId)) {
        return false;
      }
    }
    if (objective.material() != null && objective.material() != item.getType()) {
      return false;
    }
    if (!objective.itemTags().isEmpty()) {
      List<String> tags = ItemMarkers.getItemTags(item);
      if (tags.isEmpty()) {
        return false;
      }
      java.util.Set<String> tagSet = new java.util.HashSet<>();
      for (String tag : tags) {
        if (tag != null) {
          tagSet.add(tag.toLowerCase(Locale.ROOT));
        }
      }
      for (String tag : objective.itemTags()) {
        if (!tagSet.contains(tag.toLowerCase(Locale.ROOT))) {
          return false;
        }
      }
    }
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      if (objective.customModelData() != null) {
        if (!meta.hasCustomModelDataComponent()) {
          return false;
        }
        var component = meta.getCustomModelDataComponent();
        List<Float> floats = component.getFloats();
        if (floats.isEmpty() || floats.get(0) == null) {
          return false;
        }
        int cmd = floats.get(0).intValue();
        if (cmd != objective.customModelData()) {
          return false;
        }
      }
      if (!objective.loreContains().isEmpty()) {
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
          return false;
        }
        StringBuilder sb = new StringBuilder();
        for (var line : lore) {
          sb.append(PLAIN.serialize(line)).append('\n');
        }
        String plain = sb.toString().toLowerCase(Locale.ROOT);
        for (String needle : objective.loreContains()) {
          if (!plain.contains(needle.toLowerCase(Locale.ROOT))) {
            return false;
          }
        }
      }
      if (!objective.itemPdc().isEmpty()) {
        if (!matchesPdc(meta.getPersistentDataContainer(), objective.itemPdc())) {
          return false;
        }
      }
    } else if (!objective.itemPdc().isEmpty() || !objective.loreContains().isEmpty()
        || objective.customModelData() != null) {
      return false;
    }
    return true;
  }

  private boolean matchesCraftItemFilters(QuestObjectiveSpec objective, List<ItemStack> outputs) {
    if ((objective.itemTags().isEmpty() && objective.loreContains().isEmpty()
        && objective.itemPdc().isEmpty() && objective.customModelData() == null)
        || outputs == null || outputs.isEmpty()) {
      return false;
    }
    for (ItemStack output : outputs) {
      if (matchesItemObjective(objective, output)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesRegionObjective(QuestObjectiveSpec objective, Location location) {
    if (objective.region() == null || location == null) {
      return false;
    }
    if (!objective.region().contains(location)) {
      return false;
    }
    if (!objective.worlds().isEmpty()) {
      String worldName = location.getWorld() == null ? "" : location.getWorld().getName().toLowerCase(Locale.ROOT);
      boolean matched = false;
      for (String world : objective.worlds()) {
        if (worldName.equals(world.toLowerCase(Locale.ROOT))) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        return false;
      }
    }
    if (!objective.biomes().isEmpty()) {
      String biome = location.getBlock().getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
      boolean matched = false;
      for (String entry : objective.biomes()) {
        if (biome.equals(entry.toLowerCase(Locale.ROOT))) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        return false;
      }
    }
    if (!objective.structures().isEmpty()) {
      if (!matchesStructures(objective.structures(), location, objective.region().radius())) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesStructures(List<String> structures, Location location, double radius) {
    if (structures == null || structures.isEmpty()) {
      return true;
    }
    if (location == null || location.getWorld() == null) {
      return false;
    }
    int searchRadius = Math.max(1, (int) Math.ceil(Math.max(1.0, radius) / 16.0));
    for (String raw : structures) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      org.bukkit.generator.structure.StructureType structure = resolveStructureType(raw);
      if (structure == null) {
        continue;
      }
      org.bukkit.util.StructureSearchResult result = location.getWorld()
          .locateNearestStructure(location, structure, searchRadius, false);
      if (result == null || result.getLocation() == null) {
        continue;
      }
      if (result.getLocation().distanceSquared(location) <= (radius * radius)) {
        return true;
      }
    }
    return false;
  }

  private org.bukkit.generator.structure.StructureType resolveStructureType(String raw) {
    String name = raw.trim().toUpperCase(Locale.ROOT);
    try {
      java.lang.reflect.Field field = org.bukkit.generator.structure.StructureType.class.getField(name);
      Object value = field.get(null);
      if (value instanceof org.bukkit.generator.structure.StructureType structure) {
        return structure;
      }
    } catch (ReflectiveOperationException ex) {
      return null;
    }
    return null;
  }

  private boolean matchesPartyRole(QuestPartyRole role, Player player) {
    if (role == null || role == QuestPartyRole.ANY) {
      return true;
    }
    if (player == null) {
      return false;
    }
    Party party = partyService == null ? null : partyService.partyOf(player);
    if (role == QuestPartyRole.SOLO) {
      return party == null || party.size() <= 1;
    }
    if (party == null) {
      return false;
    }
    boolean leader = party.leader().equals(player.getUniqueId());
    return role == QuestPartyRole.LEADER ? leader : !leader;
  }

  public Component partyLockMessage(Player player, QuestSpec spec) {
    if (spec == null || !spec.partyLocked()) {
      return null;
    }
    if (player == null) {
      return Locales.component(null, "messages.quests.accept.partyRequired");
    }
    Party party = partyService == null ? null : partyService.partyOf(player);
    if (party == null || party.size() <= 1) {
      return Locales.component(player, "messages.quests.accept.partyRequired");
    }
    boolean leader = party.leader().equals(player.getUniqueId());
    if (partyLockRequireLeader && !leader) {
      return Locales.component(player, "messages.quests.accept.partyLeaderOnly");
    }
    return null;
  }

  private void markActive(Player player, long now) {
    if (player == null) {
      return;
    }
    lastActive.put(player.getUniqueId(), now);
  }

  private boolean isActiveRecently(UUID playerId, long now, long idleTimeoutSeconds) {
    if (playerId == null) {
      return false;
    }
    if (idleTimeoutSeconds <= 0L) {
      return true;
    }
    Long last = lastActive.get(playerId);
    if (last == null) {
      return false;
    }
    return now - last <= idleTimeoutSeconds * 1000L;
  }

  private QuestPartyShareSpec resolveShareSpec(QuestSpec spec, QuestObjectiveSpec objective) {
    QuestPartyShareSpec base = spec == null || spec.partyShare() == null
        ? QuestPartyShareSpec.none()
        : spec.partyShare();
    if (objective == null || objective.share() == null) {
      return base;
    }
    return objective.share().apply(base);
  }

  private void shareProgressWithParty(Player actor,
                                      QuestSpec spec,
                                      String questId,
                                      int objectiveIndex,
                                      QuestObjectiveSpec objective,
                                      ObjectiveContext context,
                                      int required,
                                      long now) {
    if (actor == null || spec == null || questId == null || objectiveIndex < 0) {
      return;
    }
    QuestPartyShareSpec share = resolveShareSpec(spec, objective);
    if (share == null || !share.enabled()) {
      return;
    }
    if (partyService == null) {
      return;
    }
    Party party = partyService.partyOf(actor);
    if (party == null || party.size() <= 1) {
      return;
    }
    boolean leader = party.leader().equals(actor.getUniqueId());
    if (share.leaderOnly() && !leader) {
      return;
    }
    Location source = context.location() == null ? actor.getLocation() : context.location();
    double radius = share.radius();
    double radiusSquared = radius * radius;
    int eligibleCount = 0;
    for (UUID memberId : party.members()) {
      if (memberId == null) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      if (!member.getWorld().equals(source.getWorld())) {
        continue;
      }
      if (radius > 0.0 && member.getLocation().distanceSquared(source) > radiusSquared) {
        continue;
      }
      if (!isActiveRecently(memberId, now, share.idleTimeoutSeconds())) {
        continue;
      }
      eligibleCount++;
    }
    int requiredContributors = Math.max(0, share.minContributors());
    if (requiredContributors > 0 && eligibleCount < requiredContributors) {
      return;
    }
    for (UUID memberId : party.members()) {
      if (memberId == null || memberId.equals(actor.getUniqueId())) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      if (!member.getWorld().equals(source.getWorld())) {
        continue;
      }
      if (radius > 0.0 && member.getLocation().distanceSquared(source) > radiusSquared) {
        continue;
      }
      if (!isActiveRecently(memberId, now, share.idleTimeoutSeconds())) {
        continue;
      }
      QuestPlayerQuest memberQuest = questsFor(memberId).get(questId);
      if (memberQuest == null || memberQuest.status() != QuestStatus.ACTIVE) {
        continue;
      }
      int memberCurrent = memberQuest.progress(objectiveIndex);
      if (memberCurrent >= required) {
        continue;
      }
      int memberNext = Math.min(required, memberCurrent + context.increment());
      memberQuest.progress(objectiveIndex, memberNext);
      repository.setProgress(memberId, questId, objectiveIndex, memberNext);
      sendProgressHint(member, spec, questId, objectiveIndex, objective, memberNext, required, now);
      if (isQuestComplete(memberQuest, spec.objectives())) {
        Component turnInLock = turnInRequirementsMessage(member, spec);
        if (turnInLock == null) {
          completeQuest(member, memberQuest, spec);
        }
      }
    }
  }

  private boolean matchesPdc(PersistentDataContainer pdc, Map<String, String> required) {
    if (pdc == null) {
      return false;
    }
    for (var entry : required.entrySet()) {
      NamespacedKey key = NamespacedKey.fromString(entry.getKey());
      if (key == null) {
        key = new NamespacedKey("dungeonsreborn", entry.getKey().toLowerCase(Locale.ROOT));
      }
      String expected = entry.getValue() == null ? "" : entry.getValue();
      String asString = pdc.get(key, PersistentDataType.STRING);
      if (asString != null) {
        if (!asString.equals(expected)) {
          return false;
        }
        continue;
      }
      Integer asInt = pdc.get(key, PersistentDataType.INTEGER);
      if (asInt != null) {
        if (!String.valueOf(asInt).equals(expected)) {
          return false;
        }
        continue;
      }
      Long asLong = pdc.get(key, PersistentDataType.LONG);
      if (asLong != null) {
        if (!String.valueOf(asLong).equals(expected)) {
          return false;
        }
        continue;
      }
      Double asDouble = pdc.get(key, PersistentDataType.DOUBLE);
      if (asDouble != null) {
        if (!String.valueOf(asDouble).equals(expected)) {
          return false;
        }
        continue;
      }
      return false;
    }
    return true;
  }

  private void completeQuest(Player player, QuestPlayerQuest quest, QuestSpec spec) {
    if (player == null || quest == null || spec == null) {
      return;
    }
    Party party = partyService == null ? null : partyService.partyOf(player);
    if (spec.partyLocked() && partyLockShareCompletion && party != null && party.size() > 1) {
      completeQuestWithParty(player, quest, spec, party);
      return;
    }
    finalizeQuest(player, quest, spec, true, true);
  }

  private void completeQuestWithParty(Player actor, QuestPlayerQuest actorQuest, QuestSpec spec, Party party) {
    List<Player> completionTargets = new ArrayList<>();
    for (UUID memberId : party.members()) {
      if (memberId == null) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      QuestPlayerQuest memberQuest = questsFor(memberId).get(spec.id());
      if (memberQuest == null || memberQuest.status() != QuestStatus.ACTIVE) {
        continue;
      }
      Component turnInLock = turnInRequirementsMessage(member, spec);
      if (turnInLock != null) {
        continue;
      }
      fillProgress(memberId, memberQuest, spec);
      completionTargets.add(member);
    }
    if (completionTargets.isEmpty()) {
      finalizeQuest(actor, actorQuest, spec, true, true);
      return;
    }
    if (partyRewardMode == QuestRewardShareMode.NONE) {
      for (Player member : completionTargets) {
        QuestPlayerQuest memberQuest = questsFor(member.getUniqueId()).get(spec.id());
        if (memberQuest != null) {
          finalizeQuest(member, memberQuest, spec, true, true);
        }
      }
      return;
    }
    for (Player member : completionTargets) {
      QuestPlayerQuest memberQuest = questsFor(member.getUniqueId()).get(spec.id());
      if (memberQuest != null) {
        finalizeQuest(member, memberQuest, spec, false, true);
      }
    }
    List<Player> rewardRecipients = resolvePartyRewardRecipients(party, actor);
    if (rewardRecipients.isEmpty()) {
      rewardRecipients = List.of(actor);
    }
    switch (partyRewardMode) {
      case LEADER_ONLY -> {
        Player leader = party.leader() == null ? null : Bukkit.getPlayer(party.leader());
        Player target = leader != null && rewardRecipients.contains(leader)
            ? leader
            : rewardRecipients.get(0);
        applyRewardsScaled(target, spec, 1.0);
        broadcastPartyReward(party, Locales.component(actor, "messages.quests.complete.party",
            Locales.placeholders("quest", spec.name(), "player", target.getName())));
      }
      case ROLL -> {
        Player winner = rewardRecipients.get(ThreadLocalRandom.current().nextInt(rewardRecipients.size()));
        applyRewardsScaled(winner, spec, 1.0);
        broadcastPartyReward(party, Locales.component(actor, "messages.quests.rewards.rollWinner",
            Locales.placeholders("quest", spec.name(), "player", winner.getName())));
      }
      case SPLIT -> {
        double share = rewardRecipients.isEmpty() ? 1.0 : (1.0 / rewardRecipients.size());
        for (Player member : rewardRecipients) {
          applyRewardsScaled(member, spec, share);
        }
        broadcastPartyReward(party, Locales.component(actor, "messages.quests.rewards.split",
            Locales.placeholders("quest", spec.name(), "count", String.valueOf(rewardRecipients.size()))));
      }
      case NONE -> {
      }
    }
  }

  private void finalizeQuest(Player player, QuestPlayerQuest quest, QuestSpec spec, boolean grantRewards,
      boolean notify) {
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
    applyRepeatCompletion(quest, spec.repeat(), now);
    repository.upsertQuest(player.getUniqueId(), quest);
    QuestAuditLog.get().record("complete", spec.id(), player.getName());
    if (grantRewards) {
      applyRewardsScaled(player, spec, 1.0);
    }
    if (craftingDiscovery != null) {
      craftingDiscovery.unlockFromQuest(player, spec.id());
    }
    if (notify) {
      player.sendMessage(Locales.component(player, "messages.quests.complete.player",
          Locales.placeholders("quest", spec.name())));
    }
  }

  private void fillProgress(UUID playerId, QuestPlayerQuest quest, QuestSpec spec) {
    if (playerId == null || quest == null || spec == null) {
      return;
    }
    int size = spec.objectives().size();
    for (int i = 0; i < size; i++) {
      int required = Math.max(1, spec.objectives().get(i).count());
      if (quest.progress(i) < required) {
        quest.progress(i, required);
        repository.setProgress(playerId, spec.id(), i, required);
      }
    }
  }

  private List<Player> resolvePartyRewardRecipients(Party party, Player actor) {
    List<Player> out = new ArrayList<>();
    if (party == null) {
      if (actor != null) {
        out.add(actor);
      }
      return out;
    }
    Location center = actor == null ? null : actor.getLocation();
    double radius = assistRadius(party);
    double radiusSquared = radius * radius;
    for (UUID memberId : party.members()) {
      if (memberId == null) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      if (partyRewardRequireAssist && center != null && radius > 0.0) {
        if (!member.getWorld().equals(center.getWorld())) {
          continue;
        }
        if (member.getLocation().distanceSquared(center) > radiusSquared) {
          continue;
        }
      }
      out.add(member);
    }
    return out;
  }

  private double assistRadius(Party party) {
    if (partyAssistRules == null || party == null) {
      return 0.0;
    }
    double radius = partyAssistRules.baseRadius();
    if (party.size() > 1) {
      radius += partyAssistRules.scalePerMember() * Math.max(0, party.size() - 1);
    }
    if (partyAssistRules.maxRadius() > 0.0) {
      radius = Math.min(radius, partyAssistRules.maxRadius());
    }
    return Math.max(0.0, radius);
  }

  private void broadcastPartyReward(Party party, Component message) {
    if (party == null || message == null) {
      return;
    }
    for (UUID memberId : party.members()) {
      if (memberId == null) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member != null) {
        member.sendMessage(message);
      }
    }
  }

  private void applyRewardsScaled(Player player, QuestSpec spec, double shareMultiplier) {
    QuestRewards rewards = spec.rewards();
    if (rewards == null) {
      return;
    }
    QuestRewardScaling scaling = rewards.scaling() == null ? QuestRewardScaling.none() : rewards.scaling();
    double multiplier = rewardMultiplier(player, scaling) * Math.max(0.0, shareMultiplier);
    int xpReward = scaleAmount(rewards.xp(), multiplier);
    if (xpReward > 0) {
      if (customXpService != null) {
        customXpService.awardXp(player, xpReward);
      } else {
        progression.awardForQuest(player, xpReward, spec.id());
      }
    }
    giveTokens(player,
        scaleAmount(rewards.tokens(), multiplier),
        scaleAmount(rewards.compressed(), multiplier),
        scaleAmount(rewards.pallet(), multiplier));
    if (manaProvider != null && questSource.enabled()) {
      if (rewards.mana() > 0.0) {
        addResource(player, questSource.resourceId(), scaleAmount(rewards.mana(), multiplier));
      }
      if (rewards.resources() != null && !rewards.resources().isEmpty()) {
        for (var entry : rewards.resources().entrySet()) {
          if (entry.getKey() == null || entry.getKey().isBlank()) {
            continue;
          }
          double amount = entry.getValue() == null ? 0.0 : scaleAmount(entry.getValue(), multiplier);
          if (amount > 0.0) {
            addResource(player, entry.getKey().trim(), amount);
          }
        }
      }
    }
    for (QuestRewardItem reward : rewards.items()) {
      ItemStack item = resolveRewardItem(reward);
      if (item == null) {
        continue;
      }
      int remaining = reward.amount();
      if (scaling.applyToItems()) {
        remaining = scaleAmount(remaining, multiplier);
      }
      int maxStack = Math.max(1, item.getMaxStackSize());
      while (remaining > 0) {
        int amount = Math.min(maxStack, remaining);
        ItemStack stack = item.clone();
        stack.setAmount(amount);
        giveItemOrDrop(player, stack);
        remaining -= amount;
      }
    }
    if (rewards.entries() != null && !rewards.entries().isEmpty()) {
      for (QuestRewardEntry entry : rewards.entries()) {
        if (entry == null) {
          continue;
        }
        if (entry.chance() < 1.0 && ThreadLocalRandom.current().nextDouble() > entry.chance()) {
          continue;
        }
        applyRewardEntry(player, entry, multiplier, scaling, spec.id());
      }
    }
    if (rewards.pools() != null && !rewards.pools().isEmpty()) {
      for (QuestRewardPool pool : rewards.pools()) {
        rollRewardPool(player, pool, multiplier, scaling, spec.id());
      }
    }
  }

  private void rollRewardPool(Player player, QuestRewardPool pool, double multiplier, QuestRewardScaling scaling,
      String questId) {
    if (pool == null || pool.entries().isEmpty()) {
      return;
    }
    List<QuestRewardEntry> available = new ArrayList<>(pool.entries());
    for (int i = 0; i < pool.rolls(); i++) {
      QuestRewardEntry pick = pickRewardEntry(available);
      if (pick == null) {
        continue;
      }
      applyRewardEntry(player, pick, multiplier, scaling, questId);
      if (pool.unique()) {
        available.remove(pick);
        if (available.isEmpty()) {
          return;
        }
      }
    }
  }

  private QuestRewardEntry pickRewardEntry(List<QuestRewardEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return null;
    }
    int totalWeight = 0;
    List<QuestRewardEntry> candidates = new ArrayList<>();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (QuestRewardEntry entry : entries) {
      if (entry == null) {
        continue;
      }
      double chance = entry.chance();
      if (chance <= 0.0) {
        continue;
      }
      if (chance < 1.0 && random.nextDouble() > chance) {
        continue;
      }
      totalWeight += Math.max(1, entry.weight());
      candidates.add(entry);
    }
    if (totalWeight <= 0 || candidates.isEmpty()) {
      return null;
    }
    int roll = random.nextInt(totalWeight);
    for (QuestRewardEntry entry : candidates) {
      roll -= Math.max(1, entry.weight());
      if (roll < 0) {
        return entry;
      }
    }
    return candidates.get(candidates.size() - 1);
  }

  private void applyRewardEntry(Player player, QuestRewardEntry entry, double multiplier, QuestRewardScaling scaling,
      String questId) {
    if (entry == null || entry.type() == null) {
      return;
    }
    switch (entry.type()) {
      case XP -> {
        int amount = scaleAmount((int) Math.round(entry.amount()), multiplier);
        if (amount > 0) {
          if (customXpService != null) {
            customXpService.awardXp(player, amount);
          } else {
            progression.awardForQuest(player, amount, questId == null ? "quest:reward" : questId);
          }
        }
      }
      case TOKENS -> giveTokens(player, scaleAmount((int) Math.round(entry.amount()), multiplier), 0, 0);
      case COMPRESSED -> giveTokens(player, 0, scaleAmount((int) Math.round(entry.amount()), multiplier), 0);
      case PALLET -> giveTokens(player, 0, 0, scaleAmount((int) Math.round(entry.amount()), multiplier));
      case MANA -> {
        if (manaProvider != null && questSource.enabled()) {
          double amount = scaleAmount(entry.amount(), multiplier);
          if (amount > 0.0) {
            addResource(player, questSource.resourceId(), amount);
          }
        }
      }
      case RESOURCE -> {
        if (entry.id() == null || entry.id().isBlank()) {
          return;
        }
        double amount = scaleAmount(entry.amount(), multiplier);
        if (amount > 0.0) {
          addResource(player, entry.id().trim(), amount);
        }
      }
      case ITEM -> {
        QuestRewardItem reward = entry.item();
        if (reward == null) {
          return;
        }
        ItemStack item = resolveRewardItem(reward);
        if (item == null) {
          return;
        }
        int remaining = reward.amount();
        if (scaling.applyToItems()) {
          remaining = scaleAmount(remaining, multiplier);
        }
        int maxStack = Math.max(1, item.getMaxStackSize());
        while (remaining > 0) {
          int amount = Math.min(maxStack, remaining);
          ItemStack stack = item.clone();
          stack.setAmount(amount);
          giveItemOrDrop(player, stack);
          remaining -= amount;
        }
      }
      case CURRENCY -> {
        if (shopRegistry == null || entry.id() == null || entry.id().isBlank()) {
          return;
        }
        var currency = shopRegistry.currency(entry.id());
        int amount = scaleAmount((int) Math.round(entry.amount()), multiplier);
        if (currency != null && amount > 0) {
          giveTokenStacks(player, currency.item(), amount);
        }
      }
      case TITLE -> {
        QuestRewardTitle title = entry.title();
        if (title == null) {
          return;
        }
        Component titleText = title.title() == null ? Component.empty() : GuiMini.mm(title.title());
        Component subtitleText = title.subtitle() == null ? Component.empty() : GuiMini.mm(title.subtitle());
        Title.Times times = Title.Times.times(
            java.time.Duration.ofMillis(title.fadeInTicks() * 50L),
            java.time.Duration.ofMillis(title.stayTicks() * 50L),
            java.time.Duration.ofMillis(title.fadeOutTicks() * 50L));
        player.showTitle(Title.title(titleText, subtitleText, times));
      }
      case BUFF -> {
        QuestRewardBuff buff = entry.buff();
        if (buff == null || buff.durationTicks() <= 0) {
          return;
        }
        player.addPotionEffect(new PotionEffect(buff.type(), buff.durationTicks(), buff.amplifier(),
            buff.ambient(), buff.particles(), buff.icon()));
      }
      case UNLOCK_QUEST -> {
        if (entry.id() == null || entry.id().isBlank()) {
          return;
        }
        accept(player, entry.id());
      }
      case FACTION_REP -> {
        if (factionService == null || entry.id() == null || entry.id().isBlank()) {
          return;
        }
        int amount = Math.max(0, (int) Math.round(entry.amount()));
        if (amount > 0) {
          factionService.addReputation(player.getUniqueId(), entry.id(), amount);
        }
      }
    }
  }

  private double rewardMultiplier(Player player, QuestRewardScaling scaling) {
    if (player == null || scaling == null) {
      return 1.0;
    }
    int level = progression.getOrCreate(player.getUniqueId()).level();
    int partySize = 1;
    if (partyService != null) {
      Party party = partyService.partyOf(player);
      if (party != null) {
        partySize = Math.max(1, party.size());
      }
    }
    double multiplier = 1.0;
    if (scaling.levelFactor() != 0.0) {
      multiplier += scaling.levelFactor() * Math.max(0, level - 1);
    }
    if (scaling.partyFactor() != 0.0) {
      multiplier += scaling.partyFactor() * Math.max(0, partySize - 1);
    }
    double min = scaling.minMultiplier() <= 0.0 ? 1.0 : scaling.minMultiplier();
    double max = scaling.maxMultiplier() < min ? min : scaling.maxMultiplier();
    return Math.max(min, Math.min(max, multiplier));
  }

  private int scaleAmount(int base, double multiplier) {
    if (base <= 0 || multiplier <= 0.0 || !Double.isFinite(multiplier)) {
      return 0;
    }
    long value = Math.round(base * multiplier);
    if (value > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) value;
  }

  private double scaleAmount(double base, double multiplier) {
    if (base <= 0.0 || multiplier <= 0.0 || !Double.isFinite(multiplier)) {
      return 0.0;
    }
    return base * multiplier;
  }

  private void addResource(Player player, String resourceId, double amount) {
    if (manaProvider == null || player == null || resourceId == null || resourceId.isBlank()) {
      return;
    }
    if (!Double.isFinite(amount) || amount <= 0.0) {
      return;
    }
    double max = manaProvider.getMax(player, resourceId);
    if (max <= 0.0) {
      return;
    }
    double current = manaProvider.get(player, resourceId);
    manaProvider.set(player, resourceId, Math.min(max, current + amount));
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
      case BREAK_BLOCK -> Locales.text(player, "messages.quests.objective.break",
          Locales.placeholders("target", targetLabel(player, null, objective.material()),
              "current", current, "required", required));
      case PLACE_BLOCK -> Locales.text(player, "messages.quests.objective.place",
          Locales.placeholders("target", targetLabel(player, null, objective.material()),
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

  private void sendProgressHint(Player player, QuestSpec spec, String questId, int objectiveIndex,
      QuestObjectiveSpec objective, int current, int required, long now) {
    if (player == null || spec == null || objective == null) {
      return;
    }
    if (!shouldNotifyProgress(player, spec, questId, objectiveIndex, now)) {
      return;
    }
    String label = describeObjective(player, objective, current, required);
    player.sendMessage(Locales.component(player, "messages.quests.progress",
        Locales.placeholders("quest", spec.name(), "objective", label)));
  }

  private boolean shouldNotifyProgress(Player player, QuestSpec spec, String questId, int objectiveIndex, long now) {
    if (player == null || spec == null || questId == null || objectiveIndex < 0) {
      return true;
    }
    long throttleSeconds = spec.progressThrottleSeconds();
    if (throttleSeconds <= 0L) {
      return true;
    }
    long throttleMillis = throttleSeconds * 1000L;
    Map<String, long[]> playerMap = progressThrottle.computeIfAbsent(player.getUniqueId(),
        id -> new ConcurrentHashMap<>());
    long[] lastTimes = playerMap.get(questId);
    int size = spec.objectives().size();
    if (lastTimes == null) {
      lastTimes = new long[size];
      playerMap.put(questId, lastTimes);
    } else if (objectiveIndex >= lastTimes.length || lastTimes.length < size) {
      lastTimes = Arrays.copyOf(lastTimes, size);
      playerMap.put(questId, lastTimes);
    }
    long last = lastTimes[objectiveIndex];
    if (last > 0L && now - last < throttleMillis) {
      return false;
    }
    lastTimes[objectiveIndex] = now;
    return true;
  }

  private boolean isQuestComplete(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives) {
    GroupIndex groups = GroupIndex.of(objectives);
    for (int i = 0; i < objectives.size(); i++) {
      QuestObjectiveSpec objective = objectives.get(i);
      if (objective.groupId() != null && !objective.groupId().isBlank()) {
        continue;
      }
      if (objective.optional()) {
        continue;
      }
      int required = Math.max(1, objective.count());
      if (quest.progress(i) < required) {
        return false;
      }
    }
    for (GroupState group : groups.groups().values()) {
      if (!group.hasRequiredMembers(objectives)) {
        continue;
      }
      if (!group.isComplete(quest, objectives)) {
        return false;
      }
    }
    return true;
  }

  private boolean isReadyToTurnIn(QuestSpec spec, QuestPlayerQuest quest) {
    if (spec == null || quest == null || quest.status() != QuestStatus.ACTIVE) {
      return false;
    }
    return isQuestComplete(quest, spec.objectives());
  }

  private DialogueState dialogueState(Player player, QuestGiverSpec giver) {
    if (player == null || giver == null) {
      return DialogueState.DEFAULT;
    }
    List<String> questIds = questIdsForGiver(player, giver);
    boolean anyQuest = false;
    boolean anyTurnIn = false;
    boolean anyActive = false;
    boolean anyAvailable = false;
    boolean anyIncomplete = false;
    boolean anyCompleted = false;
    for (String questId : questIds) {
      QuestSpec spec = registry.quest(questId);
      if (spec == null) {
        continue;
      }
      anyQuest = true;
      QuestLogEntry entry = entryFor(player, spec);
      if (entry == null) {
        anyIncomplete = true;
        continue;
      }
      if (isReadyToTurnIn(spec, entry.state())) {
        anyTurnIn = true;
        anyActive = true;
        continue;
      }
      switch (entry.status()) {
        case ACTIVE -> {
          anyActive = true;
          anyIncomplete = true;
        }
        case AVAILABLE -> {
          anyAvailable = true;
          anyIncomplete = true;
        }
        case COMPLETED -> anyCompleted = true;
        default -> anyIncomplete = true;
      }
    }
    if (anyTurnIn) {
      return DialogueState.TURN_IN;
    }
    if (anyActive) {
      return DialogueState.ACTIVE;
    }
    if (anyAvailable) {
      return DialogueState.ACCEPT;
    }
    if (anyQuest && anyCompleted && !anyIncomplete) {
      return DialogueState.COMPLETED;
    }
    return DialogueState.DEFAULT;
  }

  private List<String> dialogueFromTree(Player player, QuestDialogueTree tree) {
    QuestDialogueNode node = resolveDialogueNode(player, tree);
    if (node == null) {
      return List.of();
    }
    List<String> lines = new ArrayList<>(node.lines());
    for (QuestDialogueChoice choice : node.choices()) {
      if (!conditionsMatch(player, choice.conditions())) {
        continue;
      }
      String text = choice.text();
      if (text == null || text.isBlank()) {
        continue;
      }
      lines.add("> " + text);
    }
    return lines;
  }

  private QuestDialogueNode resolveDialogueNode(Player player, QuestDialogueTree tree) {
    if (tree == null || tree.nodes().isEmpty()) {
      return null;
    }
    if (tree.start() != null) {
      QuestDialogueNode startNode = tree.nodes().get(tree.start());
      if (startNode != null && conditionsMatch(player, startNode.conditions())) {
        return startNode;
      }
    }
    for (QuestDialogueNode node : tree.nodes().values()) {
      if (conditionsMatch(player, node.conditions())) {
        return node;
      }
    }
    return null;
  }

  private boolean conditionsMatch(Player player, List<QuestDialogueCondition> conditions) {
    if (conditions == null || conditions.isEmpty()) {
      return true;
    }
    for (QuestDialogueCondition condition : conditions) {
      if (!conditionMatches(player, condition)) {
        return false;
      }
    }
    return true;
  }

  private boolean conditionMatches(Player player, QuestDialogueCondition condition) {
    if (condition == null) {
      return true;
    }
    String questId = condition.questId();
    if (questId == null || questId.isBlank()) {
      return true;
    }
    QuestSpec spec = registry.quest(questId);
    if (spec == null) {
      return false;
    }
    QuestRequiredStatus required = condition.required();
    if (required == null) {
      return true;
    }
    QuestLogEntry entry = entryFor(player, spec);
    QuestEntryStatus status = entry == null ? QuestEntryStatus.LOCKED : entry.status();
    boolean readyToTurnIn = entry != null && isReadyToTurnIn(spec, entry.state());
    return switch (required) {
      case TURNIN -> readyToTurnIn;
      case ACTIVE -> status == QuestEntryStatus.ACTIVE;
      case AVAILABLE -> status == QuestEntryStatus.AVAILABLE;
      case COMPLETED -> status == QuestEntryStatus.COMPLETED;
      case FAILED -> status == QuestEntryStatus.FAILED;
      case COOLDOWN -> status == QuestEntryStatus.COOLDOWN;
      case LOCKED -> status == QuestEntryStatus.LOCKED;
    };
  }

  private boolean matchesFilter(QuestSpec spec, QuestLogEntry entry, QuestLogFilter filter, VisibilityState visibility) {
    if (spec == null || filter == null) {
      return true;
    }
    if (filter.category() != null && !filter.category().isBlank()) {
      boolean match = false;
      for (String category : spec.categories()) {
        if (category != null && category.equalsIgnoreCase(filter.category())) {
          match = true;
          break;
        }
      }
      if (!match) {
        return false;
      }
    }
    if (filter.tier() != null && !filter.tier().isBlank()) {
      String tier = spec.tier();
      if (tier == null || !tier.equalsIgnoreCase(filter.tier())) {
        return false;
      }
    }
    QuestRewardTag reward = filter.reward();
    if (reward != null && reward != QuestRewardTag.ANY) {
      if (!rewardTagsFor(spec.rewards()).contains(reward)) {
        return false;
      }
    }
    String query = filter.query();
    if (query == null || query.isBlank()) {
      return true;
    }
    String needle = query.toLowerCase(Locale.ROOT);
    if (spec.id().toLowerCase(Locale.ROOT).contains(needle)) {
      return true;
    }
    if (!visibility.hidden && spec.name().toLowerCase(Locale.ROOT).contains(needle)) {
      return true;
    }
    for (String category : spec.categories()) {
      if (category != null && category.toLowerCase(Locale.ROOT).contains(needle)) {
        return true;
      }
    }
    for (String tag : spec.tags()) {
      if (tag != null && tag.toLowerCase(Locale.ROOT).contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private EnumSet<QuestRewardTag> rewardTagsFor(QuestRewards rewards) {
    EnumSet<QuestRewardTag> tags = EnumSet.noneOf(QuestRewardTag.class);
    if (rewards == null) {
      return tags;
    }
    if (rewards.xp() > 0) {
      tags.add(QuestRewardTag.XP);
    }
    if (rewards.tokens() > 0) {
      tags.add(QuestRewardTag.TOKENS);
    }
    if (rewards.compressed() > 0) {
      tags.add(QuestRewardTag.COMPRESSED);
    }
    if (rewards.pallet() > 0) {
      tags.add(QuestRewardTag.PALLET);
    }
    if (rewards.mana() > 0.0) {
      tags.add(QuestRewardTag.MANA);
    }
    if (rewards.resources() != null && !rewards.resources().isEmpty()) {
      tags.add(QuestRewardTag.RESOURCES);
    }
    if (rewards.items() != null && !rewards.items().isEmpty()) {
      tags.add(QuestRewardTag.ITEMS);
    }
    if (rewards.entries() != null && !rewards.entries().isEmpty()) {
      tags.add(QuestRewardTag.ENTRIES);
    }
    if (rewards.pools() != null && !rewards.pools().isEmpty()) {
      tags.add(QuestRewardTag.POOLS);
    }
    return tags;
  }

  private VisibilityState visibilityState(Player player, QuestLogEntry entry) {
    if (entry == null) {
      return VisibilityState.visible();
    }
    QuestSpec spec = entry.spec();
    QuestVisibilitySpec visibility = spec.visibility();
    if (visibility == null || !visibility.hidden()) {
      return VisibilityState.visible();
    }
    boolean revealed = isRevealed(player, entry, visibility);
    boolean showInLog = visibility.showInLog();
    boolean showInGiver = visibility.showInGiver();
    return new VisibilityState(!revealed, showInLog, showInGiver);
  }

  private boolean isRevealed(Player player, QuestLogEntry entry, QuestVisibilitySpec visibility) {
    if (visibility == null) {
      return true;
    }
    QuestEntryStatus status = entry.status();
    boolean readyToTurnIn = isReadyToTurnIn(entry.spec(), entry.state());
    List<QuestRequiredStatus> revealOn = visibility.revealOn();
    if (revealOn.isEmpty()) {
      revealOn = List.of(QuestRequiredStatus.ACTIVE, QuestRequiredStatus.TURNIN,
          QuestRequiredStatus.COMPLETED, QuestRequiredStatus.FAILED);
    }
    for (QuestRequiredStatus reveal : revealOn) {
      if (reveal == QuestRequiredStatus.TURNIN && readyToTurnIn) {
        return true;
      }
      if (reveal == null) {
        continue;
      }
      if (status.name().equalsIgnoreCase(reveal.name())) {
        return true;
      }
    }
    if (player == null) {
      return false;
    }
    for (QuestVisibilityCondition condition : visibility.requires()) {
      if (!visibilityConditionMatches(player, condition)) {
        return false;
      }
    }
    return !visibility.requires().isEmpty();
  }

  private boolean visibilityConditionMatches(Player player, QuestVisibilityCondition condition) {
    if (condition == null) {
      return true;
    }
    String questId = condition.questId();
    if (questId == null || questId.isBlank()) {
      return true;
    }
    QuestSpec spec = registry.quest(questId);
    if (spec == null) {
      return false;
    }
    QuestRequiredStatus required = condition.required();
    if (required == null) {
      return true;
    }
    QuestLogEntry entry = entryFor(player, spec);
    QuestEntryStatus status = entry == null ? QuestEntryStatus.LOCKED : entry.status();
    boolean readyToTurnIn = entry != null && isReadyToTurnIn(spec, entry.state());
    return switch (required) {
      case TURNIN -> readyToTurnIn;
      case ACTIVE -> status == QuestEntryStatus.ACTIVE;
      case AVAILABLE -> status == QuestEntryStatus.AVAILABLE;
      case COMPLETED -> status == QuestEntryStatus.COMPLETED;
      case FAILED -> status == QuestEntryStatus.FAILED;
      case COOLDOWN -> status == QuestEntryStatus.COOLDOWN;
      case LOCKED -> status == QuestEntryStatus.LOCKED;
    };
  }

  private record VisibilityState(boolean hidden, boolean showInLog, boolean showInGiver) {
    static VisibilityState visible() {
      return new VisibilityState(false, true, true);
    }
  }

  private enum DialogueState {
    DEFAULT,
    ACCEPT,
    ACTIVE,
    TURN_IN,
    COMPLETED
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
    if (req.permissions() != null && !req.permissions().isEmpty()) {
      List<String> missing = new ArrayList<>();
      for (String permission : req.permissions()) {
        if (permission == null || permission.isBlank()) {
          continue;
        }
        if (!player.hasPermission(permission)) {
          missing.add(permission);
        }
      }
      if (!missing.isEmpty()) {
        return Locales.component(player, "messages.quests.require.permission",
            Locales.placeholders("permission", String.join(", ", missing)));
      }
    }
    if (req.level() > 0) {
      int level = progression.getOrCreate(player.getUniqueId()).level();
      if (level < req.level()) {
        return Locales.component(player, "messages.quests.require.level",
            Locales.placeholders("level", req.level()));
      }
    }
    if (req.classIds() != null && !req.classIds().isEmpty()) {
      if (classService == null) {
        return Locales.component(player, "messages.quests.require.class",
            Locales.placeholders("class", String.join(", ", req.classIds())));
      }
      String current = classService.currentClassId(player.getUniqueId());
      if (current == null || req.classIds().stream().noneMatch(id -> id.equals(current))) {
        return Locales.component(player, "messages.quests.require.class",
            Locales.placeholders("class", String.join(", ", req.classIds())));
      }
    }
    if (req.skillNodes() != null && !req.skillNodes().isEmpty()) {
      if (classService == null || classSkillService == null) {
        return Locales.component(player, "messages.quests.require.skill",
            Locales.placeholders("skill", String.join(", ", req.skillNodes())));
      }
      String current = classService.currentClassId(player.getUniqueId());
      if (current == null) {
        return Locales.component(player, "messages.quests.require.skill",
            Locales.placeholders("skill", String.join(", ", req.skillNodes())));
      }
      List<String> missing = new ArrayList<>();
      for (String node : req.skillNodes()) {
        if (node == null || node.isBlank()) {
          continue;
        }
        if (!classSkillService.isUnlocked(player.getUniqueId(), current, node)) {
          missing.add(node);
        }
      }
      if (!missing.isEmpty()) {
        return Locales.component(player, "messages.quests.require.skill",
            Locales.placeholders("skill", String.join(", ", missing)));
      }
    }
    if (req.minCustomLevel() > 0 || req.minCustomPoints() > 0L) {
      if (customXpService == null) {
        return Locales.component(player, "messages.quests.require.customXp",
            Locales.placeholders("required", String.valueOf(Math.max(req.minCustomLevel(), req.minCustomPoints()))));
      }
      var profile = customXpService.getOrCreate(player.getUniqueId());
      if ((req.minCustomLevel() > 0 && profile.level() < req.minCustomLevel())
          || (req.minCustomPoints() > 0L && profile.points() < req.minCustomPoints())) {
        return Locales.component(player, "messages.quests.require.customXp",
            Locales.placeholders("required", String.valueOf(Math.max(req.minCustomLevel(), req.minCustomPoints()))));
      }
    }
    if (req.factionId() != null && !req.factionId().isBlank()) {
      if (factionService == null
          || !factionService.hasFaction(player.getUniqueId(), req.factionId(), req.minFactionRank())) {
        return Locales.component(player, "messages.quests.require.faction",
            Locales.placeholders("faction", req.factionId(), "rank", req.minFactionRank()));
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
    if (req.questStages() != null && !req.questStages().isEmpty()) {
      List<String> missing = new ArrayList<>();
      for (QuestStageRequirement stageRequirement : req.questStages()) {
        if (stageRequirement == null || stageRequirement.questId() == null) {
          continue;
        }
        if (!meetsQuestStage(player, stageRequirement)) {
          missing.add(stageRequirement.questId() + ":" + stageRequirement.stage());
        }
      }
      if (!missing.isEmpty()) {
        return Locales.component(player, "messages.quests.require.questStage",
            Locales.placeholders("stages", String.join(", ", missing)));
      }
    }
    Component branchLock = branchLockMessage(player, spec);
    if (branchLock != null) {
      return branchLock;
    }
    Component rotationLock = rotationPoolMessage(player, spec);
    if (rotationLock != null) {
      return rotationLock;
    }
    if (!matchesAvailability(req.availability())) {
      return Locales.component(player, "messages.quests.require.availability");
    }
    if (!matchesWorlds(req.acceptWorlds(), player.getWorld())) {
      return Locales.component(player, "messages.quests.require.acceptWorld");
    }
    if (!matchesRegions(req.acceptRegions(), player.getLocation())) {
      return Locales.component(player, "messages.quests.require.acceptRegion");
    }
    return null;
  }

  private Component branchLockMessage(Player player, QuestSpec spec) {
    if (player == null || spec == null || spec.branchId() == null || spec.branchId().isBlank()) {
      return null;
    }
    QuestBranchLock lock = spec.branchLock() == null ? QuestBranchLock.COMPLETED : spec.branchLock();
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    if (quests.isEmpty()) {
      return null;
    }
    List<String> conflicts = new ArrayList<>();
    for (QuestSpec other : registry.quests().values()) {
      if (other == null || other.id().equals(spec.id())) {
        continue;
      }
      if (other.branchId() == null || !other.branchId().equals(spec.branchId())) {
        continue;
      }
      QuestPlayerQuest state = quests.get(other.id());
      if (state == null) {
        continue;
      }
      boolean blocked = switch (lock) {
        case ACTIVE -> state.status() == QuestStatus.ACTIVE;
        case ANY -> state.status() == QuestStatus.ACTIVE || state.status() == QuestStatus.COMPLETED;
        case COMPLETED -> state.status() == QuestStatus.COMPLETED;
      };
      if (blocked) {
        conflicts.add(other.name());
      }
    }
    if (conflicts.isEmpty()) {
      return null;
    }
    return Locales.component(player, "messages.quests.require.branchLock",
        Locales.placeholders("branch", spec.branchId(), "quests", String.join(", ", conflicts)));
  }

  private Component turnInRequirementsMessage(Player player, QuestSpec spec) {
    QuestRequirements req = spec.requirements();
    if (req == null) {
      return null;
    }
    if (!matchesAvailability(req.turnInAvailability())) {
      return Locales.component(player, "messages.quests.require.turnInAvailability");
    }
    if (!matchesWorlds(req.turnInWorlds(), player.getWorld())) {
      return Locales.component(player, "messages.quests.require.turnInWorld");
    }
    if (!matchesRegions(req.turnInRegions(), player.getLocation())) {
      return Locales.component(player, "messages.quests.require.turnInRegion");
    }
    return null;
  }

  private void handleFailChecks(Player player, Location location) {
    Map<String, QuestPlayerQuest> quests = questsFor(player.getUniqueId());
    if (quests.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    for (QuestPlayerQuest quest : quests.values()) {
      if (quest == null || quest.status() != QuestStatus.ACTIVE) {
        continue;
      }
      QuestSpec spec = registry.quest(quest.questId());
      if (spec == null) {
        continue;
      }
      maybeFailQuest(player, quest, spec, location, now);
    }
  }

  private boolean maybeFailQuest(Player player, QuestPlayerQuest quest, QuestSpec spec, Location location, long now) {
    QuestFailSpec fail = spec.fail();
    if (fail == null) {
      return false;
    }
    if (fail.timeoutSeconds() > 0 && quest.startedAt() > 0L) {
      long elapsed = now - quest.startedAt();
      if (elapsed >= fail.timeoutSeconds() * 1000L) {
        failQuest(player, quest, spec, now, Locales.component(player, "messages.quests.fail.timeout",
            Locales.placeholders("quest", spec.name())));
        return true;
      }
    }
    if (location != null) {
      if (fail.region() != null) {
        if (!fail.region().contains(location)) {
          failQuest(player, quest, spec, now, Locales.component(player, "messages.quests.fail.region",
              Locales.placeholders("quest", spec.name())));
          return true;
        }
      } else if (fail.failOnLeaveRegion()) {
        QuestRequirements req = spec.requirements();
        if (req != null && req.acceptRegions() != null && !req.acceptRegions().isEmpty()
            && !matchesRegions(req.acceptRegions(), location)) {
          failQuest(player, quest, spec, now, Locales.component(player, "messages.quests.fail.region",
              Locales.placeholders("quest", spec.name())));
          return true;
        }
      }
    }
    return false;
  }

  private void failQuest(Player player, QuestPlayerQuest quest, QuestSpec spec, long now, Component message) {
    quest.status(QuestStatus.FAILED);
    quest.completedAt(now);
    if (spec.cooldownSeconds() > 0) {
      quest.cooldownUntil(now + spec.cooldownSeconds() * 1000L);
    } else {
      quest.cooldownUntil(0L);
    }
    repository.upsertQuest(player.getUniqueId(), quest);
    QuestAuditLog.get().record("fail", spec.id(), player.getName());
    if (message != null) {
      player.sendMessage(message);
    }
  }

  private boolean matchesAvailability(ShopAvailabilitySpec availability) {
    return availability == null || availability.isAvailableNow();
  }

  private boolean matchesWorlds(List<String> worlds, World world) {
    if (worlds == null || worlds.isEmpty() || world == null) {
      return true;
    }
    String name = world.getName().toLowerCase(Locale.ROOT);
    String key = world.getKey().toString().toLowerCase(Locale.ROOT);
    for (String entry : worlds) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      String needle = entry.toLowerCase(Locale.ROOT);
      if (needle.equals(name) || needle.equals(key)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesRegions(List<QuestRegion> regions, Location location) {
    if (regions == null || regions.isEmpty()) {
      return true;
    }
    for (QuestRegion region : regions) {
      if (region != null && region.contains(location)) {
        return true;
      }
    }
    return false;
  }

  private boolean meetsQuestStage(Player player, QuestStageRequirement requirement) {
    if (player == null || requirement == null || requirement.questId() == null) {
      return false;
    }
    QuestSpec chainSpec = registry.quest(requirement.questId());
    if (chainSpec == null) {
      return false;
    }
    QuestPlayerQuest state = questsFor(player.getUniqueId()).get(requirement.questId());
    if (state == null) {
      return false;
    }
    if (state.status() == QuestStatus.COMPLETED) {
      return true;
    }
    if (state.status() != QuestStatus.ACTIVE) {
      return false;
    }
    GroupIndex groups = GroupIndex.of(chainSpec.objectives());
    int stage = currentStage(state, chainSpec.objectives(), groups);
    if (stage == Integer.MAX_VALUE) {
      return true;
    }
    return stage >= requirement.stage();
  }

  private record RepeatLimitState(boolean limited, long nextResetAt) {
  }

  private RepeatLimitState repeatLimitState(Player player, QuestPlayerQuest state, QuestSpec spec, long now) {
    if (state == null || spec == null || spec.repeat() == null) {
      return new RepeatLimitState(false, 0L);
    }
    QuestRepeatSpec repeat = spec.repeat();
    if (repeat.dailyLimit() <= 0 && repeat.weeklyLimit() <= 0) {
      return new RepeatLimitState(false, 0L);
    }
    boolean changed = refreshRepeatCounters(state, repeat, now);
    if (changed) {
      repository.upsertQuest(player.getUniqueId(), state);
    }
    boolean dailyLimited = repeat.dailyLimit() > 0 && state.dailyCount() >= repeat.dailyLimit();
    boolean weeklyLimited = repeat.weeklyLimit() > 0 && state.weeklyCount() >= repeat.weeklyLimit();
    if (!dailyLimited && !weeklyLimited) {
      return new RepeatLimitState(false, 0L);
    }
    long nextReset = Long.MAX_VALUE;
    if (dailyLimited && state.dailyResetAt() > 0L) {
      nextReset = Math.min(nextReset, state.dailyResetAt());
    }
    if (weeklyLimited && state.weeklyResetAt() > 0L) {
      nextReset = Math.min(nextReset, state.weeklyResetAt());
    }
    if (nextReset == Long.MAX_VALUE) {
      nextReset = now;
    }
    return new RepeatLimitState(true, nextReset);
  }

  private boolean refreshRepeatCounters(QuestPlayerQuest state, QuestRepeatSpec repeat, long now) {
    boolean changed = false;
    if (repeat.dailyLimit() > 0) {
      if (state.dailyResetAt() <= 0L || now >= state.dailyResetAt()) {
        state.dailyCount(0);
        state.dailyResetAt(nextRotationMillis(QuestRotation.DAILY));
        changed = true;
      }
    } else if (state.dailyCount() != 0 || state.dailyResetAt() != 0L) {
      state.dailyCount(0);
      state.dailyResetAt(0L);
      changed = true;
    }
    if (repeat.weeklyLimit() > 0) {
      if (state.weeklyResetAt() <= 0L || now >= state.weeklyResetAt()) {
        state.weeklyCount(0);
        state.weeklyResetAt(nextRotationMillis(QuestRotation.WEEKLY));
        changed = true;
      }
    } else if (state.weeklyCount() != 0 || state.weeklyResetAt() != 0L) {
      state.weeklyCount(0);
      state.weeklyResetAt(0L);
      changed = true;
    }
    return changed;
  }

  private void applyRepeatCompletion(QuestPlayerQuest state, QuestRepeatSpec repeat, long now) {
    if (state == null || repeat == null) {
      return;
    }
    refreshRepeatCounters(state, repeat, now);
    if (repeat.dailyLimit() > 0) {
      if (state.dailyResetAt() <= 0L) {
        state.dailyResetAt(nextRotationMillis(QuestRotation.DAILY));
      }
      state.dailyCount(state.dailyCount() + 1);
    }
    if (repeat.weeklyLimit() > 0) {
      if (state.weeklyResetAt() <= 0L) {
        state.weeklyResetAt(nextRotationMillis(QuestRotation.WEEKLY));
      }
      state.weeklyCount(state.weeklyCount() + 1);
    }
  }

  private Component rotationPoolMessage(Player player, QuestSpec spec) {
    if (spec == null) {
      return null;
    }
    if (isQuestInActiveRotationPool(player, spec)) {
      return null;
    }
    return Locales.component(player, "messages.quests.require.rotationPool");
  }

  private boolean isQuestInActiveRotationPool(Player player, QuestSpec spec) {
    if (spec == null) {
      return true;
    }
    if (spec.rotationPool() != null) {
      QuestRotationPoolSpec pool = registry.rotationPools().get(spec.rotationPool());
      if (pool == null) {
        return false;
      }
      return isQuestActiveInPool(player, pool, spec.id());
    }
    List<QuestRotationPoolSpec> pools = registry.rotationPoolsForQuest(spec.id());
    if (pools.isEmpty()) {
      return true;
    }
    for (QuestRotationPoolSpec pool : pools) {
      if (isQuestActiveInPool(player, pool, spec.id())) {
        return true;
      }
    }
    return false;
  }

  private boolean isQuestActiveInPool(Player player, QuestRotationPoolSpec pool, String questId) {
    if (pool == null || questId == null) {
      return true;
    }
    List<String> poolQuestIds = pool.questIds();
    if (poolQuestIds == null || poolQuestIds.isEmpty()) {
      return true;
    }
    if (!poolQuestIds.contains(questId)) {
      return false;
    }
    int size = pool.size();
    if (size <= 0 || size >= poolQuestIds.size()) {
      return true;
    }
    long seed = rotationSeed(pool.rotation(), pool.id(), player, pool.scope());
    List<String> copy = new ArrayList<>(poolQuestIds);
    Collections.shuffle(copy, new Random(seed));
    return copy.subList(0, size).contains(questId);
  }

  private long rotationSeed(QuestRotation rotation, String poolId, Player player, QuestRotationPoolScope scope) {
    long seed = rotationKey(rotation);
    if (poolId != null) {
      seed ^= poolId.hashCode();
    }
    if (scope == QuestRotationPoolScope.PLAYER && player != null) {
      UUID id = player.getUniqueId();
      seed ^= id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }
    return seed;
  }

  private long rotationKey(QuestRotation rotation) {
    ZoneId zone = ZoneId.systemDefault();
    LocalDate date = LocalDate.now(zone);
    return switch (rotation == null ? QuestRotation.NONE : rotation) {
      case DAILY -> date.toEpochDay();
      case WEEKLY -> {
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        yield ((long) year << 6) ^ week;
      }
      case MONTHLY -> (long) date.getYear() * 12L + date.getMonthValue();
      case SEASONAL -> (long) date.getYear() * 4L + (date.getMonthValue() - 1) / 3;
      case NONE -> 0L;
    };
  }

  private boolean isRepeatable(QuestSpec spec) {
    if (spec == null) {
      return false;
    }
    if (spec.rotation() != null && spec.rotation() != QuestRotation.NONE) {
      return true;
    }
    if (spec.repeat() != null && (spec.repeat().dailyLimit() > 0 || spec.repeat().weeklyLimit() > 0)) {
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
      case MONTHLY -> now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
      case SEASONAL -> {
        int month = now.getMonthValue();
        int seasonStartMonth = ((month - 1) / 3) * 3 + 1;
        ZonedDateTime seasonStart = now.withMonth(seasonStartMonth).withDayOfMonth(1).toLocalDate().atStartOfDay(zone);
        yield seasonStart.plusMonths(3).toInstant().toEpochMilli();
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
      Player player,
      String mobId,
      EntityType entityType,
      String mobTier,
      String mobPhase,
      String mobVariant,
      String mobTrait,
      ItemStack item,
      String itemId,
      Material material,
      Material blockMaterial,
      Location location,
      String recipeId,
      int increment,
      List<ItemStack> outputItems,
      List<String> outputItemIds,
      List<Material> outputMaterials
  ) {
    static ObjectiveContext kill(Player player, String mobId, EntityType entityType, String mobTier, String mobPhase,
        String mobVariant, String mobTrait) {
      return new ObjectiveContext(player, mobId, entityType, mobTier, mobPhase, mobVariant, mobTrait,
          null, null, null, null, null, null, 1, List.of(), List.of(), List.of());
    }

    static ObjectiveContext useItem(Player player, String itemId, Material material, ItemStack item) {
      return new ObjectiveContext(player, null, null, null, null, null, null,
          item, itemId, material, null, null, null, 1, List.of(), List.of(), List.of());
    }

    static ObjectiveContext visit(Player player, Location location) {
      return new ObjectiveContext(player, null, null, null, null, null, null,
          null, null, null, null, location, null, 1, List.of(), List.of(), List.of());
    }

    static ObjectiveContext craft(Player player, String recipeId, List<ItemStack> outputs) {
      List<String> itemIds = new ArrayList<>();
      List<Material> materials = new ArrayList<>();
      List<ItemStack> items = new ArrayList<>();
      if (outputs != null) {
        for (ItemStack stack : outputs) {
          if (stack == null || stack.getType().isAir()) {
            continue;
          }
          items.add(stack);
          String itemId = ItemMarkers.getItemId(stack);
          if (itemId != null) {
            itemIds.add(itemId);
          }
          materials.add(stack.getType());
        }
      }
      return new ObjectiveContext(player, null, null, null, null, null, null,
          null, null, null, null, null, recipeId, 1, items, itemIds, materials);
    }

    static ObjectiveContext breakBlock(Player player, Material material) {
      return new ObjectiveContext(player, null, null, null, null, null, null,
          null, null, null, material, null, null, 1, List.of(), List.of(), List.of());
    }

    static ObjectiveContext placeBlock(Player player, Material material) {
      return new ObjectiveContext(player, null, null, null, null, null, null,
          null, null, null, material, null, null, 1, List.of(), List.of(), List.of());
    }
  }

  private boolean isObjectiveExpired(QuestPlayerQuest quest, QuestObjectiveSpec objective, long now) {
    if (quest == null || objective == null) {
      return false;
    }
    long limit = objective.timeLimitSeconds();
    if (limit <= 0) {
      return false;
    }
    long deadline = quest.startedAt() + (limit * 1000L);
    return now > deadline;
  }

  private int currentStage(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives, GroupIndex groups) {
    int minStage = Integer.MAX_VALUE;
    for (int i = 0; i < objectives.size(); i++) {
      QuestObjectiveSpec objective = objectives.get(i);
      if (objective.groupId() != null && !objective.groupId().isBlank()) {
        continue;
      }
      if (objective.optional()) {
        continue;
      }
      if (!isObjectiveComplete(quest, objective, i)) {
        minStage = Math.min(minStage, objective.stage());
      }
    }
    for (GroupState group : groups.groups().values()) {
      if (!group.hasRequiredMembers(objectives)) {
        continue;
      }
      int stage = group.nextRequiredStage(quest, objectives);
      if (stage >= 0) {
        minStage = Math.min(minStage, stage);
      }
    }
    return minStage == Integer.MAX_VALUE ? Integer.MAX_VALUE : minStage;
  }

  private boolean isObjectiveActive(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives, GroupIndex groups,
      int currentStage, int index) {
    QuestObjectiveSpec objective = objectives.get(index);
    if (currentStage != Integer.MAX_VALUE && objective.stage() > currentStage) {
      return false;
    }
    if (objective.groupId() == null || objective.groupId().isBlank()) {
      return true;
    }
    GroupState group = groups.groups().get(objective.groupId());
    if (group == null) {
      return true;
    }
    if (group.mode() == QuestCompositeMode.ANY_OF && group.isComplete(quest, objectives)) {
      return false;
    }
    if (group.mode() == QuestCompositeMode.SEQUENCE) {
      int nextOrder = group.nextRequiredOrder(quest, objectives);
      if (nextOrder >= 0 && objective.order() != nextOrder) {
        return false;
      }
    }
    return true;
  }

  private boolean isObjectiveComplete(QuestPlayerQuest quest, QuestObjectiveSpec objective, int index) {
    int required = Math.max(1, objective.count());
    return quest.progress(index) >= required;
  }

  private static final class GroupIndex {
    private final Map<String, GroupState> groups;

    private GroupIndex(Map<String, GroupState> groups) {
      this.groups = groups;
    }

    static GroupIndex of(List<QuestObjectiveSpec> objectives) {
      Map<String, GroupState> groups = new HashMap<>();
      for (int i = 0; i < objectives.size(); i++) {
        QuestObjectiveSpec objective = objectives.get(i);
        if (objective.groupId() == null || objective.groupId().isBlank()) {
          continue;
        }
        GroupState state = groups.computeIfAbsent(objective.groupId(),
            id -> new GroupState(objective.groupId(), objective.groupMode()));
        state.add(i);
      }
      return new GroupIndex(groups);
    }

    Map<String, GroupState> groups() {
      return groups;
    }
  }

  private static final class GroupState {
    @SuppressWarnings("unused")
    private final String id;
    private final QuestCompositeMode mode;
    private final List<Integer> members = new ArrayList<>();

    GroupState(String id, QuestCompositeMode mode) {
      this.id = id;
      this.mode = mode == null ? QuestCompositeMode.NONE : mode;
    }

    void add(int index) {
      members.add(index);
    }

    QuestCompositeMode mode() {
      return mode;
    }

    boolean hasRequiredMembers(List<QuestObjectiveSpec> objectives) {
      for (int index : members) {
        QuestObjectiveSpec objective = objectives.get(index);
        if (!objective.optional()) {
          return true;
        }
      }
      return false;
    }

    int nextRequiredOrder(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives) {
      int next = Integer.MAX_VALUE;
      for (int index : members) {
        QuestObjectiveSpec objective = objectives.get(index);
        if (objective.optional()) {
          continue;
        }
        int required = Math.max(1, objective.count());
        if (quest.progress(index) < required) {
          next = Math.min(next, objective.order());
        }
      }
      return next == Integer.MAX_VALUE ? -1 : next;
    }

    int nextRequiredStage(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives) {
      int next = Integer.MAX_VALUE;
      for (int index : members) {
        QuestObjectiveSpec objective = objectives.get(index);
        if (objective.optional()) {
          continue;
        }
        int required = Math.max(1, objective.count());
        if (quest.progress(index) < required) {
          next = Math.min(next, objective.stage());
        }
      }
      return next == Integer.MAX_VALUE ? -1 : next;
    }

    boolean isComplete(QuestPlayerQuest quest, List<QuestObjectiveSpec> objectives) {
      if (mode == QuestCompositeMode.ANY_OF) {
        for (int index : members) {
          QuestObjectiveSpec objective = objectives.get(index);
          int required = Math.max(1, objective.count());
          if (quest.progress(index) >= required) {
            return true;
          }
        }
        for (int index : members) {
          QuestObjectiveSpec objective = objectives.get(index);
          if (!objective.optional()) {
            return false;
          }
        }
        return true;
      }
      for (int index : members) {
        QuestObjectiveSpec objective = objectives.get(index);
        if (objective.optional()) {
          continue;
        }
        int required = Math.max(1, objective.count());
        if (quest.progress(index) < required) {
          return false;
        }
      }
      return true;
    }
  }
}
