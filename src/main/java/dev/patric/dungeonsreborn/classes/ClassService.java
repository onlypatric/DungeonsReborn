package dev.patric.dungeonsreborn.classes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;

public final class ClassService {
  public record Availability(boolean allowedWorld, int level, int levelRequired, int tokens, int tokensRequired,
      boolean questsMet, List<String> quests) {
    public boolean isUnlocked() {
      return allowedWorld && level >= levelRequired && tokens >= tokensRequired && questsMet;
    }
  }

  public record SelectionResult(boolean success, Component message) {
  }

  private final ClassYamlRegistry registry;
  private final ClassSelectionRepository repository;
  private final ProgressionService progression;
  private final ShopYamlRegistry shops;
  private final Predicate<World> worldAllowed;
  private final Logger logger;

  public ClassService(ClassYamlRegistry registry, ClassSelectionRepository repository, ProgressionService progression,
      ShopYamlRegistry shops, Predicate<World> worldAllowed, Logger logger) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.progression = Objects.requireNonNull(progression, "progression");
    this.shops = shops;
    this.worldAllowed = worldAllowed;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public String currentClassId(UUID uuid) {
    if (uuid == null) {
      return null;
    }
    return repository.load(uuid).orElse(null);
  }

  public ClassSpec currentClass(UUID uuid) {
    String id = currentClassId(uuid);
    return id == null ? null : registry.classSpec(id);
  }

  public Availability availability(Player player, ClassSpec spec) {
    if (player == null || spec == null) {
      return new Availability(false, 0, 0, 0, 0, true, List.of());
    }
    boolean allowed = worldAllowed == null || worldAllowed.test(player.getWorld());
    progression.syncFromPlayer(player);
    int level = progression.getOrCreate(player.getUniqueId()).level();
    ClassUnlockSpec unlock = spec.unlock() == null ? ClassUnlockSpec.none() : spec.unlock();
    int tokens = countTokens(player);
    boolean questsMet = unlock.quests() == null || unlock.quests().isEmpty();
    List<String> quests = unlock.quests() == null ? List.of() : unlock.quests();
    return new Availability(allowed, level, unlock.level(), tokens, unlock.tokens(), questsMet, quests);
  }

  public SelectionResult selectClass(Player player, String classId) {
    if (player == null || classId == null) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.invalid"));
    }
    ClassSpec spec = registry.classSpec(classId);
    if (spec == null) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.unknown",
          Locales.placeholders("id", classId)));
    }
    if (!spec.enabled()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.disabled"));
    }
    String current = currentClassId(player.getUniqueId());
    if (current != null && current.equals(spec.id())) {
      return new SelectionResult(true, Locales.component(player, "messages.classes.select.alreadySelected"));
    }
    Availability availability = availability(player, spec);
    if (!availability.allowedWorld()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.worldDenied"));
    }
    if (availability.level() < availability.levelRequired()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.requiresLevel",
          Locales.placeholders("level", String.valueOf(availability.levelRequired()))));
    }
    if (!availability.questsMet()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.requiresQuests"));
    }
    if (availability.tokens() < availability.tokensRequired()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.requiresTokens",
          Locales.placeholders("tokens", String.valueOf(availability.tokensRequired()))));
    }
    if (availability.tokensRequired() > 0 && !consumeTokens(player, availability.tokensRequired())) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.consumeFailed"));
    }
    repository.save(player.getUniqueId(), spec.id(), System.currentTimeMillis());
    return new SelectionResult(true, Locales.component(player, "messages.classes.select.ok",
        Locales.placeholders("class", spec.id())));
  }

  private int countTokens(Player player) {
    ShopTokenSpec tokenSpec = shops == null ? null : shops.tokenSpec();
    if (player == null || tokenSpec == null || tokenSpec.markerKey() == null) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (ItemMarkers.has(stack, tokenSpec.markerKey())) {
        total += stack.getAmount();
      }
    }
    return total;
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
      logger.warning("[Classes] Token consumption failed for player " + player.getName());
      return false;
    }
    return true;
  }

  public List<Component> buildRequirementLore(Player player, ClassSpec spec) {
    Availability availability = availability(player, spec);
    List<Component> lore = new ArrayList<>();
    if (availability.levelRequired() > 0) {
      lore.add(Locales.component(player, "gui.classes.requirements.level",
          Locales.placeholders(
              "current", String.valueOf(availability.level()),
              "required", String.valueOf(availability.levelRequired()))));
    }
    if (availability.tokensRequired() > 0) {
      lore.add(Locales.component(player, "gui.classes.requirements.tokens",
          Locales.placeholders(
              "current", String.valueOf(availability.tokens()),
              "required", String.valueOf(availability.tokensRequired()))));
    }
    if (!availability.quests().isEmpty()) {
      lore.add(Locales.component(player, "gui.classes.requirements.quests",
          Locales.placeholders("quests", String.join(", ", availability.quests()))));
    }
    if (!availability.allowedWorld()) {
      lore.add(Locales.component(player, "gui.classes.requirements.world"));
    }
    return lore;
  }
}
