package dev.patric.dungeonsreborn.menus;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.menu.ClassSelectMenu;
import dev.patric.dungeonsreborn.classes.menu.ClassSkillTreeMenu;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.dungeons.menu.DungeonQueueMenu;
import dev.patric.dungeonsreborn.dungeons.menu.DungeonStatusMenu;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.kits.KitService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class UserHubMenu extends Window {
  private static final int SIZE = 54;

  private final ClassYamlRegistry classes;
  private final ClassService classService;
  private final ClassSkillService classSkills;
  private final DungeonYamlRegistry dungeons;
  private final DungeonQueueService dungeonQueue;
  private final DungeonSessionManager dungeonSessions;
  private final QuestService quests;
  private final PartyService parties;
  private final CraftingYamlRegistry crafting;
  private final CraftingGuiSessionManager craftingSessions;
  private final AdvancementService advancements;
  private final UpgradeService upgrades;
  private final KitService kits;

  public UserHubMenu(ClassYamlRegistry classes, ClassService classService, ClassSkillService classSkills,
      DungeonYamlRegistry dungeons, DungeonQueueService dungeonQueue, DungeonSessionManager dungeonSessions,
      QuestService quests, PartyService parties, CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions, AdvancementService advancements, UpgradeService upgrades, KitService kits) {
    super(SIZE, GuiI18n.tr("gui.userHub.title"), true);
    this.classes = classes;
    this.classService = classService;
    this.classSkills = classSkills;
    this.dungeons = dungeons;
    this.dungeonQueue = dungeonQueue;
    this.dungeonSessions = dungeonSessions;
    this.quests = quests;
    this.parties = parties;
    this.crafting = crafting;
    this.craftingSessions = craftingSessions;
    this.advancements = advancements;
    this.upgrades = upgrades;
    this.kits = kits;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(4, new Label(GuiItems.named(Material.COMPASS, GuiI18n.tr("gui.userHub.header.title"), List.of(
        GuiI18n.tr("gui.userHub.header.subtitle")))));

    setFixed(19, button(Material.DIAMOND_SWORD, "gui.userHub.dungeonQueue.title",
        "gui.userHub.dungeonQueue.hint",
        player -> {
          if (this.dungeons == null || this.dungeonQueue == null) {
            sendUnavailable(player, "labels.system.dungeons");
            return;
          }
          new DungeonQueueMenu(this.dungeons, this.dungeonQueue).open(player);
        }));
    setFixed(20, button(Material.BEACON, "gui.userHub.dungeonStatus.title",
        "gui.userHub.dungeonStatus.hint",
        player -> {
          if (this.dungeons == null) {
            sendUnavailable(player, "labels.system.dungeons");
            return;
          }
          new DungeonStatusMenu(this.dungeons, this.dungeonQueue, this.dungeonSessions).open(player);
        }));
    setFixed(22, button(Material.WRITABLE_BOOK, "gui.userHub.questLog.title",
        "gui.userHub.questLog.hint",
        player -> {
          if (this.quests == null) {
            sendUnavailable(player, "labels.system.quests");
            return;
          }
          new QuestLogMenu(this.quests).open(player);
        }));
    setFixed(24, button(Material.BOOK, "gui.userHub.classes.title",
        "gui.userHub.classes.hint",
        player -> {
          if (this.classes == null || this.classService == null) {
            sendUnavailable(player, "labels.system.classes");
            return;
          }
          new ClassSelectMenu(this.classes, this.classService).open(player);
        }));
    setFixed(25, button(Material.ENCHANTED_BOOK, "gui.userHub.skills.title",
        "gui.userHub.skills.hint",
        this::openSkills));
    setFixed(31, button(Material.PLAYER_HEAD, "gui.userHub.party.title",
        "gui.userHub.party.hint",
        player -> {
          if (this.parties == null) {
            sendUnavailable(player, "labels.system.party");
            return;
          }
          new PartyMenu(this.parties).open(player);
        }));
    setFixed(32, button(Material.CRAFTING_TABLE, "gui.userHub.crafting.title",
        "gui.userHub.crafting.hint",
        player -> {
          if (this.crafting == null || this.craftingSessions == null) {
            sendUnavailable(player, "labels.system.crafting");
            return;
          }
          new CraftingTestMenu(this.crafting, this.craftingSessions, this.advancements, this.quests).open(player);
        }));
    setFixed(33, button(Material.ENCHANTED_BOOK, "gui.userHub.upgrades.title",
        "gui.userHub.upgrades.hint",
        player -> {
          if (this.upgrades == null) {
            sendUnavailable(player, "labels.system.upgrades");
            return;
          }
          new UpgradeMergeMenu(this.upgrades).open(player);
        }));
    setFixed(34, button(Material.CHEST, "gui.userHub.kits.title",
        "gui.userHub.kits.hint",
        player -> {
          if (this.kits == null) {
            sendUnavailable(player, "labels.system.kits");
            return;
          }
          new KitsMenu(this.kits).open(player);
        }));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Button button(Material material, String nameKey, String hintKey, java.util.function.Consumer<Player> action) {
    return new Button(p -> GuiItems.named(material, GuiI18n.tr(p, nameKey), List.of(GuiI18n.tr(p, hintKey))), ctx -> {
      if (action != null && ctx.player() != null) {
        action.accept(ctx.player());
      }
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private void openSkills(Player player) {
    if (player == null || classes == null || classService == null || classSkills == null) {
      return;
    }
    String classId = classService.currentClassId(player.getUniqueId());
    if (classId == null) {
      player.sendMessage(GuiI18n.tr(player, "gui.userHub.skills.selectClass"));
      new ClassSelectMenu(classes, classService).open(player);
      return;
    }
    ClassSpec spec = classes.classSpec(classId);
    if (spec == null) {
      player.sendMessage(GuiI18n.tr(player, "gui.userHub.skills.missingClass", Placeholder.unparsed("id", classId)));
      new ClassSelectMenu(classes, classService).open(player);
      return;
    }
    new ClassSkillTreeMenu(spec, classSkills).open(player);
  }

  private void sendUnavailable(Player player, String systemKey) {
    if (player == null) {
      return;
    }
    String system = Locales.text(player, systemKey);
    player.sendMessage(Locales.component(player, "messages.command.systemUnavailable", Locales.placeholders("system", system)));
  }
}
