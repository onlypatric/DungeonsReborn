package dev.patric.dungeonsreborn.effects.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.items.ItemConsumeMode;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeModifierType;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeStatusEffectSpec;

public final class EffectsBindings implements Listener {
  private final EffectsEngine engine;
  private final List<InteractBinding> interactBindings = new ArrayList<>();
  private final List<PassiveBinding> passiveBindings = new ArrayList<>();
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

    var markerKey = trigger == InteractTrigger.RIGHT_CLICK ? ItemMarkers.RIGHT_CLICK_ABILITIES : ItemMarkers.LEFT_CLICK_ABILITIES;
    var markerIds = ItemMarkers.getStringList(item, markerKey);
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

  private void tickItemPassives() {
    long now = engine.tickNow();
    if ((now % ITEM_PASSIVE_PERIOD) != 0L) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      ItemStack[] items = {
          player.getInventory().getItemInMainHand(),
          player.getInventory().getItemInOffHand(),
          player.getInventory().getHelmet(),
          player.getInventory().getChestplate(),
          player.getInventory().getLeggings(),
          player.getInventory().getBoots()
      };
      for (ItemStack item : items) {
        if (item == null || item.getType().isAir()) {
          continue;
        }
        for (String abilityId : ItemMarkers.getStringList(item, ItemMarkers.PASSIVE_ABILITIES)) {
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
        ctx -> applyUpgradeState(ctx, item, effects));
    if (allowSecondary && !secondary.isEmpty()) {
      for (String secondaryId : secondary) {
        if (secondaryId.equals(abilityId)) {
          continue;
        }
        castWithItem(player, secondaryId, item, false);
      }
    }
  }

  private void applyUpgradeState(CastContext ctx, ItemStack item, List<UpgradeStatusEffectSpec> effects) {
    java.util.Map<String, Double> modifiers = ItemMarkers.getUpgradeModifiers(item);
    for (UpgradeModifierType type : UpgradeModifierType.values()) {
      double value = modifiers.getOrDefault(type.key(), type.defaultValue());
      ctx.variables().put("upgrade_" + type.key(), value);
    }
    if (effects != null && !effects.isEmpty()) {
      ctx.variables().put(Vars.UPGRADE_STATUS_EFFECTS, effects);
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
    if (engine.isDebugEnabled()) {
      engine.debug("interact: action=" + event.getAction()
          + " hand=" + (event.getHand() == null ? "null" : event.getHand().name())
          + " player=" + event.getPlayer().getName());
    }

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

    boolean castAny = false;
    boolean shouldCancel = false;
    boolean boundRightClick = false;

    // Item-bound ability list (ability set) - pragmatic ExecutableItems-style binding.
    // This runs before explicit InteractBindings so items can be configured without registering Java bindings.
    if (item != null && !item.getType().isAir()) {
      if (rightClick || leftClick) {
        boolean sneaking = player.isSneaking();
        NamespacedKey key;
        List<String> ids;
        if (rightClick) {
          key = sneaking ? ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES : ItemMarkers.RIGHT_CLICK_ABILITIES;
          ids = ItemMarkers.getStringList(item, key);
          if (ids.isEmpty() && sneaking) {
            ids = ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES);
          }
        } else {
          key = sneaking ? ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES : ItemMarkers.LEFT_CLICK_ABILITIES;
          ids = ItemMarkers.getStringList(item, key);
          if (ids.isEmpty() && sneaking) {
            ids = ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES);
          }
        }
        if (!ids.isEmpty()) {
          if (rightClick) {
            boundRightClick = true;
          }
          event.setCancelled(true);
          for (String abilityId : ids) {
            try {
              if (!engine.hasAbility(abilityId)) {
                if (engine.isDebugEnabled()) {
                  engine.debug("item ability not registered: " + abilityId);
                }
                continue;
              }
              castWithItem(player, abilityId, item, true);
              castAny = true;
            } catch (IllegalArgumentException ex) {
              if (engine.isDebugEnabled()) {
                engine.debug("item ability invalid: " + abilityId + " (" + ex.getMessage() + ")");
              }
            }
          }
        }
      }
    }

    for (InteractBinding binding : interactBindings) {
      if (binding.trigger() == InteractTrigger.RIGHT_CLICK && item != null && binding.itemMatcher().matches(player, item)) {
        boundRightClick = true;
      }
      if (!binding.trigger().matches(event)) {
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
      castWithItem(player, binding.abilityId(), item, true);
      castAny = true;
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
      consumeItem(player, event.getHand(), item);
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
