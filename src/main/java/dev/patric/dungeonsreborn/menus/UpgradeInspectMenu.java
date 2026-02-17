package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeTemplate;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class UpgradeInspectMenu extends Window {
  private final UpgradeService upgrades;
  private final UpgradeTemplate template;

  public UpgradeInspectMenu(UpgradeService upgrades, UpgradeTemplate template) {
    super(54, GuiI18n.tr("gui.upgrades.inspect.title",
        Placeholder.component("upgrade", template == null ? Component.empty() : GuiMini.mm(template.spec().name()))));
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
    this.template = Objects.requireNonNull(template, "template");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(2, 4, new Label(this::upgradeItem));
    setFixedAt(3, 4, new Label(this::infoItem));
    setFixedAt(rows() - 1, 5, applyButton());
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.upgrades.inspect.header",
        Placeholder.component("upgrade", GuiMini.mm(template.spec().name())));
    return GuiItems.head("ICON_UPGRADES", title, List.of());
  }

  private ItemStack upgradeItem(Player player) {
    ItemStack item = template.buildItem();
    return GuiItems.named(item, GuiMini.mm(template.spec().name()), List.of(), true);
  }

  private ItemStack infoItem(Player player) {
    UpgradeSpec spec = template.spec();
    List<Component> lore = new ArrayList<>();
    if (spec.description() != null && !spec.description().isBlank()) {
      lore.add(GuiMini.mm(spec.description()));
    }
    lore.add(GuiI18n.tr(player, "gui.upgrades.inspect.modifiers",
        Placeholder.unparsed("count", String.valueOf(spec.modifiers().size()))));
    lore.add(GuiI18n.tr(player, "gui.upgrades.inspect.attributes",
        Placeholder.unparsed("count", String.valueOf(spec.attributes().size()))));
    lore.add(GuiI18n.tr(player, "gui.upgrades.inspect.enchants",
        Placeholder.unparsed("count", String.valueOf(spec.enchants().size()))));
    lore.add(GuiI18n.tr(player, "gui.upgrades.inspect.spells",
        Placeholder.unparsed("count", String.valueOf(spec.spells().size()))));
    return GuiItems.head("ICON_UPGRADES", GuiI18n.tr(player, "gui.upgrades.inspect.info"), lore);
  }

  private Button applyButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CONFIRM,
        GuiI18n.tr(player, "gui.upgrades.inspect.apply.title"),
        List.of(GuiI18n.tr(player, "gui.upgrades.inspect.apply.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Player player = ctx.player();
      ItemStack target = player.getInventory().getItemInMainHand();
      if (target == null || target.getType() == Material.AIR) {
        player.sendMessage(GuiMini.mm("<red>Hold a target item in your main hand.</red>"));
        return;
      }
      ItemStack upgradeItem = template.buildItem();
      var result = upgrades.apply(player, target, upgradeItem);
      if (!result.success()) {
        String message = result.error() == null ? "Upgrade failed." : result.error();
        player.sendMessage(GuiMini.mm("<red>" + message + "</red>"));
        return;
      }
      player.getInventory().setItemInMainHand(result.updated());
      player.sendMessage(GuiMini.mm("<green>Upgrade applied.</green>"));
      ctx.window().redraw(player);
    });
    button.autoDescribeInLore(false);
    return button;
  }
}
