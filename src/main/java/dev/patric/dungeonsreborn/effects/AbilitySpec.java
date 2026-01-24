package dev.patric.dungeonsreborn.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.conditions.Condition;
import dev.patric.dungeonsreborn.effects.costs.Cost;
import dev.patric.dungeonsreborn.effects.integration.InteractBinding;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Code-first ability definition with metadata + reusable building blocks (requirements, costs, cooldowns, triggers).
 */
public final class AbilitySpec {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  public record Requirement(Condition condition, Component failMessage) {
    public Requirement {
      Objects.requireNonNull(condition, "condition");
    }
  }

  private final String id;
  private final String name;
  private final String description;
  private final List<Requirement> requirements;
  private final List<Cost> costs;
  private final Long cooldownTicks;
  private final String cooldownKey;
  private final Action action;
  private final Action onCostFail;
  private final Action onCooldownFail;
  private final List<InteractBinding> interactBindings;
  private final int xpMin;
  private final int xpMax;

  private AbilitySpec(String id, String name, String description,
      List<Requirement> requirements,
      List<Cost> costs,
      Long cooldownTicks,
      String cooldownKey,
      Action action,
      Action onCostFail,
      Action onCooldownFail,
      List<InteractBinding> interactBindings,
      int xpMin,
      int xpMax) {
    this.id = Ids.normalize(id);
    this.name = name == null ? null : name.trim();
    this.description = description == null ? null : description.trim();
    this.requirements = requirements;
    this.costs = costs;
    this.cooldownTicks = cooldownTicks;
    this.cooldownKey = cooldownKey == null ? null : Ids.normalize(cooldownKey);
    this.action = Objects.requireNonNull(action, "action");
    this.onCostFail = onCostFail;
    this.onCooldownFail = onCooldownFail;
    this.interactBindings = interactBindings;
    this.xpMin = Math.max(0, xpMin);
    this.xpMax = Math.max(this.xpMin, xpMax);
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static AbilitySpec simple(String id, Ability ability) {
    Objects.requireNonNull(ability, "ability");
    return builder(id).action(ability::cast).build();
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public List<Requirement> requirements() {
    return requirements;
  }

  public List<Cost> costs() {
    return costs;
  }

  public Long cooldownTicks() {
    return cooldownTicks;
  }

  public String cooldownKey() {
    return cooldownKey;
  }

  public List<InteractBinding> interactBindings() {
    return interactBindings;
  }

  public int xpMin() {
    return xpMin;
  }

  public int xpMax() {
    return xpMax;
  }

  public Ability compile() {
    return ctx -> {
      for (Requirement req : requirements) {
        if (req.condition().test(ctx)) {
          continue;
        }
        if (req.failMessage() != null && ctx.caster() instanceof Player player) {
          player.sendMessage(req.failMessage());
        }
        String reason = req.failMessage() == null ? req.condition().getClass().getSimpleName() : PLAIN.serialize(req.failMessage());
        ctx.engine().recordCastFailure(ctx, EffectsEngine.CastFailureType.REQUIREMENT, reason);
        return;
      }

      for (Cost cost : costs) {
        Component fail = cost.tryApply(ctx);
        if (fail == null) {
          continue;
        }
        if (onCostFail != null) {
          onCostFail.executeWithHandle(ctx);
        } else if (ctx.caster() instanceof Player player) {
          player.sendMessage(fail);
        }
        ctx.engine().recordCastFailure(ctx, EffectsEngine.CastFailureType.COST, PLAIN.serialize(fail));
        return;
      }

      if (cooldownTicks != null && cooldownTicks > 0 && ctx.caster() instanceof Player player) {
        long effectiveCooldown = adjustCooldown(ctx, cooldownTicks);
        if (effectiveCooldown <= 0) {
          action.executeWithHandle(ctx);
          return;
        }
        String key = cooldownKey == null ? id : cooldownKey;
        if (!ctx.engine().tryStartCooldown(player.getUniqueId(), key, effectiveCooldown)) {
          long remaining = ctx.engine().cooldownRemainingTicks(player.getUniqueId(), key);
          if (onCooldownFail != null) {
            onCooldownFail.executeWithHandle(ctx);
          } else {
            player.sendMessage("§cOn cooldown (" + remaining + "t)");
          }
          ctx.engine().recordCastFailure(ctx, EffectsEngine.CastFailureType.COOLDOWN, "remaining=" + remaining + "t");
          return;
        }
      }

      action.executeWithHandle(ctx);
      awardProgressionXp(ctx);
    };
  }

  private void awardProgressionXp(CastContext ctx) {
    if (xpMax <= 0) {
      return;
    }
    if (!(ctx.caster() instanceof Player player)) {
      return;
    }
    if (!(ctx.plugin() instanceof DungeonsRebornPlugin plugin)) {
      return;
    }
    ProgressionService progression = plugin.progressionService();
    if (progression == null) {
      return;
    }
    int award = xpMin == xpMax ? xpMax : ThreadLocalRandom.current().nextInt(xpMin, xpMax + 1);
    if (award <= 0) {
      return;
    }
    progression.awardForEffect(player, award, id);
  }

  private static long adjustCooldown(CastContext ctx, long baseTicks) {
    double mult = readNumber(ctx, "upgrade_cooldown_mult", 1.0);
    double add = readNumber(ctx, "upgrade_cooldown_add", 0.0);
    double value = baseTicks * mult + add;
    if (!Double.isFinite(value)) {
      return baseTicks;
    }
    if (value <= 0.0) {
      return 0L;
    }
    return Math.max(1L, Math.round(value));
  }

  private static double readNumber(CastContext ctx, String key, double fallback) {
    Object value = ctx.variables().get(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String raw) {
      try {
        return Double.parseDouble(raw.trim());
      } catch (Exception ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  public static final class Builder {
    private final String id;
    private String name;
    private String description;
    private final ArrayList<Requirement> requirements = new ArrayList<>();
    private final ArrayList<Cost> costs = new ArrayList<>();
    private Long cooldownTicks;
    private String cooldownKey;
    private Action action;
    private Action onCostFail;
    private Action onCooldownFail;
    private final ArrayList<InteractBinding> interactBindings = new ArrayList<>();
    private int xpMin;
    private int xpMax;

    private Builder(String id) {
      this.id = Objects.requireNonNull(id, "id");
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder require(Condition condition) {
      return require(condition, null);
    }

    public Builder require(Condition condition, Component failMessage) {
      requirements.add(new Requirement(condition, failMessage));
      return this;
    }

    public Builder cost(Cost cost) {
      costs.add(Objects.requireNonNull(cost, "cost"));
      return this;
    }

    public Builder cooldownTicks(long ticks) {
      return cooldownTicks(ticks, null);
    }

    public Builder cooldownTicks(long ticks, String key) {
      if (ticks <= 0) {
        throw new IllegalArgumentException("ticks must be > 0");
      }
      this.cooldownTicks = ticks;
      this.cooldownKey = key;
      return this;
    }

    public Builder action(Action action) {
      this.action = Objects.requireNonNull(action, "action");
      return this;
    }

    public Builder onCostFail(Action action) {
      this.onCostFail = action;
      return this;
    }

    public Builder onCooldownFail(Action action) {
      this.onCooldownFail = action;
      return this;
    }

    public Builder triggerInteract(String bindingId, InteractTrigger trigger, ItemMatcher matcher) {
      return triggerInteract(bindingId, trigger, matcher, false, null, true);
    }

    public Builder triggerInteract(String bindingId, InteractTrigger trigger, ItemMatcher matcher,
        boolean requireSneaking, String permission, boolean cancelEvent) {
      Objects.requireNonNull(bindingId, "bindingId");
      Objects.requireNonNull(trigger, "trigger");
      Objects.requireNonNull(matcher, "matcher");
      interactBindings.add(InteractBinding.builder(bindingId)
          .trigger(trigger)
          .ability(this.id)
          .item(matcher)
          .requireSneaking(requireSneaking)
          .permission(permission)
          .cancelEvent(cancelEvent)
          .build());
      return this;
    }

    public Builder xpAward(int min, int max) {
      this.xpMin = Math.max(0, min);
      this.xpMax = Math.max(this.xpMin, max);
      return this;
    }

    public AbilitySpec build() {
      if (action == null) {
        throw new IllegalStateException("action not set");
      }
      String normalized = Ids.normalize(id);
      return new AbilitySpec(
          normalized,
          name,
          description,
          Collections.unmodifiableList(new ArrayList<>(requirements)),
          Collections.unmodifiableList(new ArrayList<>(costs)),
          cooldownTicks,
          cooldownKey,
          action,
          onCostFail,
          onCooldownFail,
          Collections.unmodifiableList(new ArrayList<>(interactBindings)),
          xpMin,
          xpMax);
    }
  }
}
