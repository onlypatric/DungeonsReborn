package dev.patric.dungeonsreborn.shops;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestRegion;
import dev.patric.dungeonsreborn.quests.QuestService.QuestEntryStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShopRequirements {
  public record Services(QuestService questService, ClassService classService, CustomXpService customXpService,
      ShopFactionService factionService) {
  }

  private ShopRequirements() {
  }

  public static boolean isVisible(Player player, List<ShopRequirementSpec> requirements, Services services) {
    if (player == null || requirements == null || requirements.isEmpty()) {
      return true;
    }
    for (ShopRequirementSpec requirement : requirements) {
      if (requirement == null) {
        continue;
      }
      if (!meetsRequirement(player, requirement, services, null)) {
        return false;
      }
    }
    return true;
  }

  public static ShopRequirementResult check(Player player, List<ShopRequirementSpec> requirements, Services services,
      String messagePrefix) {
    if (player == null || requirements == null || requirements.isEmpty()) {
      return ShopRequirementResult.allow();
    }
    for (ShopRequirementSpec requirement : requirements) {
      if (requirement == null) {
        continue;
      }
      ShopRequirementResult result = checkRequirement(player, requirement, services, messagePrefix);
      if (!result.allowed()) {
        return result;
      }
    }
    return ShopRequirementResult.allow();
  }

  private static ShopRequirementResult checkRequirement(Player player, ShopRequirementSpec requirement, Services services,
      String messagePrefix) {
    if (meetsRequirement(player, requirement, services, messagePrefix)) {
      return ShopRequirementResult.allow();
    }
    Component message = buildMessage(player, requirement, messagePrefix);
    return ShopRequirementResult.deny(requirement.type().name().toLowerCase(Locale.ROOT), message);
  }

  private static boolean meetsRequirement(Player player, ShopRequirementSpec requirement, Services services,
      String messagePrefix) {
    return switch (requirement.type()) {
      case PERMISSION -> requirement.permission() != null && !requirement.permission().isBlank()
          && player.hasPermission(requirement.permission());
      case LEVEL -> player.getLevel() >= Math.max(0, requirement.minLevel());
      case CUSTOM_XP -> {
        CustomXpService customXp = services == null ? null : services.customXpService();
        if (customXp == null) {
          yield false;
        }
        var profile = customXp.getOrCreate(player.getUniqueId());
        boolean ok = (requirement.minCustomLevel() <= 0 || profile.level() >= requirement.minCustomLevel())
            && (requirement.minCustomPoints() <= 0L || profile.points() >= requirement.minCustomPoints());
        yield ok;
      }
      case QUEST -> {
        QuestService quests = services == null ? null : services.questService();
        if (quests == null) {
          yield false;
        }
        QuestEntryStatus status = quests.statusFor(player, requirement.questId());
        ShopRequirementSpec.QuestStatus expected = requirement.questStatus();
        if (expected == null) {
          expected = ShopRequirementSpec.QuestStatus.COMPLETED;
        }
        yield matchesQuestStatus(status, expected);
      }
      case CLASS -> {
        ClassService classes = services == null ? null : services.classService();
        if (classes == null || requirement.classIds().isEmpty()) {
          yield false;
        }
        String current = classes.currentClassId(player.getUniqueId());
        yield current != null && requirement.classIds().stream().anyMatch(id -> id.equals(current));
      }
      case REGION -> {
        List<QuestRegion> regions = requirement.regions();
        if (regions == null || regions.isEmpty()) {
          yield true;
        }
        boolean ok = false;
        for (QuestRegion region : regions) {
          if (region != null && region.contains(player.getLocation())) {
            ok = true;
            break;
          }
        }
        yield ok;
      }
      case FACTION -> {
        ShopFactionService factions = services == null ? null : services.factionService();
        if (factions == null) {
          yield false;
        }
        String factionId = requirement.factionId();
        if (factionId == null || factionId.isBlank()) {
          yield false;
        }
        yield factions.hasFaction(player.getUniqueId(), factionId, requirement.minFactionRank());
      }
    };
  }

  private static Component buildMessage(Player player, ShopRequirementSpec requirement, String prefix) {
    String override = requirement.message();
    if (override != null && !override.isBlank()) {
      return GuiMini.mm(override,
          Placeholder.unparsed("permission", String.valueOf(requirement.permission())),
          Placeholder.unparsed("level", String.valueOf(requirement.minLevel())),
          Placeholder.unparsed("required", String.valueOf(Math.max(requirement.minCustomLevel(), requirement.minCustomPoints()))),
          Placeholder.unparsed("quest", String.valueOf(requirement.questId())),
          Placeholder.unparsed("status", requirement.questStatus() == null ? "" : requirement.questStatus().name().toLowerCase(Locale.ROOT)),
          Placeholder.unparsed("class", String.join(", ", requirement.classIds())),
          Placeholder.unparsed("faction", String.valueOf(requirement.factionId())),
          Placeholder.unparsed("rank", String.valueOf(requirement.minFactionRank())));
    }
    String key = Objects.requireNonNullElse(prefix, "messages.shops.trade");
    return switch (requirement.type()) {
      case PERMISSION -> Locales.component(player, key + ".missingPermission",
          Locales.placeholders("perm", requirement.permission()));
      case LEVEL -> Locales.component(player, key + ".requiresLevel",
          Locales.placeholders("level", String.valueOf(requirement.minLevel())));
      case CUSTOM_XP -> Locales.component(player, key + ".requiresCustomXp",
          Locales.placeholders("required", String.valueOf(Math.max(requirement.minCustomLevel(), requirement.minCustomPoints()))));
      case QUEST -> Locales.component(player, key + ".requiresQuest",
          Locales.placeholders("quest", String.valueOf(requirement.questId()),
              "status", requirement.questStatus() == null ? "completed" : requirement.questStatus().name().toLowerCase(Locale.ROOT)));
      case CLASS -> Locales.component(player, key + ".requiresClass",
          Locales.placeholders("class", String.join(", ", requirement.classIds())));
      case REGION -> Locales.component(player, key + ".requiresRegion");
      case FACTION -> Locales.component(player, key + ".requiresFaction",
          Locales.placeholders("faction", String.valueOf(requirement.factionId()),
              "rank", String.valueOf(requirement.minFactionRank())));
    };
  }

  private static boolean matchesQuestStatus(QuestEntryStatus actual, ShopRequirementSpec.QuestStatus expected) {
    if (expected == null) {
      return true;
    }
    if (actual == null) {
      return expected == ShopRequirementSpec.QuestStatus.LOCKED;
    }
    return switch (expected) {
      case ACTIVE -> actual == QuestEntryStatus.ACTIVE;
      case AVAILABLE -> actual == QuestEntryStatus.AVAILABLE;
      case COMPLETED -> actual == QuestEntryStatus.COMPLETED;
      case COOLDOWN -> actual == QuestEntryStatus.COOLDOWN;
      case LOCKED -> actual == QuestEntryStatus.LOCKED;
    };
  }
}
