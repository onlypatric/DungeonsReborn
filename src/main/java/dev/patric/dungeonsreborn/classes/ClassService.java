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
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestService.QuestEntryStatus;
import dev.patric.dungeonsreborn.shops.ShopCurrencySpec;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;

public final class ClassService {
  public record ItemRequirement(String label, int current, int required) {
    public boolean met() {
      return current >= required;
    }
  }

  public record CurrencyRequirement(String label, int current, int required) {
    public boolean met() {
      return current >= required;
    }
  }

  public record Availability(boolean allowedWorld, int level, int levelRequired, int tokens, int tokensRequired,
      boolean questsMet, List<String> quests, List<ItemRequirement> items, List<CurrencyRequirement> currencies) {
    public boolean itemsMet() {
      for (ItemRequirement item : items) {
        if (item == null || !item.met()) {
          return false;
        }
      }
      return true;
    }

    public boolean currenciesMet() {
      for (CurrencyRequirement currency : currencies) {
        if (currency == null || !currency.met()) {
          return false;
        }
      }
      return true;
    }

    public boolean isUnlocked() {
      return allowedWorld && level >= levelRequired && tokens >= tokensRequired && questsMet && itemsMet()
          && currenciesMet();
    }
  }

  public record SelectionResult(boolean success, Component message) {
  }

  private final ClassYamlRegistry registry;
  private final ClassSelectionRepository repository;
  private final ProgressionService progression;
  private final ShopYamlRegistry shops;
  private final QuestService quests;
  private final Predicate<World> worldAllowed;
  private final long switchCooldownMs;
  private final java.util.Set<String> lockoutWorlds;
  private final Logger logger;

  public ClassService(ClassYamlRegistry registry, ClassSelectionRepository repository, ProgressionService progression,
      ShopYamlRegistry shops, QuestService quests, Predicate<World> worldAllowed, Logger logger,
      long switchCooldownSeconds,
      java.util.Set<String> lockoutWorlds) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.progression = Objects.requireNonNull(progression, "progression");
    this.shops = shops;
    this.quests = quests;
    this.worldAllowed = worldAllowed;
    this.switchCooldownMs = Math.max(0L, switchCooldownSeconds) * 1000L;
    this.lockoutWorlds = lockoutWorlds == null ? java.util.Set.of() : java.util.Set.copyOf(lockoutWorlds);
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
      return new Availability(false, 0, 0, 0, 0, true, List.of(), List.of(), List.of());
    }
    boolean allowed = worldAllowed == null || worldAllowed.test(player.getWorld());
    progression.syncFromPlayer(player);
    int level = progression.getOrCreate(player.getUniqueId()).level();
    ClassUnlockSpec unlock = spec.unlock() == null ? ClassUnlockSpec.none() : spec.unlock();
    int tokens = countTokens(player);
    List<String> questIds = unlock.quests() == null ? List.of() : unlock.quests();
    boolean questsMet = questIds.isEmpty() || questsMet(player, questIds);
    List<ItemRequirement> items = buildItemRequirements(player, unlock.items());
    List<CurrencyRequirement> currencies = buildCurrencyRequirements(player, unlock.currencies());
    return new Availability(allowed, level, unlock.level(), tokens, unlock.tokens(), questsMet, questIds, items, currencies);
  }

  public SelectionResult selectClass(Player player, String classId) {
    if (player == null || classId == null) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.invalid"));
    }
    if (isLockoutWorld(player.getWorld())) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.locked"));
    }
    ClassSpec spec = registry.classSpec(classId);
    if (spec == null) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.unknown",
          Locales.placeholders("id", classId)));
    }
    if (!spec.enabled()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.disabled"));
    }
    ClassSelectionRepository.Selection selection = repository.loadSelection(player.getUniqueId()).orElse(null);
    String current = selection == null ? null : selection.classId();
    if (current != null && current.equals(spec.id())) {
      return new SelectionResult(true, Locales.component(player, "messages.classes.select.alreadySelected"));
    }
    if (selection != null && switchCooldownMs > 0L) {
      long elapsed = System.currentTimeMillis() - selection.lastUpdateMillis();
      if (elapsed < switchCooldownMs) {
        long remainingMs = switchCooldownMs - Math.max(0L, elapsed);
        long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        return new SelectionResult(false, Locales.component(player, "messages.classes.select.cooldown",
            Locales.placeholders("seconds", String.valueOf(seconds))));
      }
    }
    ClassUnlockSpec unlock = spec.unlock() == null ? ClassUnlockSpec.none() : spec.unlock();
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
    if (!availability.itemsMet()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.requiresItems",
          Locales.placeholders("items", missingItems(availability.items()))));
    }
    if (!availability.currenciesMet()) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.requiresCurrencies",
          Locales.placeholders("currencies", missingCurrencies(availability.currencies()))));
    }
    if (availability.tokensRequired() > 0 && !consumeTokens(player, availability.tokensRequired())) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.consumeFailed"));
    }
    if (!consumeUnlockCurrencies(player, unlock.currencies())) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.consumeCurrenciesFailed"));
    }
    if (!consumeUnlockItems(player, unlock.items())) {
      return new SelectionResult(false, Locales.component(player, "messages.classes.select.consumeItemsFailed"));
    }
    String fromClass = current;
    repository.save(player.getUniqueId(), spec.id(), System.currentTimeMillis());
    repository.recordHistory(player.getUniqueId(), fromClass, spec.id(), System.currentTimeMillis(), "switch");
    logger.info("[Classes] " + player.getName() + " switched from " + (fromClass == null ? "none" : fromClass) + " to "
        + spec.id());
    return new SelectionResult(true, Locales.component(player, "messages.classes.select.ok",
        Locales.placeholders("class", spec.id())));
  }

  private boolean isLockoutWorld(World world) {
    if (world == null || lockoutWorlds.isEmpty()) {
      return false;
    }
    String name = world.getName().toLowerCase(java.util.Locale.ROOT);
    String key = world.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
    return lockoutWorlds.contains(name) || lockoutWorlds.contains(key);
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

  private boolean questsMet(Player player, List<String> questIds) {
    if (questIds == null || questIds.isEmpty()) {
      return true;
    }
    if (quests == null) {
      return false;
    }
    for (String questId : questIds) {
      if (questId == null || questId.isBlank()) {
        continue;
      }
      QuestEntryStatus status = quests.statusFor(player, questId);
      if (status != QuestEntryStatus.COMPLETED) {
        return false;
      }
    }
    return true;
  }

  private List<ItemRequirement> buildItemRequirements(Player player, List<ClassUnlockItemSpec> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    List<ItemRequirement> out = new ArrayList<>();
    for (ClassUnlockItemSpec spec : items) {
      if (spec == null || spec.amount() <= 0) {
        continue;
      }
      int current = countItems(player, spec.matcher());
      out.add(new ItemRequirement(spec.label(), current, spec.amount()));
    }
    return List.copyOf(out);
  }

  private List<CurrencyRequirement> buildCurrencyRequirements(Player player, List<ClassUnlockCurrencySpec> currencies) {
    if (currencies == null || currencies.isEmpty()) {
      return List.of();
    }
    List<CurrencyRequirement> out = new ArrayList<>();
    for (ClassUnlockCurrencySpec spec : currencies) {
      if (spec == null || spec.amount() <= 0) {
        continue;
      }
      ShopCurrencySpec currency = shops == null ? null : shops.currency(spec.id());
      int current = countCurrency(player, currency);
      String label = currency == null ? spec.id() : currency.id();
      out.add(new CurrencyRequirement(label, current, spec.amount()));
    }
    return List.copyOf(out);
  }

  private int countItems(Player player, ItemMatcher matcher) {
    if (player == null || matcher == null) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (matcher.matches(player, stack)) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  private boolean consumeUnlockItems(Player player, List<ClassUnlockItemSpec> items) {
    if (items == null || items.isEmpty()) {
      return true;
    }
    for (ClassUnlockItemSpec spec : items) {
      if (spec == null || spec.amount() <= 0) {
        continue;
      }
      if (!consumeItems(player, spec.matcher(), spec.amount())) {
        return false;
      }
    }
    return true;
  }

  private boolean consumeItems(Player player, ItemMatcher matcher, int amount) {
    if (player == null || matcher == null || amount <= 0) {
      return true;
    }
    int remaining = amount;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && remaining > 0; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!matcher.matches(player, stack)) {
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
      logger.warning("[Classes] Item consumption failed for player " + player.getName());
      return false;
    }
    return true;
  }

  private int countCurrency(Player player, ShopCurrencySpec currency) {
    if (player == null || currency == null) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (currency.matches(stack)) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  private boolean consumeUnlockCurrencies(Player player, List<ClassUnlockCurrencySpec> currencies) {
    if (currencies == null || currencies.isEmpty()) {
      return true;
    }
    for (ClassUnlockCurrencySpec spec : currencies) {
      if (spec == null || spec.amount() <= 0) {
        continue;
      }
      ShopCurrencySpec currency = shops == null ? null : shops.currency(spec.id());
      if (!consumeCurrency(player, currency, spec.amount())) {
        return false;
      }
    }
    return true;
  }

  private boolean consumeCurrency(Player player, ShopCurrencySpec currency, int amount) {
    if (player == null || currency == null || amount <= 0) {
      return currency != null || amount <= 0;
    }
    int remaining = amount;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && remaining > 0; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!currency.matches(stack)) {
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
      logger.warning("[Classes] Currency consumption failed for player " + player.getName());
      return false;
    }
    return true;
  }

  private String missingItems(List<ItemRequirement> items) {
    if (items == null || items.isEmpty()) {
      return "";
    }
    List<String> missing = new ArrayList<>();
    for (ItemRequirement item : items) {
      if (item != null && !item.met()) {
        missing.add(item.label());
      }
    }
    return String.join(", ", missing);
  }

  private String missingCurrencies(List<CurrencyRequirement> currencies) {
    if (currencies == null || currencies.isEmpty()) {
      return "";
    }
    List<String> missing = new ArrayList<>();
    for (CurrencyRequirement currency : currencies) {
      if (currency != null && !currency.met()) {
        missing.add(currency.label());
      }
    }
    return String.join(", ", missing);
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
    for (ItemRequirement item : availability.items()) {
      if (item.required() <= 0) {
        continue;
      }
      lore.add(Locales.component(player, "gui.classes.requirements.items",
          Locales.placeholders(
              "label", item.label(),
              "current", String.valueOf(item.current()),
              "required", String.valueOf(item.required()))));
    }
    for (CurrencyRequirement currency : availability.currencies()) {
      if (currency.required() <= 0) {
        continue;
      }
      lore.add(Locales.component(player, "gui.classes.requirements.currencies",
          Locales.placeholders(
              "label", currency.label(),
              "current", String.valueOf(currency.current()),
              "required", String.valueOf(currency.required()))));
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
