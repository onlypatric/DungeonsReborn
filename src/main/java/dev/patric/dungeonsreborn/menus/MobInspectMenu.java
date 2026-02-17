package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.item.PreviewCard;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.MobLootSpec;
import dev.patric.dungeonsreborn.mobs.MobLootPoolRef;
import dev.patric.dungeonsreborn.mobs.MobDropSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;

public final class MobInspectMenu extends Window {
  private final MobSpec spec;
  private final MobYamlRegistry yaml;

  public MobInspectMenu(MobSpec spec, MobYamlRegistry yaml) {
    super(54, GuiI18n.tr("gui.mobs.inspect.title",
        Placeholder.component("mob", spec == null ? Component.empty() : titleComponent(spec))));
    this.spec = Objects.requireNonNull(spec, "spec");
    this.yaml = yaml;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, PreviewCard.head("ICON_MOBS",
        player -> GuiI18n.tr(player, "gui.mobs.inspect.header",
            Placeholder.component("mob", titleComponent(spec))),
        player -> List.of()));
    setFixedAt(2, 4, new Label(this::previewItem));
    setFixedAt(3, 4, PreviewCard.head("ICON_MOBS",
        player -> GuiI18n.tr(player, "gui.mobs.inspect.info"),
        this::infoLore));
  }

  private ItemStack previewItem(Player player) {
    Component title = titleComponent(spec);
    List<Component> lore = new ArrayList<>();
    if (spec.entityType() != null) {
      lore.add(GuiI18n.tr(player, "gui.mobs.inspect.type",
          Placeholder.unparsed("type", spec.entityType().name())));
    }
    if (spec.tier() != null && !spec.tier().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.mobs.inspect.tier",
          Placeholder.unparsed("tier", spec.tier())));
    }
    if (spec.minXpLevel() > 0) {
      lore.add(GuiMini.mm("<gray>Min Level:</gray> <white>" + spec.minXpLevel() + "</white>"));
    }
    double hp = spec.attributes().getOrDefault(Attribute.MAX_HEALTH, 0.0);
    double dps = spec.attributes().getOrDefault(Attribute.ATTACK_DAMAGE, 0.0);
    if (hp > 0.0) {
      lore.add(GuiMini.mm("<gray>HP:</gray> <white>" + formatStat(hp) + "</white>"));
    }
    if (dps > 0.0) {
      lore.add(GuiMini.mm("<gray>DPS:</gray> <white>" + formatStat(dps) + "</white>"));
    }
    return GuiItems.head("ICON_MOBS", title, lore);
  }

  private List<Component> infoLore(Player player) {
    List<Component> lore = new ArrayList<>();
    int traitCount = spec.traits() == null ? 0 : spec.traits().size();
    int phaseCount = spec.phases() == null ? 0 : spec.phases().size();
    int attackCount = 0;
    if (spec.mainAttack() != null) {
      attackCount++;
    }
    if (spec.secondaryAttack() != null) {
      attackCount++;
    }
    if (traitCount > 0) {
      lore.add(GuiI18n.tr(player, "gui.mobs.inspect.traits",
          Placeholder.unparsed("count", String.valueOf(traitCount))));
    }
    if (phaseCount > 0) {
      lore.add(GuiI18n.tr(player, "gui.mobs.inspect.phases",
          Placeholder.unparsed("count", String.valueOf(phaseCount))));
    }
    lore.add(GuiI18n.tr(player, "gui.mobs.inspect.attacks",
        Placeholder.unparsed("count", String.valueOf(attackCount))));
    List<Component> drops = dropLore();
    if (!drops.isEmpty()) {
      lore.add(GuiMini.mm("<gray>Drops:</gray>"));
      lore.addAll(drops);
    }
    return lore;
  }

  private static Component titleComponent(MobSpec spec) {
    if (spec.displayName() != null) {
      return spec.displayName();
    }
    return Component.text(spec.id());
  }

  private List<Component> dropLore() {
    if (spec.loot() == null) {
      return List.of();
    }
    List<MobDropSpec> drops = new ArrayList<>(spec.loot().guaranteed());
    drops.addAll(spec.loot().drops());
    if (yaml != null) {
      for (MobLootPoolRef ref : spec.loot().pools()) {
        MobLootSpec pool = yaml.lootPool(ref.poolId());
        if (pool != null) {
          drops.addAll(pool.guaranteed());
          drops.addAll(pool.drops());
        }
      }
    }
    if (drops.isEmpty()) {
      return List.of();
    }
    List<Component> lore = new ArrayList<>();
    int limit = Math.min(8, drops.size());
    for (int i = 0; i < limit; i++) {
      MobDropSpec drop = drops.get(i);
      ItemStack stack = drop.item();
      Component name = stack != null && stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
          ? stack.getItemMeta().displayName()
          : Component.text(stack == null ? "Unknown" : humanizeMaterial(stack.getType().name()));
      String chance = formatChance(drop.chance());
      lore.add(GuiMini.mm("<dark_gray>-</dark_gray> ").append(name).append(GuiMini.mm(" <gray>(" + chance + ")</gray>")));
    }
    if (drops.size() > limit) {
      lore.add(GuiMini.mm("<gray>... +" + (drops.size() - limit) + " more</gray>"));
    }
    return lore;
  }

  private static String formatChance(double chance) {
    if (chance >= 0.999) {
      return "100%";
    }
    return String.format(java.util.Locale.ROOT, "%.0f%%", chance * 100.0);
  }

  private static String formatStat(double value) {
    if (Math.abs(value - Math.round(value)) < 0.01) {
      return String.valueOf(Math.round(value));
    }
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }

  private static String humanizeMaterial(String material) {
    String[] parts = material.toLowerCase(java.util.Locale.ROOT).split("_");
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      out.append(Character.toUpperCase(part.charAt(0)))
          .append(part.substring(1))
          .append(' ');
    }
    return out.toString().trim();
  }
}
