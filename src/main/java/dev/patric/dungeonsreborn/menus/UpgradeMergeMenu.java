package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeEnchantSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeModifierType;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeStatusEffectSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeActivator;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradePriceSpec;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.components.storage.StorageSlot;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class UpgradeMergeMenu extends Window {
  private static final int SIZE = 54;

  private static final int SLOT_TITLE = 4;
  private static final int SLOT_DIFF = 13;
  private static final int SLOT_PREVIEW = 22;
  private static final int SLOT_APPLY = 31;
  private static final int SLOT_QUICK = 49;
  private static final int SLOT_CLEAR = 40;

  private final UpgradeService upgrades;
  private final StorageArea targetSlot = new StorageArea(2, 2, 1, 1);
  private final StorageArea upgradeSlot = new StorageArea(2, 6, 1, 1);
  private final Set<UUID> applyLocks = new HashSet<>();

  public UpgradeMergeMenu(UpgradeService upgrades) {
    super(SIZE, GuiI18n.tr("gui.upgrades.merge.title"), true);
    this.upgrades = upgrades;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.ENCHANTED_BOOK, GuiI18n.tr("gui.upgrades.merge.header.title"), List.of(
        GuiI18n.tr("gui.upgrades.merge.header.hint1"),
        GuiI18n.tr("gui.upgrades.merge.header.hint2")))));

    setFixed(SLOT_DIFF, new Label(this::diffItem));
    setFixed(SLOT_PREVIEW, new Label(this::previewItem));
    setFixed(SLOT_APPLY, new Button(this::applyButtonItem, ctx -> applyUpgrade(ctx.player(), false)).autoDescribeInLore(false));
    setFixed(SLOT_QUICK, new Button(this::quickApplyButtonItem, ctx -> applyUpgrade(ctx.player(), true)).autoDescribeInLore(false));
    setFixed(SLOT_CLEAR, new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.upgrades.merge.clear.title"), List.of(
        GuiI18n.tr(p, "gui.upgrades.merge.clear.hint"))), ctx -> {
      returnInputs(ctx.player());
      redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    configureSlots();
    targetSlot.applyFixed(this);
    upgradeSlot.applyFixed(this);
    targetSlot.onChange((player, index, stack) -> {
      redrawSlot(player, SLOT_PREVIEW);
      redrawSlot(player, SLOT_DIFF);
      redrawSlot(player, SLOT_APPLY);
      redrawSlot(player, SLOT_QUICK);
    });
    upgradeSlot.onChange((player, index, stack) -> {
      redrawSlot(player, SLOT_PREVIEW);
      redrawSlot(player, SLOT_DIFF);
      redrawSlot(player, SLOT_APPLY);
      redrawSlot(player, SLOT_QUICK);
    });

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      returnInputs(ctx.player());
      applyLocks.remove(ctx.player().getUniqueId());
      setInputsLocked(false);
      GuiSounds.close(ctx.player());
    });
  }

  private void configureSlots() {
    StorageSlot target = targetSlot.slot(0);
    target.vanilla(true).accepts(stack -> stack != null && !stack.getType().isAir());

    StorageSlot upgrade = upgradeSlot.slot(0);
    upgrade.vanilla(true).accepts(stack -> upgrades != null && upgrades.isUpgradeItem(stack));
  }

  private ItemStack previewItem(Player player) {
    ItemStack target = targetSlot.slot(0).stored(player);
    ItemStack upgrade = upgradeSlot.slot(0).stored(player);
    if (target == null || target.getType().isAir() || upgrade == null || upgrade.getType().isAir()) {
      return GuiItems.named(Material.GRAY_DYE, tr(player, "gui.upgrades.merge.preview.waiting.title"), List.of(
          tr(player, "gui.upgrades.merge.preview.waiting.hint")));
    }
    if (upgrades == null) {
      return GuiItems.named(Material.BARRIER, tr(player, "gui.upgrades.merge.preview.missing.title"), List.of(
          tr(player, "gui.upgrades.merge.preview.missing.hint")));
    }
    UpgradeService.ApplyResult result = upgrades.preview(player, target, upgrade);
    if (!result.success()) {
      List<Component> lore = new ArrayList<>();
      lore.add(tr(player, "gui.upgrades.merge.error.line", Placeholder.unparsed("error", result.error())));
      lore.addAll(buildErrorLore(player, result.error(), target, upgrade));
      return GuiItems.named(Material.RED_DYE, tr(player, "gui.upgrades.merge.preview.error.title"), lore);
    }
    return result.updated();
  }

  private ItemStack diffItem(Player player) {
    ItemStack target = targetSlot.slot(0).stored(player);
    ItemStack upgrade = upgradeSlot.slot(0).stored(player);
    if (target == null || target.getType().isAir() || upgrade == null || upgrade.getType().isAir()) {
      return GuiItems.named(Material.GRAY_DYE, tr(player, "gui.upgrades.merge.diff.waiting.title"), List.of(
          tr(player, "gui.upgrades.merge.diff.waiting.hint")));
    }
    if (upgrades == null) {
      return GuiItems.named(Material.BARRIER, tr(player, "gui.upgrades.merge.diff.missing.title"), List.of(
          tr(player, "gui.upgrades.merge.diff.missing.hint")));
    }
    UpgradeService.ApplyResult result = upgrades.preview(player, target, upgrade);
    if (!result.success()) {
      List<Component> lore = new ArrayList<>();
      lore.add(tr(player, "gui.upgrades.merge.error.line", Placeholder.unparsed("error", result.error())));
      lore.addAll(buildErrorLore(player, result.error(), target, upgrade));
      return GuiItems.named(Material.RED_DYE, tr(player, "gui.upgrades.merge.diff.error.title"), lore);
    }
    List<Component> lore = new ArrayList<>();
    List<Component> modifiers = new ArrayList<>();
    List<Component> enchants = new ArrayList<>();
    List<Component> attributes = new ArrayList<>();
    List<Component> effects = new ArrayList<>();
    List<Component> bindings = new ArrayList<>();
    List<Component> secondary = new ArrayList<>();
    appendModifierDiff(modifiers, target, result.updated());
    appendEnchantDiff(enchants, target, result.updated());
    appendAttributeDiff(attributes, target, result.updated());
    appendStatusEffectDiff(effects, target, result.updated());
    appendBindingDiff(bindings, target, result.updated());
    appendSecondaryDiff(secondary, target, result.updated());
    appendSection(lore, player, "gui.upgrades.merge.section.modifiers", modifiers);
    appendSection(lore, player, "gui.upgrades.merge.section.enchants", enchants);
    appendSection(lore, player, "gui.upgrades.merge.section.attributes", attributes);
    appendSection(lore, player, "gui.upgrades.merge.section.status", effects);
    appendSection(lore, player, "gui.upgrades.merge.section.bindings", bindings);
    appendSection(lore, player, "gui.upgrades.merge.section.secondary", secondary);
    if (lore.isEmpty()) {
      lore = List.of(tr(player, "gui.upgrades.merge.diff.none.hint"));
    }
    return GuiItems.named(Material.BOOK, tr(player, "gui.upgrades.merge.diff.title"), lore);
  }

  private static void appendSection(List<Component> out, Player player, String key, List<Component> lines) {
    if (lines.isEmpty()) {
      return;
    }
    out.add(tr(player, key));
    out.addAll(lines);
  }

  private static void appendModifierDiff(List<Component> lore, ItemStack before, ItemStack after) {
    Map<String, Double> beforeMods = ItemMarkers.getUpgradeModifiers(before);
    Map<String, Double> afterMods = ItemMarkers.getUpgradeModifiers(after);
    for (UpgradeModifierType type : UpgradeModifierType.values()) {
      double beforeValue = beforeMods.getOrDefault(type.key(), type.defaultValue());
      double afterValue = afterMods.getOrDefault(type.key(), type.defaultValue());
      if (Math.abs(afterValue - beforeValue) < 1e-9) {
        continue;
      }
      String line = formatModifier(type, beforeValue, afterValue);
      lore.add(GuiMini.mm(line));
    }
  }

  private static void appendEnchantDiff(List<Component> lore, ItemStack before, ItemStack after) {
    ItemMeta beforeMeta = before.getItemMeta();
    ItemMeta afterMeta = after.getItemMeta();
    if (beforeMeta == null || afterMeta == null) {
      return;
    }
    Map<Enchantment, Integer> beforeEnchants = new LinkedHashMap<>(beforeMeta.getEnchants());
    Map<Enchantment, Integer> afterEnchants = new LinkedHashMap<>(afterMeta.getEnchants());
    Set<Enchantment> keys = new HashSet<>(beforeEnchants.keySet());
    keys.addAll(afterEnchants.keySet());
    for (Enchantment enchant : keys) {
      int beforeLevel = beforeEnchants.getOrDefault(enchant, 0);
      int afterLevel = afterEnchants.getOrDefault(enchant, 0);
      if (beforeLevel == afterLevel) {
        continue;
      }
      lore.add(GuiMini.mm(formatEnchant(enchant, beforeLevel, afterLevel)));
    }
  }

  private static void appendAttributeDiff(List<Component> lore, ItemStack before, ItemStack after) {
    Map<String, AttributeEntry> beforeMods = upgradeAttributeModifiers(before);
    Map<String, AttributeEntry> afterMods = upgradeAttributeModifiers(after);
    Set<String> keys = new HashSet<>(beforeMods.keySet());
    keys.addAll(afterMods.keySet());
    for (String key : keys) {
      AttributeEntry beforeEntry = beforeMods.get(key);
      AttributeEntry afterEntry = afterMods.get(key);
      if (beforeEntry == null && afterEntry == null) {
        continue;
      }
      lore.add(GuiMini.mm(formatAttribute(beforeEntry, afterEntry)));
    }
  }

  private static void appendStatusEffectDiff(List<Component> lore, ItemStack before, ItemStack after) {
    List<String> beforeEffects = ItemMarkers.getUpgradeStatusEffects(before);
    List<String> afterEffects = ItemMarkers.getUpgradeStatusEffects(after);
    if (beforeEffects.equals(afterEffects)) {
      return;
    }
    Set<String> added = new HashSet<>(afterEffects);
    added.removeAll(beforeEffects);
    Set<String> removed = new HashSet<>(beforeEffects);
    removed.removeAll(afterEffects);
    for (String record : added) {
      UpgradeStatusEffectSpec spec = UpgradeStatusEffectSpec.fromRecord(record);
      String label = spec == null ? record : formatStatusEffect(spec);
      lore.add(GuiMini.mm("<green>+ " + label + "</green>"));
    }
    for (String record : removed) {
      UpgradeStatusEffectSpec spec = UpgradeStatusEffectSpec.fromRecord(record);
      String label = spec == null ? record : formatStatusEffect(spec);
      lore.add(GuiMini.mm("<red>- " + label + "</red>"));
    }
  }

  private static void appendBindingDiff(List<Component> lore, ItemStack before, ItemStack after) {
    appendBindingDiff(lore, "RIGHT", ItemMarkers.RIGHT_CLICK_ABILITIES, before, after);
    appendBindingDiff(lore, "LEFT", ItemMarkers.LEFT_CLICK_ABILITIES, before, after);
    appendBindingDiff(lore, "SHIFT+RIGHT", ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES, before, after);
    appendBindingDiff(lore, "SHIFT+LEFT", ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES, before, after);
    appendBindingDiff(lore, "PASSIVE", ItemMarkers.PASSIVE_ABILITIES, before, after);
  }

  private static void appendBindingDiff(List<Component> lore, String label, NamespacedKey key, ItemStack before, ItemStack after) {
    List<String> beforeList = ItemMarkers.getStringList(before, key);
    List<String> afterList = ItemMarkers.getStringList(after, key);
    if (beforeList.equals(afterList)) {
      return;
    }
    Set<String> added = new HashSet<>(afterList);
    added.removeAll(beforeList);
    Set<String> removed = new HashSet<>(beforeList);
    removed.removeAll(afterList);
    for (String ability : added) {
      lore.add(GuiMini.mm("<green>+ " + label + " -> " + ability + "</green>"));
    }
    for (String ability : removed) {
      lore.add(GuiMini.mm("<red>- " + label + " -> " + ability + "</red>"));
    }
  }

  private static void appendSecondaryDiff(List<Component> lore, ItemStack before, ItemStack after) {
    List<String> beforeList = ItemMarkers.getStringList(before, ItemMarkers.UPGRADE_SECONDARY_ABILITIES);
    List<String> afterList = ItemMarkers.getStringList(after, ItemMarkers.UPGRADE_SECONDARY_ABILITIES);
    if (beforeList.equals(afterList)) {
      return;
    }
    Set<String> added = new HashSet<>(afterList);
    added.removeAll(beforeList);
    Set<String> removed = new HashSet<>(beforeList);
    removed.removeAll(afterList);
    for (String ability : added) {
      lore.add(GuiMini.mm("<green>+ " + ability + "</green>"));
    }
    for (String ability : removed) {
      lore.add(GuiMini.mm("<red>- " + ability + "</red>"));
    }
  }

  private static String formatModifier(UpgradeModifierType type, double beforeValue, double afterValue) {
    if (type.isMultiplier()) {
      return formatDelta(type.label(), "x" + formatNumber(beforeValue), "x" + formatNumber(afterValue),
          Math.abs(afterValue - beforeValue) < 1e-9);
    }
    return formatDelta(type.label(), formatSigned(beforeValue), formatSigned(afterValue),
        Math.abs(afterValue - beforeValue) < 1e-9);
  }

  private static String formatEnchant(Enchantment enchant, int before, int after) {
    String name = enchant.getKey().getKey().replace('_', ' ');
    if (before == 0) {
      return "<green>+ " + name + " " + after + "</green>";
    }
    if (after == 0) {
      return "<red>- " + name + " " + before + "</red>";
    }
    return "<yellow>~ " + name + " " + before + " -> " + after + "</yellow>";
  }

  private static String formatAttribute(AttributeEntry before, AttributeEntry after) {
    AttributeEntry entry = after != null ? after : before;
    if (entry == null) {
      return "";
    }
    String name = entry.attribute().getKey().getKey().replace('_', ' ');
    String operation = entry.modifier().getOperation().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    String slot = entry.modifier().getSlotGroup().toString().toLowerCase(Locale.ROOT);
    double beforeAmount = before == null ? 0.0 : before.modifier().getAmount();
    double afterAmount = after == null ? 0.0 : after.modifier().getAmount();
    if (before == null) {
      return "<green>+ " + name + " " + formatSigned(afterAmount) + " (" + operation + ", " + slot + ")</green>";
    }
    if (after == null) {
      return "<red>- " + name + " " + formatSigned(beforeAmount) + " (" + operation + ", " + slot + ")</red>";
    }
    return "<yellow>~ " + name + " " + formatSigned(beforeAmount) + " -> " + formatSigned(afterAmount)
        + " (" + operation + ", " + slot + ")</yellow>";
  }

  private static String formatStatusEffect(UpgradeStatusEffectSpec spec) {
    String name = spec.type().getKey().getKey().replace('_', ' ');
    int seconds = Math.max(1, Math.round(spec.durationTicks() / 20.0f));
    int level = spec.amplifier() + 1;
    return name + " " + level + " (" + seconds + "s)";
  }

  private static String formatDelta(String label, String before, String after, boolean same) {
    if (same) {
      return "<gray>" + label + ": " + after + "</gray>";
    }
    if (before.startsWith("+0") || before.equals("0") || before.equals("x1")) {
      return "<green>+ " + label + ": " + after + "</green>";
    }
    if (after.startsWith("+0") || after.equals("0") || after.equals("x1")) {
      return "<red>- " + label + ": " + before + "</red>";
    }
    return "<yellow>~ " + label + ": " + before + " -> " + after + "</yellow>";
  }

  private static String formatNumber(double value) {
    if (Math.abs(value - Math.round(value)) < 1e-9) {
      return String.valueOf((long) Math.round(value));
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static String formatSigned(double value) {
    String prefix = value >= 0 ? "+" : "";
    return prefix + formatNumber(value);
  }

  private static String formatPercent(double value) {
    double clamped = Math.max(0.0, Math.min(1.0, value));
    long percent = Math.round(clamped * 100.0);
    return percent + "%";
  }

  private static Component tr(Player player, String key, TagResolver... resolvers) {
    if (player == null) {
      return GuiI18n.tr(key, resolvers);
    }
    return GuiI18n.tr(player, key, resolvers);
  }

  private void appendPriceLore(Player player, List<Component> lore, ItemStack upgrade) {
    if (lore == null || upgrades == null || upgrade == null) {
      return;
    }
    UpgradePriceSpec price = null;
    var spec = upgrades.resolveSpec(upgrade);
    if (spec != null) {
      price = spec.price();
    }
    if (price == null || price.isEmpty()) {
      return;
    }
    lore.add(tr(player, "gui.upgrades.merge.cost.title"));
    if (price.pallet() > 0) {
      lore.add(tr(player, "gui.upgrades.merge.cost.pallet", Placeholder.unparsed("amount", String.valueOf(price.pallet()))));
    }
    if (price.compressed() > 0) {
      lore.add(tr(player, "gui.upgrades.merge.cost.compressed", Placeholder.unparsed("amount", String.valueOf(price.compressed()))));
    }
    if (price.normal() > 0) {
      lore.add(tr(player, "gui.upgrades.merge.cost.tokens", Placeholder.unparsed("amount", String.valueOf(price.normal()))));
    }
  }

  private void appendRequirementLore(Player player, List<Component> lore, ItemStack upgrade) {
    if (lore == null || upgrades == null || upgrade == null) {
      return;
    }
    var spec = upgrades.resolveSpec(upgrade);
    if (spec == null) {
      return;
    }
    var requirements = spec.requirements();
    if (requirements == null) {
      return;
    }
    List<Component> lines = new ArrayList<>();
    if (requirements.minXp() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.minXp",
          Placeholder.unparsed("value", String.valueOf(requirements.minXp()))));
    }
    if (requirements.consumeXp() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.consumeXp",
          Placeholder.unparsed("value", String.valueOf(requirements.consumeXp()))));
    }
    if (requirements.minTotalXp() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.minTotalXp",
          Placeholder.unparsed("value", String.valueOf(requirements.minTotalXp()))));
    }
    if (requirements.consumeTotalXp() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.consumeTotalXp",
          Placeholder.unparsed("value", String.valueOf(requirements.consumeTotalXp()))));
    }
    if (requirements.minProgress() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.minProgress",
          Placeholder.unparsed("value", formatPercent(requirements.minProgress()))));
    }
    if (requirements.consumeProgress() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.consumeProgress",
          Placeholder.unparsed("value", formatPercent(requirements.consumeProgress()))));
    }
    if (requirements.minMaxMana() > 0) {
      lines.add(tr(player, "gui.upgrades.merge.requirements.minMaxMana",
          Placeholder.unparsed("value", formatNumber(requirements.minMaxMana()))));
    }
    if (lines.isEmpty()) {
      return;
    }
    lore.add(tr(player, "gui.upgrades.merge.requirements.title"));
    lore.addAll(lines);
  }

  private List<Component> buildErrorLore(Player player, String error, ItemStack target, ItemStack upgrade) {
    if (error == null || error.isBlank()) {
      return List.of();
    }
    List<Component> lore = new ArrayList<>();
    String lower = error.toLowerCase(Locale.ROOT);
    if (lower.contains("activation slot") || lower.contains("occupied")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.bindingSlot"));
    }
    if (lower.contains("conflict") && lower.contains("enchant")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.enchantConflict"));
    }
    if (lower.contains("not compatible") || lower.contains("specific items")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.compatibility"));
    }
    if (lower.contains("mana system")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.manaProvider"));
    }
    if (lower.contains("max mana")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.maxMana"));
    }
    if (lower.contains("xp")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.xp"));
      appendRequirementLore(player, lore, upgrade);
    }
    if (lower.contains("token")) {
      lore.add(tr(player, "gui.upgrades.merge.tip.tokens"));
      appendPriceLore(player, lore, upgrade);
    }
    if (target == null || upgrade == null || target.getType().isAir() || upgrade.getType().isAir()) {
      return lore;
    }
    if (lower.contains("activation slot") || lower.contains("occupied")) {
      appendSlotDetails(player, lore, target, upgrade);
    }
    if (lower.contains("enchant")) {
      appendEnchantConflictDetails(player, lore, target, upgrade);
    }
    return lore;
  }

  private void appendSlotDetails(Player player, List<Component> lore, ItemStack target, ItemStack upgrade) {
    if (upgrades == null) {
      return;
    }
    String upgradeId = ItemMarkers.getUpgradeId(upgrade);
    if (upgradeId == null) {
      return;
    }
    var spec = upgrades.registry().upgradeSpec(upgradeId);
    if (spec == null || spec.spell() == null) {
      return;
    }
    List<String> abilities = switch (spec.spell().activator()) {
      case RIGHT_CLICK -> ItemMarkers.getStringList(target, ItemMarkers.RIGHT_CLICK_ABILITIES);
      case LEFT_CLICK -> ItemMarkers.getStringList(target, ItemMarkers.LEFT_CLICK_ABILITIES);
      case SHIFT_RIGHT_CLICK -> ItemMarkers.getStringList(target, ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES);
      case SHIFT_LEFT_CLICK -> ItemMarkers.getStringList(target, ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES);
      case PASSIVE -> ItemMarkers.getStringList(target, ItemMarkers.PASSIVE_ABILITIES);
    };
    if (!abilities.isEmpty()) {
      lore.add(tr(player, "gui.upgrades.merge.slot.contains"));
      for (String ability : limitList(abilities, 3)) {
        lore.add(tr(player, "gui.upgrades.merge.slot.line", Placeholder.unparsed("value", ability)));
      }
      if (abilities.size() > 3) {
        lore.add(tr(player, "gui.upgrades.merge.slot.more"));
      }
    } else {
      lore.add(tr(player, "gui.upgrades.merge.slot.occupied"));
    }
  }

  private List<Component> buildActivationWarning(Player player, ItemStack target, ItemStack upgrade) {
    if (upgrades == null || player == null || target == null || upgrade == null) {
      return List.of();
    }
    String upgradeId = ItemMarkers.getUpgradeId(upgrade);
    if (upgradeId == null) {
      return List.of();
    }
    var spec = upgrades.registry().upgradeSpec(upgradeId);
    if (spec == null || spec.spell() == null) {
      return List.of();
    }
    if (!upgrades.hasActivationConflict(player, target, spec.spell().activator())) {
      return List.of();
    }
    List<Component> lore = new ArrayList<>();
    Component binding = activatorLabel(player, spec.spell().activator());
    lore.add(tr(player, "gui.upgrades.merge.warning.binding", Placeholder.component("binding", binding)));
    lore.add(tr(player, "gui.upgrades.merge.warning.overwrite"));
    appendSlotDetails(player, lore, target, upgrade);
    return lore;
  }

  private static Component activatorLabel(Player player, UpgradeActivator activator) {
    return switch (activator) {
      case LEFT_CLICK -> tr(player, "gui.upgrades.merge.activator.left");
      case RIGHT_CLICK -> tr(player, "gui.upgrades.merge.activator.right");
      case SHIFT_LEFT_CLICK -> tr(player, "gui.upgrades.merge.activator.shiftLeft");
      case SHIFT_RIGHT_CLICK -> tr(player, "gui.upgrades.merge.activator.shiftRight");
      case PASSIVE -> tr(player, "gui.upgrades.merge.activator.passive");
    };
  }

  private void appendEnchantConflictDetails(Player player, List<Component> lore, ItemStack target, ItemStack upgrade) {
    List<UpgradeEnchantSpec> enchants = resolveUpgradeEnchants(upgrade);
    if (enchants.isEmpty()) {
      return;
    }
    ItemMeta meta = target.getItemMeta();
    if (meta == null) {
      return;
    }
    Map<Enchantment, Integer> existing = meta.getEnchants();
    List<String> conflicts = new ArrayList<>();
    for (UpgradeEnchantSpec spec : enchants) {
      Enchantment enchant = spec.enchantment();
      if (!enchant.canEnchantItem(target)) {
        conflicts.add(enchant.getKey().getKey() + " cannot be applied");
        continue;
      }
      for (Enchantment other : existing.keySet()) {
        if (other.equals(enchant)) {
          continue;
        }
        if (enchant.conflictsWith(other)) {
          conflicts.add(enchant.getKey().getKey() + " conflicts with " + other.getKey().getKey());
        }
      }
    }
    if (conflicts.isEmpty()) {
      return;
    }
    lore.add(tr(player, "gui.upgrades.merge.conflicts.title"));
    for (String line : limitList(conflicts, 3)) {
      lore.add(tr(player, "gui.upgrades.merge.conflicts.line", Placeholder.unparsed("value", line.replace('_', ' '))));
    }
    if (conflicts.size() > 3) {
      lore.add(tr(player, "gui.upgrades.merge.conflicts.more"));
    }
  }

  private List<UpgradeEnchantSpec> resolveUpgradeEnchants(ItemStack upgrade) {
    if (upgrade == null || upgrade.getType().isAir()) {
      return List.of();
    }
    String upgradeId = ItemMarkers.getUpgradeId(upgrade);
    if (upgradeId != null && upgrades != null) {
      var spec = upgrades.registry().upgradeSpec(upgradeId);
      if (spec != null && !spec.enchants().isEmpty()) {
        return spec.enchants();
      }
    }
    ItemMeta meta = upgrade.getItemMeta();
    if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta stored && stored.hasStoredEnchants()) {
      List<UpgradeEnchantSpec> out = new ArrayList<>();
      for (var entry : stored.getStoredEnchants().entrySet()) {
        out.add(new UpgradeEnchantSpec(entry.getKey(), entry.getValue()));
      }
      return out;
    }
    return List.of();
  }

  private static <T> List<T> limitList(List<T> values, int limit) {
    if (values.size() <= limit) {
      return values;
    }
    return values.subList(0, limit);
  }

  private static Map<String, AttributeEntry> upgradeAttributeModifiers(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasAttributeModifiers()) {
      return Map.of();
    }
    var modifiers = meta.getAttributeModifiers();
    if (modifiers == null || modifiers.isEmpty()) {
      return Map.of();
    }
    Map<String, AttributeEntry> out = new HashMap<>();
    for (var entry : modifiers.entries()) {
      Attribute attribute = entry.getKey();
      AttributeModifier modifier = entry.getValue();
      NamespacedKey key = modifier.getKey();
      if (key == null || !"dungeonsreborn".equalsIgnoreCase(key.getNamespace())) {
        continue;
      }
      if (!key.getKey().startsWith("upgrade_")) {
        continue;
      }
      out.put(key.toString(), new AttributeEntry(attribute, modifier));
    }
    return out;
  }

  private record AttributeEntry(Attribute attribute, AttributeModifier modifier) {
  }

  private ItemStack applyButtonItem(Player player) {
    ItemStack target = targetSlot.slot(0).stored(player);
    ItemStack upgrade = upgradeSlot.slot(0).stored(player);
    if (target == null || target.getType().isAir() || upgrade == null || upgrade.getType().isAir()) {
      return GuiItems.named(Material.GRAY_DYE, tr(player, "gui.upgrades.merge.apply.waiting.title"), List.of(
          tr(player, "gui.upgrades.merge.apply.waiting.hint")));
    }
    if (upgrades == null) {
      return GuiItems.named(Material.RED_DYE, tr(player, "gui.upgrades.merge.apply.missing.title"), List.of(
          tr(player, "gui.upgrades.merge.apply.missing.hint")));
    }
    UpgradeService.ApplyResult result = upgrades.preview(player, target, upgrade);
    if (!result.success()) {
      List<Component> lore = new ArrayList<>();
      lore.add(tr(player, "gui.upgrades.merge.error.line", Placeholder.unparsed("error", result.error())));
      lore.addAll(buildErrorLore(player, result.error(), target, upgrade));
      return GuiItems.named(Material.RED_DYE, tr(player, "gui.upgrades.merge.apply.error.title"), lore);
    }
    List<Component> lore = new ArrayList<>();
    lore.add(tr(player, "gui.upgrades.merge.apply.consume"));
    appendPriceLore(player, lore, upgrade);
    appendRequirementLore(player, lore, upgrade);
    lore.addAll(buildActivationWarning(player, target, upgrade));
    return GuiItems.named(Material.LIME_DYE, tr(player, "gui.upgrades.merge.apply.title"), lore);
  }

  private ItemStack quickApplyButtonItem(Player player) {
    ItemStack target = targetSlot.slot(0).stored(player);
    ItemStack upgrade = upgradeSlot.slot(0).stored(player);
    if (target == null || target.getType().isAir() || upgrade == null || upgrade.getType().isAir()) {
      return GuiItems.named(Material.GRAY_DYE, tr(player, "gui.upgrades.merge.quick.waiting.title"), List.of(
          tr(player, "gui.upgrades.merge.quick.waiting.hint")));
    }
    if (upgrades == null) {
      return GuiItems.named(Material.RED_DYE, tr(player, "gui.upgrades.merge.quick.missing.title"), List.of(
          tr(player, "gui.upgrades.merge.quick.missing.hint")));
    }
    UpgradeService.ApplyResult result = upgrades.preview(player, target, upgrade);
    if (!result.success()) {
      List<Component> lore = new ArrayList<>();
      lore.add(tr(player, "gui.upgrades.merge.error.line", Placeholder.unparsed("error", result.error())));
      lore.addAll(buildErrorLore(player, result.error(), target, upgrade));
      return GuiItems.named(Material.RED_DYE, tr(player, "gui.upgrades.merge.quick.error.title"), lore);
    }
    List<Component> lore = new ArrayList<>();
    lore.add(tr(player, "gui.upgrades.merge.quick.hint"));
    appendPriceLore(player, lore, upgrade);
    appendRequirementLore(player, lore, upgrade);
    lore.addAll(buildActivationWarning(player, target, upgrade));
    return GuiItems.named(Material.EMERALD, tr(player, "gui.upgrades.merge.quick.title"), lore);
  }

  private void applyUpgrade(Player player, boolean closeAfter) {
    if (!lockApply(player)) {
      return;
    }
    ItemStack target = targetSlot.slot(0).stored(player);
    ItemStack upgrade = upgradeSlot.slot(0).stored(player);
    if (target == null || target.getType().isAir() || upgrade == null || upgrade.getType().isAir()) {
      GuiSounds.error(player);
      player.sendMessage(tr(player, "messages.upgrades.merge.missingInputs"));
      unlockApply(player);
      return;
    }
    UpgradeService.ApplyResult result = upgrades.apply(player, target, upgrade);
    if (!result.success()) {
      GuiSounds.error(player);
      player.sendMessage(tr(player, "messages.upgrades.merge.error", Placeholder.unparsed("error", result.error())));
      unlockApply(player);
      return;
    }
    targetSlot.slot(0).stored(player, result.updated());
    consumeUpgrade(player);
    redraw(player);
    GuiSounds.success(player);
    player.sendMessage(tr(player, "messages.upgrades.merge.applied"));
    unlockApply(player);
    if (closeAfter) {
      player.closeInventory();
    }
  }

  private boolean lockApply(Player player) {
    UUID id = player.getUniqueId();
    if (applyLocks.contains(id)) {
      return false;
    }
    applyLocks.add(id);
    setInputsLocked(true);
    JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> unlockApply(player), 2L);
    return true;
  }

  private void unlockApply(Player player) {
    applyLocks.remove(player.getUniqueId());
    setInputsLocked(false);
  }

  private void setInputsLocked(boolean locked) {
    StorageSlot target = targetSlot.slot(0);
    target.allowPut(!locked);
    target.allowTake(!locked);
    StorageSlot upgrade = upgradeSlot.slot(0);
    upgrade.allowPut(!locked);
    upgrade.allowTake(!locked);
  }

  private void consumeUpgrade(Player player) {
    ItemStack upgrade = upgradeSlot.slot(0).stored(player);
    if (upgrade == null || upgrade.getType().isAir()) {
      return;
    }
    int remaining = upgrade.getAmount() - 1;
    if (remaining <= 0) {
      upgradeSlot.slot(0).stored(player, null);
      return;
    }
    ItemStack updated = upgrade.clone();
    updated.setAmount(remaining);
    upgradeSlot.slot(0).stored(player, updated);
  }

  private void returnInputs(Player player) {
    for (ItemStack stack : targetSlot.contents(player)) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
    }
    for (ItemStack stack : upgradeSlot.contents(player)) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
    }
    targetSlot.clear(player);
    upgradeSlot.clear(player);
  }
}
