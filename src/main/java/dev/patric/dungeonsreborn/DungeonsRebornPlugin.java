package dev.patric.dungeonsreborn;

import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.commands.FlySpeedCommand;
import dev.patric.dungeonsreborn.commands.EffectsCommand;
import dev.patric.dungeonsreborn.commands.GuiCommand;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.ItemSyncListener;
import dev.patric.dungeonsreborn.effects.mana.ManaSessionListener;
import dev.patric.dungeonsreborn.effects.mana.ManaDropListener;
import dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.config.YamlVarsSessionListener;
import dev.patric.dungeonsreborn.effects.damage.DamageMechanicsListener;
import dev.patric.dungeonsreborn.effects.editor.EditorDraftStore;
import dev.patric.dungeonsreborn.effects.editor.EditorAccessController;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditLogger;
import dev.patric.dungeonsreborn.effects.editor.EditorLockListener;
import dev.patric.dungeonsreborn.effects.editor.EditorLockManager;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.registry.BuiltinTypes;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpawnManager;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.commands.MobsCommand;
import dev.patric.dungeonsreborn.mobs.MobEggListener;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.style.GuiStyles;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;

public final class DungeonsRebornPlugin extends JavaPlugin {
    private EffectsEngine effectsEngine;
    private EffectsBindings effectsBindings;
    private EffectsYamlAbilities yamlAbilities;
    private EditorDraftStore editorDraftStore;
    private EditorLockManager editorLockManager;
    private EditorAuditLogger editorAuditLogger;
    private EditorAccessController editorAccessController;
    private EditorServices editorServices;
    private MobRegistry mobRegistry;
    private MobSpawnManager mobSpawnManager;
    private MobYamlRegistry mobYamlRegistry;
    private MinionManager minionManager;

    @Override
    public void onEnable() {
        getLogger().info("DungeonsReborn enabled");
        GuiManager.init(this);
        GuiStyles.installButtonDefaults();

        effectsEngine = EffectsEngine.init(this);
        SessionManaProvider manaProvider = new SessionManaProvider(100.0);
        effectsEngine.setManaProvider(manaProvider);
        effectsEngine.enableManaRegen(20L, 5.0);
        Bukkit.getPluginManager().registerEvents(new ManaSessionListener(manaProvider), this);
        Bukkit.getPluginManager().registerEvents(new ManaDropListener(effectsEngine), this);
        Bukkit.getPluginManager().registerEvents(new DamageMechanicsListener(effectsEngine), this);
        mobRegistry = new MobRegistry(effectsEngine);
        Bukkit.getPluginManager().registerEvents(mobRegistry, this);
        mobSpawnManager = new MobSpawnManager(effectsEngine, mobRegistry);
        Bukkit.getPluginManager().registerEvents(mobSpawnManager, this);
        minionManager = new MinionManager(effectsEngine, mobRegistry);
        mobRegistry.setMinionManager(minionManager);
        Bukkit.getPluginManager().registerEvents(minionManager, this);

        BuiltinTypes.registerAll(effectsEngine);

        effectsBindings = new EffectsBindings(effectsEngine);
        Bukkit.getPluginManager().registerEvents(effectsBindings, this);

        yamlAbilities = new EffectsYamlAbilities(this, effectsEngine, effectsBindings);
        yamlAbilities.reload();
        Bukkit.getPluginManager().registerEvents(new YamlVarsSessionListener(yamlAbilities), this);
        Bukkit.getPluginManager().registerEvents(new ItemSyncListener(yamlAbilities), this);
        yamlAbilities.syncOnlineItems();
        editorDraftStore = new EditorDraftStore(this);
        editorAuditLogger = new EditorAuditLogger(getLogger());
        editorLockManager = new EditorLockManager();
        editorAccessController = new EditorAccessController();
        editorServices = new EditorServices(effectsEngine, yamlAbilities, editorDraftStore, editorAccessController, editorLockManager, editorAuditLogger);
        Bukkit.getPluginManager().registerEvents(new EditorLockListener(editorLockManager), this);

        mobYamlRegistry = new MobYamlRegistry(this, effectsEngine, yamlAbilities, mobRegistry, mobSpawnManager);
        mobYamlRegistry.reload();
        Bukkit.getPluginManager().registerEvents(new MobEggListener(effectsEngine, mobRegistry, mobYamlRegistry), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,commands->{
            commands.registrar().register(
                FlySpeedCommand.createCommand().build()
            );
            commands.registrar().register(
                GuiCommand.createCommand().build()
            );
            commands.registrar().register(
                EffectsCommand.createCommand(effectsEngine, yamlAbilities, effectsBindings, editorServices, minionManager).build()
            );
            commands.registrar().register(
                MobsCommand.createCommand(mobYamlRegistry, mobRegistry).build()
            );
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("DungeonsReborn disabled");
        if (effectsEngine != null) {
            effectsEngine.shutdown();
            effectsEngine = null;
        }
        effectsBindings = null;
        yamlAbilities = null;
        editorDraftStore = null;
        editorLockManager = null;
        editorAuditLogger = null;
        editorAccessController = null;
        editorServices = null;
        mobRegistry = null;
        mobSpawnManager = null;
        mobYamlRegistry = null;
        minionManager = null;
    }

    public MobRegistry mobRegistry() {
        return mobRegistry;
    }

    public MobYamlRegistry mobYamlRegistry() {
        return mobYamlRegistry;
    }

    public MinionManager minionManager() {
        return minionManager;
    }
}
