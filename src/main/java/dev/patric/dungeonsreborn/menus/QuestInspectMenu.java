package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.item.PreviewCard;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.quests.QuestSpec;
import dev.patric.dungeonsreborn.quests.QuestRewards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestInspectMenu extends Window {
  private final QuestSpec spec;

  public QuestInspectMenu(QuestSpec spec) {
    super(54, GuiI18n.tr("gui.quests.inspect.title",
        Placeholder.unparsed("quest", spec == null ? "" : spec.id())));
    this.spec = Objects.requireNonNull(spec, "spec");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, PreviewCard.head("ICON_QUESTS",
        player -> GuiI18n.tr(player, "gui.quests.inspect.header",
            Placeholder.unparsed("quest", spec.id())),
        player -> List.of()));
    setFixedAt(2, 4, PreviewCard.head("ICON_QUESTS",
        player -> displayName(player),
        player -> descriptionLore(player)));
    setFixedAt(3, 4, PreviewCard.head("ICON_QUESTS",
        player -> GuiI18n.tr(player, "gui.quests.inspect.info"),
        this::infoLore));
  }

  private Component displayName(Player player) {
    String name = spec.name().isBlank() ? spec.id() : spec.name();
    return Component.text(name);
  }

  private List<Component> descriptionLore(Player player) {
    List<Component> lore = new ArrayList<>();
    if (spec.description() != null && !spec.description().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.quests.inspect.description"));
      for (int i = 0; i < Math.min(2, spec.description().size()); i++) {
        lore.add(dev.patric.dungeonsreborn.gui.GuiMini.mm(spec.description().get(i)));
      }
    }
    return lore;
  }

  private List<Component> infoLore(Player player) {
    List<Component> lore = new ArrayList<>();
    int objectiveCount = spec.objectives() == null ? 0 : spec.objectives().size();
    lore.add(GuiI18n.tr(player, "gui.quests.inspect.objectives",
        Placeholder.unparsed("count", String.valueOf(objectiveCount))));

    QuestRewards rewards = spec.rewards();
    if (rewards != null) {
      if (rewards.xp() > 0) {
        lore.add(GuiI18n.tr(player, "gui.quests.inspect.rewardXp",
            Placeholder.unparsed("xp", String.valueOf(rewards.xp()))));
      }
      if (rewards.tokens() > 0) {
        lore.add(GuiI18n.tr(player, "gui.quests.inspect.rewardTokens",
            Placeholder.unparsed("tokens", String.valueOf(rewards.tokens()))));
      }
      if (rewards.items() != null && !rewards.items().isEmpty()) {
        lore.add(GuiI18n.tr(player, "gui.quests.inspect.rewardItems",
            Placeholder.unparsed("count", String.valueOf(rewards.items().size()))));
      }
    }
    if (spec.cooldownSeconds() > 0) {
      lore.add(GuiI18n.tr(player, "gui.quests.inspect.cooldown",
          Placeholder.unparsed("seconds", String.valueOf(spec.cooldownSeconds()))));
    }
    return lore;
  }
}
