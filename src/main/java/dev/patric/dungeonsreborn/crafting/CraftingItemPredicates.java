package dev.patric.dungeonsreborn.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class CraftingItemPredicates {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final MiniMessage MINI = MiniMessage.miniMessage();

  private CraftingItemPredicates() {
  }

  public static CraftingItemPredicate any() {
    return stack -> true;
  }

  public static CraftingItemPredicate allOf(List<CraftingItemPredicate> predicates) {
    if (predicates == null || predicates.isEmpty()) {
      return any();
    }
    List<CraftingItemPredicate> copy = List.copyOf(predicates);
    return stack -> {
      for (CraftingItemPredicate predicate : copy) {
        if (predicate != null && !predicate.matches(stack)) {
          return false;
        }
      }
      return true;
    };
  }

  public static CraftingItemPredicate anyOf(List<CraftingItemPredicate> predicates) {
    if (predicates == null || predicates.isEmpty()) {
      return any();
    }
    List<CraftingItemPredicate> copy = List.copyOf(predicates);
    return stack -> {
      for (CraftingItemPredicate predicate : copy) {
        if (predicate != null && predicate.matches(stack)) {
          return true;
        }
      }
      return false;
    };
  }

  public static CraftingItemPredicate not(CraftingItemPredicate predicate) {
    Objects.requireNonNull(predicate, "predicate");
    return stack -> !predicate.matches(stack);
  }

  public static CraftingItemPredicate customModelData(Integer exact, Integer min, Integer max) {
    return stack -> {
      if (stack == null || stack.getType().isAir()) {
        return false;
      }
      ItemMeta meta = stack.getItemMeta();
      if (meta == null || !meta.hasCustomModelDataComponent()) {
        return false;
      }
      CustomModelDataComponent component = meta.getCustomModelDataComponent();
      if (component == null) {
        return false;
      }
      var floats = component.getFloats();
      if (floats == null || floats.size() != 1) {
        return false;
      }
      Float value = floats.getFirst();
      if (value == null) {
        return false;
      }
      int cmd = Math.round(value);
      if (exact != null) {
        return cmd == exact;
      }
      if (min != null && cmd < min) {
        return false;
      }
      if (max != null && cmd > max) {
        return false;
      }
      return true;
    };
  }

  public static CraftingItemPredicate namePlain(String name) {
    if (name == null) {
      return any();
    }
    String expected = name;
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      Component display = meta == null ? null : meta.displayName();
      if (display == null) {
        return false;
      }
      String plain = PLAIN.serialize(display);
      return expected.equals(plain);
    };
  }

  public static CraftingItemPredicate nameMini(String mm) {
    if (mm == null) {
      return any();
    }
    Component expected = MINI.deserialize(mm);
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      Component display = meta == null ? null : meta.displayName();
      return expected.equals(display);
    };
  }

  public static CraftingItemPredicate loreContains(String substring) {
    if (substring == null || substring.isBlank()) {
      return any();
    }
    String needle = substring.toLowerCase();
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
        return false;
      }
      List<Component> lore = meta.lore();
      if (lore == null || lore.isEmpty()) {
        return false;
      }
      for (Component line : lore) {
        if (line == null) {
          continue;
        }
        String plain = PLAIN.serialize(line);
        if (plain != null && plain.toLowerCase().contains(needle)) {
          return true;
        }
      }
      return false;
    };
  }

  public static CraftingItemPredicate loreExact(List<String> lines) {
    if (lines == null) {
      return any();
    }
    List<String> expected = List.copyOf(lines);
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
        return false;
      }
      List<Component> lore = meta.lore();
      if (lore == null) {
        return expected.isEmpty();
      }
      if (lore.size() != expected.size()) {
        return false;
      }
      for (int i = 0; i < lore.size(); i++) {
        Component line = lore.get(i);
        String plain = line == null ? "" : PLAIN.serialize(line);
        if (!expected.get(i).equals(plain)) {
          return false;
        }
      }
      return true;
    };
  }

  public static CraftingItemPredicate enchantAll(Map<Enchantment, Integer> requirements) {
    if (requirements == null || requirements.isEmpty()) {
      return any();
    }
    Map<Enchantment, Integer> req = Map.copyOf(requirements);
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
        return false;
      }
      for (Map.Entry<Enchantment, Integer> entry : req.entrySet()) {
        Enchantment enchant = entry.getKey();
        int min = entry.getValue() == null ? 1 : entry.getValue();
        if (!meta.hasEnchant(enchant)) {
          return false;
        }
        if (meta.getEnchantLevel(enchant) < min) {
          return false;
        }
      }
      return true;
    };
  }

  public static CraftingItemPredicate enchantAny(Map<Enchantment, Integer> requirements) {
    if (requirements == null || requirements.isEmpty()) {
      return any();
    }
    Map<Enchantment, Integer> req = Map.copyOf(requirements);
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
        return false;
      }
      for (Map.Entry<Enchantment, Integer> entry : req.entrySet()) {
        Enchantment enchant = entry.getKey();
        int min = entry.getValue() == null ? 1 : entry.getValue();
        if (meta.hasEnchant(enchant) && meta.getEnchantLevel(enchant) >= min) {
          return true;
        }
      }
      return false;
    };
  }

  public record AttributeRequirement(Attribute attribute, Double min, Double max,
                                     AttributeModifier.Operation operation,
                                     EquipmentSlotGroup slotGroup) {
  }

  public static CraftingItemPredicate attributes(List<AttributeRequirement> requirements) {
    if (requirements == null || requirements.isEmpty()) {
      return any();
    }
    List<AttributeRequirement> req = List.copyOf(requirements);
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
        return false;
      }
      for (AttributeRequirement requirement : req) {
        Attribute attr = requirement.attribute();
        if (attr == null) {
          return false;
        }
        var modifiers = meta.getAttributeModifiers(attr);
        if (modifiers == null || modifiers.isEmpty()) {
          return false;
        }
        boolean matched = false;
        for (AttributeModifier modifier : modifiers) {
          if (requirement.operation() != null && modifier.getOperation() != requirement.operation()) {
            continue;
          }
          if (requirement.slotGroup() != null && modifier.getSlotGroup() != requirement.slotGroup()) {
            continue;
          }
          double amount = modifier.getAmount();
          if (requirement.min() != null && amount < requirement.min()) {
            continue;
          }
          if (requirement.max() != null && amount > requirement.max()) {
            continue;
          }
          matched = true;
          break;
        }
        if (!matched) {
          return false;
        }
      }
      return true;
    };
  }

  public record DurabilityRequirement(Integer minRemaining, Integer maxRemaining,
                                      Double minPercent, Double maxPercent,
                                      Integer minDamage, Integer maxDamage) {
  }

  public static CraftingItemPredicate durability(DurabilityRequirement requirement) {
    if (requirement == null) {
      return any();
    }
    return stack -> {
      if (stack == null || stack.getType().isAir()) {
        return false;
      }
      ItemMeta meta = stack.getItemMeta();
      if (!(meta instanceof Damageable damageable)) {
        return false;
      }
      int maxDurability = stack.getType().getMaxDurability();
      int damage = damageable.getDamage();
      int remaining = maxDurability - damage;
      if (requirement.minRemaining != null && remaining < requirement.minRemaining) {
        return false;
      }
      if (requirement.maxRemaining != null && remaining > requirement.maxRemaining) {
        return false;
      }
      if (requirement.minDamage != null && damage < requirement.minDamage) {
        return false;
      }
      if (requirement.maxDamage != null && damage > requirement.maxDamage) {
        return false;
      }
      if (maxDurability > 0 && (requirement.minPercent != null || requirement.maxPercent != null)) {
        double percent = (double) remaining / (double) maxDurability;
        if (requirement.minPercent != null && percent < requirement.minPercent) {
          return false;
        }
        if (requirement.maxPercent != null && percent > requirement.maxPercent) {
          return false;
        }
      }
      return true;
    };
  }

  public record PotionEffectRequirement(PotionEffectType type, Integer minAmplifier, Integer maxAmplifier,
                                        Integer minDuration, Integer maxDuration) {
  }

  public record PotionRequirement(PotionType baseType, List<PotionEffectRequirement> effects) {
  }

  public static CraftingItemPredicate potion(PotionRequirement requirement) {
    if (requirement == null) {
      return any();
    }
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (!(meta instanceof PotionMeta potionMeta)) {
        return false;
      }
      if (requirement.baseType != null && potionMeta.getBasePotionType() != requirement.baseType) {
        return false;
      }
      List<PotionEffectRequirement> effects = requirement.effects == null ? List.of() : requirement.effects;
      if (effects.isEmpty()) {
        return true;
      }
      for (PotionEffectRequirement effectReq : effects) {
        if (effectReq.type == null) {
          return false;
        }
        PotionEffect found = null;
        for (PotionEffect effect : potionMeta.getCustomEffects()) {
          if (effect.getType().equals(effectReq.type)) {
            found = effect;
            break;
          }
        }
        if (found == null) {
          return false;
        }
        int amplifier = found.getAmplifier();
        int duration = found.getDuration();
        if (effectReq.minAmplifier != null && amplifier < effectReq.minAmplifier) {
          return false;
        }
        if (effectReq.maxAmplifier != null && amplifier > effectReq.maxAmplifier) {
          return false;
        }
        if (effectReq.minDuration != null && duration < effectReq.minDuration) {
          return false;
        }
        if (effectReq.maxDuration != null && duration > effectReq.maxDuration) {
          return false;
        }
      }
      return true;
    };
  }

  public record PdcRequirement(NamespacedKey key, PersistentDataType<?, ?> type, Object value) {
  }

  public static CraftingItemPredicate pdc(List<PdcRequirement> requirements) {
    if (requirements == null || requirements.isEmpty()) {
      return any();
    }
    List<PdcRequirement> req = List.copyOf(requirements);
    return stack -> {
      ItemMeta meta = stack == null ? null : stack.getItemMeta();
      if (meta == null) {
        return false;
      }
      PersistentDataContainer container = meta.getPersistentDataContainer();
      for (PdcRequirement requirement : req) {
        if (requirement.key() == null || requirement.type() == null) {
          return false;
        }
        Object stored = container.get(requirement.key(), requirement.type());
        if (stored == null) {
          return false;
        }
        if (requirement.value() != null && !Objects.equals(stored, requirement.value())) {
          return false;
        }
      }
      return true;
    };
  }

  public static CraftingItemPredicate compound(List<CraftingItemPredicate> and, List<CraftingItemPredicate> any,
                                               CraftingItemPredicate not) {
    List<CraftingItemPredicate> predicates = new ArrayList<>();
    if (and != null && !and.isEmpty()) {
      predicates.add(allOf(and));
    }
    if (any != null && !any.isEmpty()) {
      predicates.add(anyOf(any));
    }
    if (not != null) {
      predicates.add(not(not));
    }
    return allOf(predicates);
  }
}
