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
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
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
import dev.patric.dungeonsreborn.quests.QuestGiverYamlRegistry;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
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
  private final QuestGiverYamlRegistry questGivers;
  private final PartyService parties;
  private final CraftingYamlRegistry crafting;
  private final CraftingGuiSessionManager craftingSessions;
  private final AdvancementService advancements;
  private final UpgradeService upgrades;
  private final KitService kits;
  private final MobRegistry mobs;
  private final EffectsYamlAbilities effects;
  private final ShopYamlRegistry shops;

  public UserHubMenu(ClassYamlRegistry classes, ClassService classService, ClassSkillService classSkills,
      DungeonYamlRegistry dungeons, DungeonQueueService dungeonQueue, DungeonSessionManager dungeonSessions,
      QuestService quests, QuestGiverYamlRegistry questGivers, PartyService parties, CraftingYamlRegistry crafting,
      CraftingGuiSessionManager craftingSessions, AdvancementService advancements, UpgradeService upgrades, KitService kits,
      MobRegistry mobs, EffectsYamlAbilities effects, ShopYamlRegistry shops) {
    super(SIZE, GuiI18n.tr("gui.userHub.title"), true);
    this.classes = classes;
    this.classService = classService;
    this.classSkills = classSkills;
    this.dungeons = dungeons;
    this.dungeonQueue = dungeonQueue;
    this.dungeonSessions = dungeonSessions;
    this.quests = quests;
    this.questGivers = questGivers;
    this.parties = parties;
    this.crafting = crafting;
    this.craftingSessions = craftingSessions;
    this.advancements = advancements;
    this.upgrades = upgrades;
    this.kits = kits;
    this.mobs = mobs;
    this.effects = effects;
    this.shops = shops;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(4, new Label(GuiItems.named(Material.COMPASS, GuiI18n.tr("gui.userHub.header.title"), List.of(
        GuiI18n.tr("gui.userHub.header.subtitle")))));

    setFixed(9, new Label(GuiItems.named(Material.NETHER_STAR, GuiI18n.tr("gui.userHub.section.play.title"),
        List.of(GuiI18n.tr("gui.userHub.section.play.hint")))));
    setFixed(10, button(Material.NETHER_STAR, "gui.userHub.classes.title",
        "gui.userHub.classes.hint",
        player -> {
          if (this.classes == null || this.classService == null || this.classSkills == null) {
            sendUnavailable(player, "labels.system.classes");
            return;
          }
          new ClassSelectMenu(this.classes, this.classService, this.classSkills).open(player);
        }));
    setFixed(11, button(Material.BOOK, "gui.userHub.skills.title",
        "gui.userHub.skills.hint",
        this::openSkills));
    setFixed(12, button(Material.ENCHANTED_BOOK, "gui.userHub.upgrades.title",
        "gui.userHub.upgrades.hint",
        player -> {
          if (this.upgrades == null) {
            sendUnavailable(player, "labels.system.upgrades");
            return;
          }
          new UpgradeMergeMenu(this.upgrades).open(player);
        }));
    setFixed(13, button(Material.CRAFTING_TABLE, "gui.userHub.crafting.title",
        "gui.userHub.crafting.hint",
        player -> {
          if (this.crafting == null || this.craftingSessions == null) {
            sendUnavailable(player, "labels.system.crafting");
            return;
          }
          new CraftingTestMenu(this.crafting, this.craftingSessions, this.advancements, this.quests).open(player);
        }));
    setFixed(19, button(Material.WRITABLE_BOOK, "gui.userHub.questGivers.title",
        "gui.userHub.questGivers.hint",
        player -> {
          if (this.questGivers == null) {
            sendUnavailable(player, "labels.system.quests");
            return;
          }
          new QuestGiverIndexMenu(this.questGivers).open(player);
        }));
    setFixed(20, button(Material.NETHER_STAR, "gui.userHub.dungeonQueue.title",
        "gui.userHub.dungeonQueue.hint",
        player -> {
          if (this.dungeons == null || this.dungeonQueue == null) {
            sendUnavailable(player, "labels.system.dungeons");
            return;
          }
          new DungeonQueueMenu(this.dungeons, this.dungeonQueue).open(player);
        }));
    setFixed(21, button(Material.MAP, "gui.userHub.dungeonStatus.title",
        "gui.userHub.dungeonStatus.hint",
        player -> {
          if (this.dungeons == null || this.dungeonQueue == null || this.dungeonSessions == null) {
            sendUnavailable(player, "labels.system.dungeons");
            return;
          }
          new DungeonStatusMenu(this.dungeons, this.dungeonQueue, this.dungeonSessions).open(player);
        }));

    setFixed(36, new Label(GuiItems.named(Material.BOOKSHELF, GuiI18n.tr("gui.userHub.section.index.title"),
        List.of(GuiI18n.tr("gui.userHub.section.index.hint")))));
    setFixed(37, button(Material.ZOMBIE_HEAD, "gui.userHub.mobIndex.title",
        "gui.userHub.mobIndex.hint",
        player -> {
          if (this.mobs == null) {
            sendUnavailable(player, "labels.system.mobsRegistry");
            return;
          }
          new MobIndexMenu(this.mobs).open(player);
        }));
    setFixed(38, button(Material.CHEST, "gui.userHub.itemIndex.title",
        "gui.userHub.itemIndex.hint",
        player -> {
          if (this.effects == null) {
            sendUnavailable(player, "labels.system.itemsEditor");
            return;
          }
          new ItemIndexMenu(this.effects).open(player);
        }));
    setFixed(39, button(Material.BOOK, "gui.userHub.upgradeIndex.title",
        "gui.userHub.upgradeIndex.hint",
        player -> {
          if (this.upgrades == null) {
            sendUnavailable(player, "labels.system.upgrades");
            return;
          }
          new UpgradeIndexMenu(this.upgrades).open(player);
        }));
    setFixed(40, button(Material.ANVIL, "gui.userHub.craftablesIndex.title",
        "gui.userHub.craftablesIndex.hint",
        player -> {
          if (this.crafting == null) {
            sendUnavailable(player, "labels.system.crafting");
            return;
          }
          new CraftablesIndexMenu(this.crafting).open(player);
        }));
    setFixed(41, button(Material.EMERALD, "gui.userHub.shopIndex.title",
        "gui.userHub.shopIndex.hint",
        player -> {
          if (this.shops == null) {
            sendUnavailable(player, "labels.system.shops");
            return;
          }
          new ShopIndexMenu(this.shops).open(player);
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
      new ClassSelectMenu(classes, classService, classSkills).open(player);
      return;
    }
    ClassSpec spec = classes.classSpec(classId);
    if (spec == null) {
      player.sendMessage(GuiI18n.tr(player, "gui.userHub.skills.missingClass", Placeholder.unparsed("id", classId)));
      new ClassSelectMenu(classes, classService, classSkills).open(player);
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

  private void sendComingSoon(Player player) {
    if (player == null) {
      return;
    }
    player.sendMessage(Locales.component(player, "messages.userHub.comingSoon"));
  }
}
