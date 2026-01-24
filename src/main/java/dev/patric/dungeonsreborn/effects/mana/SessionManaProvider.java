package dev.patric.dungeonsreborn.effects.mana;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * In-memory resource provider (session-based).
 * <p>
 * Resources reset when the player goes offline and comes back unless persistence is enabled.
 */
public final class SessionManaProvider implements ManaProvider {
  private static final double EPS = 1e-9;

  private final ResourceRuleSet ruleSet;
  private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, ResourceState>> stateByPlayer =
      new ConcurrentHashMap<>();

  public SessionManaProvider(double defaultMax) {
    this(ResourceRuleSet.fromConfig(null, defaultMax));
  }

  public SessionManaProvider(ResourceRuleSet ruleSet) {
    this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
  }

  public void init(Player player) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    stateByPlayer.computeIfAbsent(id, key -> new ConcurrentHashMap<>());
    for (String resourceId : ruleSet.resourceIds()) {
      state(player, resourceId);
    }
  }

  public void reset(Player player) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    ConcurrentHashMap<String, ResourceState> map = new ConcurrentHashMap<>();
    for (String resourceId : ruleSet.resourceIds()) {
      ResourceRules rules = rules(player, resourceId);
      map.put(resourceId, new ResourceState(rules.baseMax()));
    }
    stateByPlayer.put(id, map);
  }

  public void clear(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    stateByPlayer.remove(playerId);
  }

  @Override
  public double get(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    ResourceState state = state(player, resourceId);
    double max = getMax(player, resourceId);
    return clamp(applyCaps(state.current, rules(player, resourceId)), 0.0, max);
  }

  @Override
  public double getMax(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    ResourceState state = state(player, resourceId);
    double base = state.baseMax;
    double bonus = state.maxBonus + state.classMaxBonus;
    double max = Math.max(0.0, base + bonus);
    ResourceRules rules = rules(player, resourceId);
    if (rules.hardCap() > 0.0) {
      max = Math.min(max, rules.hardCap());
    }
    return max;
  }

  @Override
  public void set(Player player, String resourceId, double value) {
    Objects.requireNonNull(player, "player");
    if (!Double.isFinite(value)) {
      value = 0.0;
    }
    ResourceState state = state(player, resourceId);
    double max = getMax(player, resourceId);
    state.current = clamp(applyCaps(value, rules(player, resourceId)), 0.0, max);
  }

  @Override
  public void setMax(Player player, String resourceId, double max) {
    Objects.requireNonNull(player, "player");
    if (!Double.isFinite(max) || max <= 0.0) {
      return;
    }
    ResourceState state = state(player, resourceId);
    state.baseMax = max;
    double total = getMax(player, resourceId);
    state.current = clamp(state.current, 0.0, total);
  }

  public void setMaxBonus(Player player, String resourceId, double bonus) {
    Objects.requireNonNull(player, "player");
    ResourceState state = state(player, resourceId);
    state.maxBonus = sanitizeBonus(bonus);
    double total = getMax(player, resourceId);
    state.current = clamp(state.current, 0.0, total);
  }

  public void setClassMaxBonus(Player player, String resourceId, double bonus) {
    Objects.requireNonNull(player, "player");
    ResourceState state = state(player, resourceId);
    state.classMaxBonus = sanitizeBonus(bonus);
    double total = getMax(player, resourceId);
    state.current = clamp(state.current, 0.0, total);
  }

  public void setRegenBonus(Player player, String resourceId, double bonus) {
    Objects.requireNonNull(player, "player");
    ResourceState state = state(player, resourceId);
    state.regenBonus = sanitizeBonus(bonus);
  }

  public void setClassRegenBonus(Player player, String resourceId, double bonus) {
    Objects.requireNonNull(player, "player");
    ResourceState state = state(player, resourceId);
    state.classRegenBonus = sanitizeBonus(bonus);
  }

  public double baseMax(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    return state(player, resourceId).baseMax;
  }

  public double maxBonus(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    return state(player, resourceId).maxBonus;
  }

  public double classMaxBonus(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    return state(player, resourceId).classMaxBonus;
  }

  public double regenBonus(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    return state(player, resourceId).regenBonus;
  }

  public double classRegenBonus(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    return state(player, resourceId).classRegenBonus;
  }

  public double baseMax(Player player) {
    return baseMax(player, DEFAULT_RESOURCE);
  }

  public double maxBonus(Player player) {
    return maxBonus(player, DEFAULT_RESOURCE);
  }

  public double classMaxBonus(Player player) {
    return classMaxBonus(player, DEFAULT_RESOURCE);
  }

  public double regenBonus(Player player) {
    return regenBonus(player, DEFAULT_RESOURCE);
  }

  public double classRegenBonus(Player player) {
    return classRegenBonus(player, DEFAULT_RESOURCE);
  }

  public void setMaxBonus(Player player, double bonus) {
    setMaxBonus(player, DEFAULT_RESOURCE, bonus);
  }

  public void setClassMaxBonus(Player player, double bonus) {
    setClassMaxBonus(player, DEFAULT_RESOURCE, bonus);
  }

  public void setRegenBonus(Player player, double bonus) {
    setRegenBonus(player, DEFAULT_RESOURCE, bonus);
  }

  public void setClassRegenBonus(Player player, double bonus) {
    setClassRegenBonus(player, DEFAULT_RESOURCE, bonus);
  }

  public boolean convert(Player player, String fromResourceId, String toResourceId, double amount) {
    Objects.requireNonNull(player, "player");
    if (amount <= 0.0) {
      return false;
    }
    ResourceRules fromRules = rules(player, fromResourceId);
    String targetId = normalizeId(toResourceId);
    Double ratio = fromRules.conversions().get(targetId);
    if (ratio == null || ratio <= 0.0) {
      return false;
    }
    Component fail = tryConsume(player, fromResourceId, amount);
    if (fail != null) {
      return false;
    }
    double added = amount * ratio;
    double max = getMax(player, targetId);
    double current = get(player, targetId);
    set(player, targetId, Math.min(max, current + added));
    return true;
  }

  @Override
  public Component tryConsume(Player player, String resourceId, double amount) {
    Objects.requireNonNull(player, "player");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    double current = get(player, resourceId);
    if (current + EPS < amount) {
      return Locales.component(player, "messages.mana.insufficient", Locales.placeholders(
          "resource", resourceName(resourceId),
          "current", format(current),
          "max", format(getMax(player, resourceId)),
          "cost", format(amount)));
    }
    set(player, resourceId, current - amount);
    return null;
  }

  @Override
  public ResourceRules rules(Player player, String resourceId) {
    Objects.requireNonNull(player, "player");
    return ruleSet.rulesFor(player, resourceId);
  }

  @Override
  public Set<String> resourceIds() {
    return ruleSet.resourceIds();
  }

  private ResourceState state(Player player, String resourceId) {
    init(player);
    String id = normalizeId(resourceId);
    ConcurrentHashMap<String, ResourceState> resources = stateByPlayer.get(player.getUniqueId());
    ResourceState state = resources.get(id);
    if (state == null) {
      ResourceRules rules = rules(player, id);
      ResourceState fresh = new ResourceState(rules.baseMax());
      ResourceState existing = resources.putIfAbsent(id, fresh);
      return existing != null ? existing : fresh;
    }
    return state;
  }

  private static double sanitizeBonus(double bonus) {
    if (!Double.isFinite(bonus) || Math.abs(bonus) < EPS) {
      return 0.0;
    }
    return bonus;
  }

  private static double applyCaps(double value, ResourceRules rules) {
    double capped = value;
    if (rules.hardCap() > 0.0) {
      capped = Math.min(capped, rules.hardCap());
    }
    if (rules.softCap() > 0.0 && capped > rules.softCap() && rules.overflowDecay() > 0.0) {
      capped = rules.softCap() + (capped - rules.softCap()) * (1.0 - rules.overflowDecay());
    }
    return capped;
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  private static String normalizeId(String id) {
    if (id == null || id.isBlank()) {
      return DEFAULT_RESOURCE;
    }
    return id.trim().toLowerCase(Locale.ROOT);
  }

  private static String resourceName(String id) {
    String name = normalizeId(id);
    if (DEFAULT_RESOURCE.equals(name)) {
      return "mana";
    }
    return name;
  }

  private static String format(double v) {
    if (Math.abs(v - Math.round(v)) < EPS) {
      return String.valueOf((long) Math.round(v));
    }
    return String.format(java.util.Locale.ROOT, "%.2f", v);
  }

  private static final class ResourceState {
    private double current;
    private double baseMax;
    private double maxBonus;
    private double regenBonus;
    private double classMaxBonus;
    private double classRegenBonus;

    private ResourceState(double baseMax) {
      this.baseMax = Math.max(0.0, baseMax);
      this.current = this.baseMax;
    }
  }
}
