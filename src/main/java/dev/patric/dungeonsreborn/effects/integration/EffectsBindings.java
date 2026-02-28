package dev.patric.dungeonsreborn.effects.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.items.ItemConsumeMode;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.combat.CombatEventBinding;
import dev.patric.dungeonsreborn.effects.combat.CombatEventContext;
import dev.patric.dungeonsreborn.effects.combat.CombatEventSource;
import dev.patric.dungeonsreborn.effects.combat.CombatEventType;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeModifierType;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeSpellBindingSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeStatusEffectSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeActivator;

public final class EffectsBindings implements Listener {
  private final EffectsEngine engine;
  private final List<InteractBinding> interactBindings = new ArrayList<>();
  private final List<PassiveBinding> passiveBindings = new ArrayList<>();
  private final List<EventBinding> eventBindings = new ArrayList<>();
  private final List<CombatEventBinding> combatEventBindings = new ArrayList<>();
  private final java.util.Map<java.util.UUID, Long> lastHandledInteractTickByPlayer = new java.util.HashMap<>();
  private static final long PASSIVE_TICK_PERIOD = 1L;
  private static final long ITEM_PASSIVE_PERIOD = 20L;

  public EffectsBindings(EffectsEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
    engine.runRepeating(PASSIVE_TICK_PERIOD, PASSIVE_TICK_PERIOD, this::tickPassives);
  }

  public void register(InteractBinding binding) {
    interactBindings.add(Objects.requireNonNull(binding, "binding"));
  }

  public boolean unregister(String bindingId) {
    Objects.requireNonNull(bindingId, "bindingId");
    return interactBindings.removeIf(b -> b.id().equals(bindingId));
  }

  public void registerPassive(PassiveBinding binding) {
    passiveBindings.add(Objects.requireNonNull(binding, "binding"));
  }

  public boolean unregisterPassive(String bindingId) {
    Objects.requireNonNull(bindingId, "bindingId");
    return passiveBindings.removeIf(b -> b.id().equals(bindingId));
  }

  public void registerEvent(EventBinding binding) {
    eventBindings.add(Objects.requireNonNull(binding, "binding"));
  }

  public boolean unregisterEvent(String bindingId) {
    Objects.requireNonNull(bindingId, "bindingId");
    return eventBindings.removeIf(b -> b.id().equals(bindingId));
  }

  public void registerCombatEvent(CombatEventBinding binding) {
    Objects.requireNonNull(binding, "binding");
    combatEventBindings.add(binding);
    engine.combatDispatcher().register(binding);
  }

  public boolean unregisterCombatEvent(String bindingId) {
    Objects.requireNonNull(bindingId, "bindingId");
    boolean removed = combatEventBindings.removeIf(b -> b.id().equals(bindingId));
    engine.combatDispatcher().unregister(bindingId);
    return removed;
  }

  public void register(AbilitySpec spec) {
    Objects.requireNonNull(spec, "spec");
    for (InteractBinding binding : spec.interactBindings()) {
      register(binding);
    }
  }

  public List<String> explain(Player player, InteractTrigger trigger) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(trigger, "trigger");

    ArrayList<String> out = new ArrayList<>();
    ItemStack item = player.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      out.add("Hold an item in your main hand.");
      return out;
    }

    out.add("Item: " + item.getType().name());
    out.add("Trigger: " + trigger.name());

    List<String> markerIds = switch (trigger) {
      case RIGHT_CLICK -> ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES);
      case LEFT_CLICK -> ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES);
      case SHOOT -> java.util.List.of();
    };
    if (markerIds.isEmpty()) {
      out.add("Item marker bindings: (none)");
    } else {
      out.add("Item marker bindings:");
      for (String id : markerIds) {
        out.add("- " + id + (engine.hasAbility(id) ? "" : " (not registered)"));
      }
    }

    int totalForTrigger = 0;
    int shown = 0;
    out.add("Interact bindings (first 25):");
    for (InteractBinding binding : interactBindings) {
      if (binding.trigger() != trigger) {
        continue;
      }
      totalForTrigger++;
      if (shown++ >= 25) {
        continue;
      }

      String reason = null;
      if (binding.requireSneaking() && !player.isSneaking()) {
        reason = "needs sneaking";
      } else if (binding.requiredPermission() != null && !player.hasPermission(binding.requiredPermission())) {
        reason = "missing permission " + binding.requiredPermission();
      } else if (!binding.itemMatcher().matches(player, item)) {
        reason = "item mismatch";
      }

      if (reason == null) {
        out.add("OK " + binding.id() + " -> " + binding.abilityId());
      } else {
        out.add("NO " + binding.id() + " -> " + binding.abilityId() + " (" + reason + ")");
      }
    }
    out.add("Total bindings for this trigger: " + totalForTrigger);
    return out;
  }

  public List<InteractBinding> interactBindings() {
    return Collections.unmodifiableList(interactBindings);
  }

  public List<PassiveBinding> passiveBindings() {
    return Collections.unmodifiableList(passiveBindings);
  }

  public List<EventBinding> eventBindings() {
    return Collections.unmodifiableList(eventBindings);
  }

  public List<CombatEventBinding> combatEventBindings() {
    return Collections.unmodifiableList(combatEventBindings);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHit(EntityDamageByEntityEvent event) {
    Player attacker = resolvePlayerAttacker(event.getDamager());
    LivingEntity victim = event.getEntity() instanceof LivingEntity living ? living : null;
    LivingEntity sourceAttacker = resolveLivingAttacker(event.getDamager());
    if (sourceAttacker != null && victim != null) {
      CombatEventSource source = event.getDamager() instanceof Projectile ? CombatEventSource.PROJECTILE : CombatEventSource.MELEE;
      engine.combatDispatcher().dispatch(new CombatEventContext(
          engine.tickNow(),
          CombatEventType.ON_ATTACK_ATTEMPT,
          sourceAttacker,
          victim,
          victim,
          event.getDamager(),
          source,
          event.getFinalDamage(),
          isLikelyCritical(sourceAttacker),
          false,
          false,
          null,
          dev.patric.dungeonsreborn.effects.damage.DamageCause.DIRECT,
          null,
          null));
      if (event.getFinalDamage() > 0.0) {
        engine.combatDispatcher().dispatch(new CombatEventContext(
            engine.tickNow(),
            CombatEventType.ON_ATTACK_HIT,
            sourceAttacker,
            victim,
            victim,
            event.getDamager(),
            source,
            event.getFinalDamage(),
            isLikelyCritical(sourceAttacker),
            false,
            false,
            null,
            dev.patric.dungeonsreborn.effects.damage.DamageCause.DIRECT,
            null,
            null));
        engine.combatDispatcher().dispatch(new CombatEventContext(
            engine.tickNow(),
            CombatEventType.ON_HIT_TAKEN,
            sourceAttacker,
            victim,
            victim,
            event.getDamager(),
            source,
            event.getFinalDamage(),
            false,
            false,
            false,
            null,
            dev.patric.dungeonsreborn.effects.damage.DamageCause.DIRECT,
            null,
            null));
      }
    }
    if (attacker != null && event.getFinalDamage() > 0.0) {
      triggerEvent(EventTrigger.ON_HIT, attacker);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDodge(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    if (!event.isCancelled() && event.getFinalDamage() > 0.0) {
      return;
    }
    LivingEntity attacker = resolveLivingAttacker(event.getDamager());
    engine.combatDispatcher().dispatch(new CombatEventContext(
        engine.tickNow(),
        CombatEventType.ON_DODGE,
        attacker,
        player,
        player,
        event.getDamager(),
        event.getDamager() instanceof Projectile ? CombatEventSource.PROJECTILE : CombatEventSource.MELEE,
        0.0,
        false,
        player.isBlocking(),
        true,
        null,
        dev.patric.dungeonsreborn.effects.damage.DamageCause.DIRECT,
        null,
        null));
    triggerEvent(EventTrigger.ON_DODGE, player);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onKill(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) {
      return;
    }
    LivingEntity victim = event.getEntity();
    engine.combatDispatcher().dispatch(new CombatEventContext(
        engine.tickNow(),
        CombatEventType.ON_ATTACK_KILL,
        killer,
        victim,
        victim,
        killer,
        CombatEventSource.MELEE,
        0.0,
        false,
        false,
        false,
        null,
        dev.patric.dungeonsreborn.effects.damage.DamageCause.DIRECT,
        null,
        null));
    triggerEvent(EventTrigger.ON_KILL, killer);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSprint(PlayerToggleSprintEvent event) {
    if (!event.isSprinting()) {
      return;
    }
    engine.combatDispatcher().dispatch(new CombatEventContext(
        engine.tickNow(),
        CombatEventType.ON_SPRINT,
        event.getPlayer(),
        null,
        null,
        event.getPlayer(),
        CombatEventSource.UNKNOWN,
        0.0,
        false,
        false,
        false,
        null,
        dev.patric.dungeonsreborn.effects.damage.DamageCause.DIRECT,
        null,
        null));
    triggerEvent(EventTrigger.ON_SPRINT, event.getPlayer());
  }

  private void tickPassives() {
    if (passiveBindings.isEmpty()) {
      tickItemPassives();
      return;
    }
    long now = engine.tickNow();
    for (PassiveBinding binding : passiveBindings) {
      long period = Math.max(1L, binding.periodTicks());
      if ((now % period) != 0L) {
        continue;
      }
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (binding.requireSneaking() && !player.isSneaking()) {
          continue;
        }
        if (binding.requiredPermission() != null && !player.hasPermission(binding.requiredPermission())) {
          continue;
        }
        if (!matchesPassive(binding, player)) {
          continue;
        }
        try {
          engine.cast(binding.abilityId(), player);
        } catch (IllegalArgumentException ex) {
          if (engine.isDebugEnabled()) {
            engine.debug("passive ability invalid: " + binding.abilityId() + " (" + ex.getMessage() + ")");
          }
        }
      }
    }
    tickItemPassives();
  }

  private void triggerEvent(EventTrigger trigger, Player player) {
    if (eventBindings.isEmpty()) {
      return;
    }
    for (EventBinding binding : eventBindings) {
      if (binding.trigger() != trigger) {
        continue;
      }
      if (binding.requireSneaking() && !player.isSneaking()) {
        continue;
      }
      if (binding.requiredPermission() != null && !player.hasPermission(binding.requiredPermission())) {
        continue;
      }
      if (!binding.playerPredicate().test(player)) {
        continue;
      }
      try {
        engine.cast(binding.abilityId(), player);
      } catch (IllegalArgumentException ex) {
        if (engine.isDebugEnabled()) {
          engine.debug("event ability invalid: " + binding.abilityId() + " (" + ex.getMessage() + ")");
        }
      }
    }
  }

  private static Player resolvePlayerAttacker(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  private static LivingEntity resolveLivingAttacker(Entity damager) {
    if (damager instanceof LivingEntity living) {
      return living;
    }
    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
      return living;
    }
    return null;
  }

  private static boolean isLikelyCritical(LivingEntity attacker) {
    if (!(attacker instanceof Player player)) {
      return false;
    }
    return player.getFallDistance() > 0.0f && !player.isInWater() && !player.isInsideVehicle();
  }

  private void tickItemPassives() {
    long now = engine.tickNow();
    if ((now % ITEM_PASSIVE_PERIOD) != 0L) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      EquipmentSlot[] slots = {
          EquipmentSlot.HAND,
          EquipmentSlot.OFF_HAND,
          EquipmentSlot.HEAD,
          EquipmentSlot.CHEST,
          EquipmentSlot.LEGS,
          EquipmentSlot.FEET
      };
      for (EquipmentSlot slot : slots) {
        ItemStack item = itemForSlot(player, slot);
        if (item == null || item.getType().isAir()) {
          continue;
        }
        List<UpgradeSpellBindingSpec> upgradeBindings = upgradeBindingsFor(item, UpgradeActivator.PASSIVE);
        HashSet<String> upgradeAbilityIds = collectUpgradeAbilityIds(upgradeBindings);
        for (UpgradeSpellBindingSpec binding : upgradeBindings) {
          if (!matchesUpgradeConditions(player, binding)) {
            continue;
          }
          if (!tryStartUpgradeCooldown(player, item, binding)) {
            continue;
          }
          if (!engine.hasAbility(binding.abilityId())) {
            if (engine.isDebugEnabled()) {
              engine.debug("item passive ability not registered: " + binding.abilityId());
            }
            continue;
          }
          castWithItem(player, binding.abilityId(), item, true, binding);
          applyUpgradeConsume(player, slot, item, binding);
        }
        for (String abilityId : ItemMarkers.getStringList(item, ItemMarkers.PASSIVE_ABILITIES)) {
          if (upgradeAbilityIds.contains(abilityId)) {
            continue;
          }
          try {
            if (!engine.hasAbility(abilityId)) {
              if (engine.isDebugEnabled()) {
                engine.debug("item passive ability not registered: " + abilityId);
              }
              continue;
            }
            castWithItem(player, abilityId, item, true);
          } catch (IllegalArgumentException ex) {
            if (engine.isDebugEnabled()) {
              engine.debug("item passive ability invalid: " + abilityId + " (" + ex.getMessage() + ")");
            }
          }
        }
      }
    }
  }

  private void castWithItem(Player player, String abilityId, ItemStack item, boolean allowSecondary) {
    castWithItem(player, abilityId, item, allowSecondary, null);
  }

  private void castWithItem(Player player, String abilityId, ItemStack item, boolean allowSecondary,
      UpgradeSpellBindingSpec binding) {
    if (!engine.hasAbility(abilityId)) {
      if (engine.isDebugEnabled()) {
        engine.debug("item ability not registered: " + abilityId);
      }
      return;
    }
    Location origin = player.getEyeLocation();
    Vector direction = origin.getDirection();
    List<String> secondary = ItemMarkers.getStringList(item, ItemMarkers.UPGRADE_SECONDARY_ABILITIES);
    List<UpgradeStatusEffectSpec> effects = UpgradeStatusEffectSpec.parseRecords(
        ItemMarkers.getUpgradeStatusEffects(item));
    engine.castWithContext(abilityId, player, origin, direction, item,
        ctx -> applyUpgradeState(ctx, item, effects, binding));
    if (allowSecondary && !secondary.isEmpty()) {
      for (String secondaryId : secondary) {
        if (secondaryId.equals(abilityId)) {
          continue;
        }
        castWithItem(player, secondaryId, item, false);
      }
    }
  }

  private void applyUpgradeState(CastContext ctx, ItemStack item, List<UpgradeStatusEffectSpec> effects,
      UpgradeSpellBindingSpec binding) {
    java.util.Map<String, Double> modifiers = ItemMarkers.getUpgradeModifiers(item);
    for (UpgradeModifierType type : UpgradeModifierType.values()) {
      double value = modifiers.getOrDefault(type.key(), type.defaultValue());
      ctx.variables().put("upgrade_" + type.key(), value);
    }
    if (effects != null && !effects.isEmpty()) {
      ctx.variables().put(Vars.UPGRADE_STATUS_EFFECTS, effects);
    }
    if (binding != null) {
      if (binding.manaCost() != null && binding.manaCost() > 0) {
        ctx.variables().put("upgrade_mana_mult", 0.0);
        ctx.variables().put("upgrade_mana_add", binding.manaCost().doubleValue());
      }
      if (binding.cooldownTicks() != null && binding.cooldownTicks() > 0) {
        ctx.variables().put("upgrade_cooldown_mult", 0.0);
        ctx.variables().put("upgrade_cooldown_add", binding.cooldownTicks().doubleValue());
      }
    }
  }

  private static List<UpgradeSpellBindingSpec> upgradeBindingsFor(ItemStack item, UpgradeActivator activator) {
    if (item == null || activator == null) {
      return List.of();
    }
    List<UpgradeSpellBindingSpec> bindings = UpgradeSpellBindingSpec.parseRecords(
        ItemMarkers.getUpgradeSpellBindings(item));
    if (bindings.isEmpty()) {
      return List.of();
    }
    ArrayList<UpgradeSpellBindingSpec> out = new ArrayList<>();
    for (UpgradeSpellBindingSpec binding : bindings) {
      if (binding.activator() == activator) {
        out.add(binding);
      }
    }
    return out;
  }

  private static HashSet<String> collectUpgradeAbilityIds(List<UpgradeSpellBindingSpec> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return new HashSet<>();
    }
    HashSet<String> out = new HashSet<>();
    for (UpgradeSpellBindingSpec binding : bindings) {
      out.add(binding.abilityId());
    }
    return out;
  }

  private static boolean matchesUpgradeConditions(Player player, UpgradeSpellBindingSpec binding) {
    if (binding.requireSneaking() && !player.isSneaking()) {
      return false;
    }
    if (binding.requireSprinting() && !player.isSprinting()) {
      return false;
    }
    boolean onGround = isOnGround(player);
    if (binding.requireAirborne() && onGround) {
      return false;
    }
    if (binding.requireOnGround() && !onGround) {
      return false;
    }
    return true;
  }

  private static boolean isOnGround(Player player) {
    if (player == null) {
      return false;
    }
    Block block = player.getLocation().getBlock();
    if (block.getType().isSolid()) {
      return true;
    }
    Block below = block.getRelative(BlockFace.DOWN);
    return below.getType().isSolid();
  }

  private boolean tryStartUpgradeCooldown(Player player, ItemStack item, UpgradeSpellBindingSpec binding) {
    if (binding.cooldownTicks() == null || binding.cooldownTicks() <= 0) {
      return true;
    }
    String key = upgradeCooldownKey(item, binding);
    return engine.tryStartCooldown(player.getUniqueId(), key, binding.cooldownTicks());
  }

  private static String upgradeCooldownKey(ItemStack item, UpgradeSpellBindingSpec binding) {
    String base = "upgrade_spell:" + binding.abilityId() + ":" + binding.activator().name();
    return switch (binding.cooldownScope()) {
      case PER_PLAYER -> base;
      case PER_UPGRADE -> "upgrade:" + binding.upgradeId() + ":" + base;
      case PER_ITEM -> "upgrade_item:" + ItemMarkers.getOrCreateItemInstanceId(item) + ":" + base;
    };
  }

  private void applyUpgradeConsume(Player player, EquipmentSlot slot, ItemStack item, UpgradeSpellBindingSpec binding) {
    Integer durability = binding.durabilityCost();
    Integer consume = binding.consumeAmount();
    if ((durability == null || durability <= 0) && (consume == null || consume <= 0)) {
      return;
    }
    if (slot != null && slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND) {
      return;
    }
    int amount = consume == null || consume <= 0 ? 0 : consume;
    if (durability != null && durability > 0) {
      consumeDurability(player, slot == null ? EquipmentSlot.HAND : slot, item, durability);
      return;
    }
    ItemConsumeMode mode = ItemMarkers.getConsumeMode(item);
    if (mode == ItemConsumeMode.NONE) {
      mode = ItemConsumeMode.STACK;
    }
    EquipmentSlot hand = slot == null ? EquipmentSlot.HAND : slot;
    switch (mode) {
      case STACK -> consumeStack(player, hand, item, amount);
      case DURABILITY -> consumeDurability(player, hand, item, amount);
      default -> {
      }
    }
  }

  private static boolean matchesPassive(PassiveBinding binding, Player player) {
    for (EquipmentSlot slot : binding.slots()) {
      ItemStack item = itemForSlot(player, slot);
      if (binding.itemMatcher().matches(player, item)) {
        return true;
      }
    }
    return false;
  }

  private static ItemStack itemForSlot(Player player, EquipmentSlot slot) {
    return switch (slot) {
      case HAND -> player.getInventory().getItemInMainHand();
      case OFF_HAND -> player.getInventory().getItemInOffHand();
      case HEAD -> player.getInventory().getHelmet();
      case CHEST -> player.getInventory().getChestplate();
      case LEGS -> player.getInventory().getLeggings();
      case FEET -> player.getInventory().getBoots();
      default -> null;
    };
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void onInteract(PlayerInteractEvent event) {
    // Ignore off-hand duplicates (OFF_HAND often fires a second identical interact).
    if (event.getHand() == EquipmentSlot.OFF_HAND) {
      return;
    }

    Player player = event.getPlayer();
    long nowTick = engine.tickNow();
    Long last = lastHandledInteractTickByPlayer.put(player.getUniqueId(), nowTick);
    if (last != null && last == nowTick) {
      // Re-entrancy / double-fire guard: don't process multiple interact events for the same player in the same tick.
      return;
    }

    ItemStack item = event.getItem();
    if (item == null) {
      EquipmentSlot hand = event.getHand();
      if (hand == null || hand == EquipmentSlot.HAND) {
        item = player.getInventory().getItemInMainHand();
      } else if (hand == EquipmentSlot.OFF_HAND) {
        item = player.getInventory().getItemInOffHand();
      }
    }

    var action = event.getAction();
    boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    boolean deferConsumeRightClick = rightClick && isConsumeTriggeredItem(item);

    if (rightClick && item != null && isItemOnCooldown(player, item) && ItemMarkers.getItemId(item) == null) {
      event.setCancelled(true);
      return;
    }

    boolean castAny = false;
    boolean shouldCancel = false;
    boolean boundRightClick = false;
    boolean useDefaultConsume = false;
    ArrayList<UpgradeSpellBindingSpec> customConsumes = new ArrayList<>();

    // Item-bound ability list (ability set) - pragmatic ExecutableItems-style binding.
    // This runs before explicit InteractBindings so items can be configured without registering Java bindings.
    if (item != null && !item.getType().isAir()) {
      if (rightClick || leftClick) {
        boolean sneaking = player.isSneaking();
        NamespacedKey key;
        List<String> ids;
        List<UpgradeSpellBindingSpec> upgradeBindings;
        if (rightClick) {
          key = sneaking ? ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES : ItemMarkers.RIGHT_CLICK_ABILITIES;
          ids = ItemMarkers.getStringList(item, key);
          upgradeBindings = upgradeBindingsFor(item, sneaking ? UpgradeActivator.SHIFT_RIGHT_CLICK : UpgradeActivator.RIGHT_CLICK);
          if (ids.isEmpty() && sneaking) {
            ids = ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES);
            if (upgradeBindings.isEmpty()) {
              upgradeBindings = upgradeBindingsFor(item, UpgradeActivator.RIGHT_CLICK);
            }
          }
        } else {
          key = sneaking ? ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES : ItemMarkers.LEFT_CLICK_ABILITIES;
          ids = ItemMarkers.getStringList(item, key);
          upgradeBindings = upgradeBindingsFor(item, sneaking ? UpgradeActivator.SHIFT_LEFT_CLICK : UpgradeActivator.LEFT_CLICK);
          if (ids.isEmpty() && sneaking) {
            ids = ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES);
            if (upgradeBindings.isEmpty()) {
              upgradeBindings = upgradeBindingsFor(item, UpgradeActivator.LEFT_CLICK);
            }
          }
        }
        HashSet<String> upgradeAbilityIds = collectUpgradeAbilityIds(upgradeBindings);
        if (!upgradeAbilityIds.isEmpty() && !ids.isEmpty()) {
          ArrayList<String> filtered = new ArrayList<>(ids);
          filtered.removeIf(upgradeAbilityIds::contains);
          ids = filtered;
        }
        if (!upgradeBindings.isEmpty()) {
          if (rightClick && !deferConsumeRightClick) {
            boundRightClick = true;
          }
          if (!deferConsumeRightClick) {
            event.setCancelled(true);
            for (UpgradeSpellBindingSpec binding : upgradeBindings) {
              if (!matchesUpgradeConditions(player, binding)) {
                continue;
              }
              if (!tryStartUpgradeCooldown(player, item, binding)) {
                continue;
              }
              if (!engine.hasAbility(binding.abilityId())) {
                continue;
              }
              try {
                castWithItem(player, binding.abilityId(), item, true, binding);
                castAny = true;
                if ((binding.durabilityCost() != null && binding.durabilityCost() > 0)
                    || (binding.consumeAmount() != null && binding.consumeAmount() > 0)) {
                  customConsumes.add(binding);
                } else {
                  useDefaultConsume = true;
                }
              } catch (IllegalArgumentException ex) {
              }
            }
          }
        }
        if (!ids.isEmpty()) {
          if (rightClick && !deferConsumeRightClick) {
            boundRightClick = true;
          }
          if (!deferConsumeRightClick) {
            event.setCancelled(true);
            for (String abilityId : ids) {
              try {
                if (!engine.hasAbility(abilityId)) {
                  continue;
                }
                if (engine.cooldownRemainingTicks(player.getUniqueId(), abilityId) > 0L) {
                  continue;
                }
                castWithItem(player, abilityId, item, true);
                castAny = true;
                useDefaultConsume = true;
              } catch (IllegalArgumentException ex) {
              }
            }
          }
        }
      }
    }

    for (InteractBinding binding : interactBindings) {
      boolean isRightClickBinding = binding.trigger() == InteractTrigger.RIGHT_CLICK;
      boolean itemMatches = item != null && binding.itemMatcher().matches(player, item);
      if (isRightClickBinding && itemMatches && !deferConsumeRightClick) {
        boundRightClick = true;
      }
      if (!binding.trigger().matches(event)) {
        continue;
      }
      if (deferConsumeRightClick && isRightClickBinding) {
        continue;
      }
      if (binding.requireSneaking() && !player.isSneaking()) {
        continue;
      }
      if (binding.requiredPermission() != null && !player.hasPermission(binding.requiredPermission())) {
        continue;
      }
      if (!binding.itemMatcher().matches(player, item)) {
        continue;
      }

      if (binding.cancelEvent()) {
        shouldCancel = true;
      }
      if (engine.cooldownRemainingTicks(player.getUniqueId(), binding.abilityId()) > 0L) {
        continue;
      }
      castWithItem(player, binding.abilityId(), item, true);
      castAny = true;
      useDefaultConsume = true;
    }
    if (castAny && shouldCancel) {
      event.setCancelled(true);
    }
    if (item != null && action == Action.RIGHT_CLICK_BLOCK && item.getType().isBlock() && boundRightClick) {
      event.setCancelled(true);
      event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
      event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }
    if (castAny && item != null) {
      // Custom DR items are governed by ability cooldown keys.
      // Material cooldown is per-Material and can incorrectly couple distinct items
      // (e.g. heavy/quick variants that share the same base material).
      if (ItemMarkers.getItemId(item) == null) {
        applyItemCooldown(player, item);
      }
      if (useDefaultConsume) {
        consumeItem(player, event.getHand(), item);
      }
      if (!customConsumes.isEmpty()) {
        EquipmentSlot slot = event.getHand();
        for (UpgradeSpellBindingSpec binding : customConsumes) {
          applyUpgradeConsume(player, slot, item, binding);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    if (!isConsumeTriggeredItem(item)) {
      return;
    }

    boolean sneaking = player.isSneaking();
    NamespacedKey key = sneaking ? ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES : ItemMarkers.RIGHT_CLICK_ABILITIES;
    List<String> ids = ItemMarkers.getStringList(item, key);
    List<UpgradeSpellBindingSpec> upgradeBindings = upgradeBindingsFor(
        item, sneaking ? UpgradeActivator.SHIFT_RIGHT_CLICK : UpgradeActivator.RIGHT_CLICK);
    if (ids.isEmpty() && sneaking) {
      ids = ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES);
      if (upgradeBindings.isEmpty()) {
        upgradeBindings = upgradeBindingsFor(item, UpgradeActivator.RIGHT_CLICK);
      }
    }

    HashSet<String> upgradeAbilityIds = collectUpgradeAbilityIds(upgradeBindings);
    if (!upgradeAbilityIds.isEmpty() && !ids.isEmpty()) {
      ArrayList<String> filtered = new ArrayList<>(ids);
      filtered.removeIf(upgradeAbilityIds::contains);
      ids = filtered;
    }

    HashSet<String> casted = new HashSet<>();

    for (UpgradeSpellBindingSpec binding : upgradeBindings) {
      if (!matchesUpgradeConditions(player, binding)) {
        continue;
      }
      if (!tryStartUpgradeCooldown(player, item, binding)) {
        continue;
      }
      if (!engine.hasAbility(binding.abilityId())) {
        continue;
      }
      if (!casted.add(binding.abilityId())) {
        continue;
      }
      try {
        castWithItem(player, binding.abilityId(), item, true, binding);
      } catch (IllegalArgumentException ignored) {
      }
    }

    for (String abilityId : ids) {
      try {
        if (!engine.hasAbility(abilityId)) {
          continue;
        }
        if (!casted.add(abilityId)) {
          continue;
        }
        if (engine.cooldownRemainingTicks(player.getUniqueId(), abilityId) > 0L) {
          continue;
        }
        castWithItem(player, abilityId, item, true);
      } catch (IllegalArgumentException ignored) {
      }
    }

    for (InteractBinding binding : interactBindings) {
      if (binding.trigger() != InteractTrigger.RIGHT_CLICK) {
        continue;
      }
      if (binding.requireSneaking() && !player.isSneaking()) {
        continue;
      }
      if (binding.requiredPermission() != null && !player.hasPermission(binding.requiredPermission())) {
        continue;
      }
      if (!binding.itemMatcher().matches(player, item)) {
        continue;
      }
      String abilityId = binding.abilityId();
      if (!casted.add(abilityId)) {
        continue;
      }
      if (!engine.hasAbility(abilityId)) {
        continue;
      }
      if (engine.cooldownRemainingTicks(player.getUniqueId(), abilityId) > 0L) {
        continue;
      }
      try {
        castWithItem(player, abilityId, item, true);
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void onShoot(EntityShootBowEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    ItemStack weapon = event.getBow();
    if (weapon == null || weapon.getType().isAir()) {
      return;
    }

    boolean castAny = false;
    boolean shouldCancel = false;
    for (InteractBinding binding : interactBindings) {
      if (binding.trigger() != InteractTrigger.SHOOT) {
        continue;
      }
      if (binding.requireSneaking() && !player.isSneaking()) {
        continue;
      }
      if (binding.requiredPermission() != null && !player.hasPermission(binding.requiredPermission())) {
        continue;
      }
      if (!binding.itemMatcher().matches(player, weapon)) {
        continue;
      }
      if (binding.cancelEvent()) {
        shouldCancel = true;
      }
      if (engine.cooldownRemainingTicks(player.getUniqueId(), binding.abilityId()) > 0L) {
        continue;
      }
      try {
        castWithItem(player, binding.abilityId(), weapon, true);
        castAny = true;
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (castAny && shouldCancel) {
      event.setCancelled(true);
    }
  }

  private static void consumeItem(Player player, EquipmentSlot hand, ItemStack item) {
    ItemConsumeMode mode = ItemMarkers.getConsumeMode(item);
    if (mode == ItemConsumeMode.NONE) {
      return;
    }
    int amount = ItemMarkers.getConsumeAmount(item);
    if (amount <= 0) {
      amount = 1;
    }
    EquipmentSlot slot = hand == null ? EquipmentSlot.HAND : hand;
    switch (mode) {
      case STACK -> consumeStack(player, slot, item, amount);
      case DURABILITY -> consumeDurability(player, slot, item, amount);
      default -> {
      }
    }
  }

  private static boolean isItemOnCooldown(Player player, ItemStack item) {
    long ticks = player.getCooldown(item.getType());
    return ticks > 0L;
  }

  private static void applyItemCooldown(Player player, ItemStack item) {
    long ticks = useCooldownTicks(item);
    if (ticks <= 0L) {
      return;
    }
    if (player.getCooldown(item.getType()) < ticks) {
      player.setCooldown(item.getType(), (int) Math.min(Integer.MAX_VALUE, ticks));
    }
  }

  private static long useCooldownTicks(ItemStack item) {
    if (item == null) {
      return 0L;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return 0L;
    }
    UseCooldownComponent cooldown = meta.getUseCooldown();
    if (cooldown == null) {
      return 0L;
    }
    double seconds = cooldown.getCooldownSeconds();
    if (!Double.isFinite(seconds) || seconds <= 0.0) {
      return 0L;
    }
    return Math.max(1L, Math.round(seconds * 20.0));
  }

  private static boolean isConsumeTriggeredItem(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    try {
      if (item.getData(io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE) != null) {
        return true;
      }
      if (item.getData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD) != null) {
        return true;
      }
    } catch (Throwable ignored) {
      // Fallback for API mismatch: rely on plugin consume markers only.
    }
    return ItemMarkers.getConsumeMode(item) != ItemConsumeMode.NONE;
  }

  private static void consumeStack(Player player, EquipmentSlot hand, ItemStack item, int amount) {
    int remaining = item.getAmount() - amount;
    if (remaining <= 0) {
      setHandItem(player, hand, null);
      return;
    }
    ItemStack updated = item.clone();
    updated.setAmount(remaining);
    setHandItem(player, hand, updated);
  }

  private static void consumeDurability(Player player, EquipmentSlot hand, ItemStack item, int amount) {
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) {
      return;
    }
    int max = item.getType().getMaxDurability();
    if (max <= 0) {
      return;
    }
    int next = damageable.getDamage() + amount;
    if (next >= max) {
      setHandItem(player, hand, null);
      return;
    }
    ItemStack updated = item.clone();
    ItemMeta updatedMeta = updated.getItemMeta();
    if (updatedMeta instanceof org.bukkit.inventory.meta.Damageable updatedDamageable) {
      updatedDamageable.setDamage(next);
      updated.setItemMeta(updatedMeta);
    }
    setHandItem(player, hand, updated);
  }

  private static void setHandItem(Player player, EquipmentSlot hand, ItemStack item) {
    if (hand == EquipmentSlot.OFF_HAND) {
      player.getInventory().setItemInOffHand(item);
    } else {
      player.getInventory().setItemInMainHand(item);
    }
  }
}
