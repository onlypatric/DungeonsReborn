package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import net.kyori.adventure.text.Component;

public final class MobSpec {
  private final String id;
  private final EntityType entityType;
  private final String tier;
  private final MobModelSpec modelSpec;
  private final MobVisualSpec visualSpec;
  private final MobStyleSpec style;
  private final Component displayName;
  private final boolean showName;
  private final MobBossBarSpec bossBar;
  private final MobParticlesSpec spawnParticles;
  private final MobParticlesSpec deathParticles;
  private final MobSoundSpec spawnSound;
  private final MobSoundSpec deathSound;
  private final MobBroadcastSpec bossBroadcast;
  private final ItemStack mainHand;
  private final ItemStack offHand;
  private final ItemStack head;
  private final ItemStack chest;
  private final ItemStack legs;
  private final ItemStack feet;
  private final MobAiSpec aiSpec;
  private final MobAttackSpec mainAttack;
  private final MobAttackSpec secondaryAttack;
  private final List<MobPassiveSpec> passives;
  private final Map<Attribute, Double> attributes;
  private final List<MobVariantSpec> variants;
  private final List<MobTraitSpec> traits;
  private final List<MobPhaseSpec> phases;
  private final Map<DamageType, Double> resistances;
  private final MobManaDropSpec manaDrop;
  private final MobManaDrainSpec manaDrain;
  private final MobLootSpec loot;
  private final List<MobDamageBonusSpec> damageBonuses;
  private final MobCombatSpec combatSpec;
  private final MobSummonSpec summonSpec;
  private final MobProgressionSpec progressionSpec;
  private final MobAdvancementRewardSpec advancementRewards;
  private final int minXpLevel;
  private final boolean allowBlockDamage;
  private final Boolean invulnerable;
  private final Boolean collidable;
  private final boolean silent;
  private final double scaleVariance;
  private final MobCompositeSpec composite;
  private final MobEventSpec events;
  private final Consumer<MobContext> onSpawn;
  private final Consumer<MobContext> onDeath;
  private final BiConsumer<MobContext, MobRemovalReason> onRemove;

  private MobSpec(Builder builder) {
    this.id = Ids.normalize(builder.id);
    this.entityType = Objects.requireNonNull(builder.entityType, "entityType");
    this.tier = builder.tier;
    this.modelSpec = builder.modelSpec;
    this.visualSpec = builder.visualSpec;
    this.style = builder.style;
    this.displayName = builder.displayName;
    this.showName = builder.showName;
    this.bossBar = builder.bossBar;
    this.spawnParticles = builder.spawnParticles;
    this.deathParticles = builder.deathParticles;
    this.spawnSound = builder.spawnSound;
    this.deathSound = builder.deathSound;
    this.bossBroadcast = builder.bossBroadcast;
    this.mainHand = cloneItem(builder.mainHand);
    this.offHand = cloneItem(builder.offHand);
    this.head = cloneItem(builder.head);
    this.chest = cloneItem(builder.chest);
    this.legs = cloneItem(builder.legs);
    this.feet = cloneItem(builder.feet);
    this.aiSpec = builder.aiSpec;
    this.mainAttack = builder.mainAttack;
    this.secondaryAttack = builder.secondaryAttack;
    this.passives = Collections.unmodifiableList(new ArrayList<>(builder.passives));
    this.attributes = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(builder.attributes));
    this.variants = Collections.unmodifiableList(new ArrayList<>(builder.variants));
    this.traits = Collections.unmodifiableList(new ArrayList<>(builder.traits));
    this.phases = Collections.unmodifiableList(new ArrayList<>(builder.phases));
    this.resistances = Collections.unmodifiableMap(new java.util.EnumMap<>(builder.resistances));
    this.manaDrop = builder.manaDrop;
    this.manaDrain = builder.manaDrain;
    this.loot = builder.loot;
    this.damageBonuses = Collections.unmodifiableList(new ArrayList<>(builder.damageBonuses));
    this.combatSpec = builder.combatSpec;
    this.summonSpec = builder.summonSpec;
    this.progressionSpec = builder.progressionSpec;
    this.advancementRewards = builder.advancementRewards;
    this.minXpLevel = builder.minXpLevel;
    this.allowBlockDamage = builder.allowBlockDamage;
    this.invulnerable = builder.invulnerable;
    this.collidable = builder.collidable;
    this.silent = builder.silent;
    this.scaleVariance = builder.scaleVariance;
    this.composite = builder.composite;
    this.events = builder.events;
    this.onSpawn = builder.onSpawn;
    this.onDeath = builder.onDeath;
    this.onRemove = builder.onRemove;
  }

  public String id() {
    return id;
  }

  public EntityType entityType() {
    return entityType;
  }

  public String tier() {
    return tier;
  }

  public MobModelSpec modelSpec() {
    return modelSpec;
  }

  public MobVisualSpec visualSpec() {
    return visualSpec;
  }

  public MobStyleSpec style() {
    return style;
  }

  public Component displayName() {
    return displayName;
  }

  public boolean showName() {
    return showName;
  }

  public MobBossBarSpec bossBar() {
    return bossBar;
  }

  public MobParticlesSpec spawnParticles() {
    return spawnParticles;
  }

  public MobParticlesSpec deathParticles() {
    return deathParticles;
  }

  public MobSoundSpec spawnSound() {
    return spawnSound;
  }

  public MobSoundSpec deathSound() {
    return deathSound;
  }

  public MobBroadcastSpec bossBroadcast() {
    return bossBroadcast;
  }

  public ItemStack mainHand() {
    return cloneItem(mainHand);
  }

  public ItemStack offHand() {
    return cloneItem(offHand);
  }

  public ItemStack head() {
    return cloneItem(head);
  }

  public ItemStack chest() {
    return cloneItem(chest);
  }

  public ItemStack legs() {
    return cloneItem(legs);
  }

  public ItemStack feet() {
    return cloneItem(feet);
  }

  public MobAiSpec aiSpec() {
    return aiSpec;
  }

  public MobAttackSpec mainAttack() {
    return mainAttack;
  }

  public MobAttackSpec secondaryAttack() {
    return secondaryAttack;
  }

  public List<MobPassiveSpec> passives() {
    return passives;
  }

  public Map<Attribute, Double> attributes() {
    return attributes;
  }

  public List<MobVariantSpec> variants() {
    return variants;
  }

  public List<MobTraitSpec> traits() {
    return traits;
  }

  public List<MobPhaseSpec> phases() {
    return phases;
  }

  public Map<DamageType, Double> resistances() {
    return resistances;
  }

  public MobManaDropSpec manaDrop() {
    return manaDrop;
  }

  public MobManaDrainSpec manaDrain() {
    return manaDrain;
  }

  public MobLootSpec loot() {
    return loot;
  }

  public List<MobDamageBonusSpec> damageBonuses() {
    return damageBonuses;
  }

  public MobCombatSpec combatSpec() {
    return combatSpec;
  }

  public MobSummonSpec summonSpec() {
    return summonSpec;
  }

  public MobProgressionSpec progressionSpec() {
    return progressionSpec;
  }

  public MobAdvancementRewardSpec advancementRewards() {
    return advancementRewards;
  }

  public int minXpLevel() {
    return minXpLevel;
  }

  public boolean allowBlockDamage() {
    return allowBlockDamage;
  }

  public Boolean invulnerable() {
    return invulnerable;
  }

  public Boolean collidable() {
    return collidable;
  }

  public boolean silent() {
    return silent;
  }

  public double scaleVariance() {
    return scaleVariance;
  }

  public MobCompositeSpec composite() {
    return composite;
  }

  public MobEventSpec events() {
    return events;
  }

  public Consumer<MobContext> onSpawn() {
    return onSpawn;
  }

  public Consumer<MobContext> onDeath() {
    return onDeath;
  }

  public BiConsumer<MobContext, MobRemovalReason> onRemove() {
    return onRemove;
  }

  public static Builder builder(String id, EntityType type) {
    return new Builder(id, type);
  }

  public static final class Builder {
    private final String id;
    private final EntityType entityType;
    private String tier;
    private MobModelSpec modelSpec;
    private MobVisualSpec visualSpec;
    private MobStyleSpec style;
    private Component displayName;
    private boolean showName;
    private MobBossBarSpec bossBar;
    private MobParticlesSpec spawnParticles;
    private MobParticlesSpec deathParticles;
    private MobSoundSpec spawnSound;
    private MobSoundSpec deathSound;
    private MobBroadcastSpec bossBroadcast;
    private ItemStack mainHand;
    private ItemStack offHand;
    private ItemStack head;
    private ItemStack chest;
    private ItemStack legs;
    private ItemStack feet;
    private MobAiSpec aiSpec;
    private MobAttackSpec mainAttack;
    private MobAttackSpec secondaryAttack;
    private final List<MobPassiveSpec> passives = new ArrayList<>();
    private final Map<Attribute, Double> attributes = new java.util.LinkedHashMap<>();
    private final List<MobVariantSpec> variants = new ArrayList<>();
    private final List<MobTraitSpec> traits = new ArrayList<>();
    private final List<MobPhaseSpec> phases = new ArrayList<>();
    private final Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
    private MobManaDropSpec manaDrop;
    private MobManaDrainSpec manaDrain;
    private MobLootSpec loot;
    private final List<MobDamageBonusSpec> damageBonuses = new ArrayList<>();
    private MobCombatSpec combatSpec;
    private MobSummonSpec summonSpec;
    private MobProgressionSpec progressionSpec;
    private MobAdvancementRewardSpec advancementRewards;
    private int minXpLevel;
    private boolean allowBlockDamage = true;
    private Boolean invulnerable;
    private Boolean collidable;
    private boolean silent;
    private double scaleVariance;
    private MobCompositeSpec composite;
    private MobEventSpec events;
    private Consumer<MobContext> onSpawn = ctx -> {
    };
    private Consumer<MobContext> onDeath = ctx -> {
    };
    private BiConsumer<MobContext, MobRemovalReason> onRemove = (ctx, reason) -> {
    };

    private Builder(String id, EntityType entityType) {
      this.id = Objects.requireNonNull(id, "id");
      this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    public Builder displayName(Component displayName) {
      this.displayName = displayName;
      if (displayName != null) {
        this.showName = true;
      }
      return this;
    }

    public Builder displayName(String raw) {
      return displayName(MobText.parse(raw));
    }

    public Builder showName(boolean showName) {
      this.showName = showName;
      return this;
    }

    public Builder tier(String tier) {
      this.tier = tier == null || tier.isBlank() ? null : Ids.normalize(tier);
      return this;
    }

    public Builder modelSpec(MobModelSpec modelSpec) {
      this.modelSpec = modelSpec;
      return this;
    }

    public Builder visualSpec(MobVisualSpec visualSpec) {
      this.visualSpec = visualSpec;
      return this;
    }

    public Builder style(MobStyleSpec style) {
      this.style = style;
      return this;
    }

    public Builder bossBar(MobBossBarSpec bossBar) {
      this.bossBar = bossBar;
      return this;
    }

    public Builder spawnParticles(MobParticlesSpec spawnParticles) {
      this.spawnParticles = spawnParticles;
      return this;
    }

    public Builder deathParticles(MobParticlesSpec deathParticles) {
      this.deathParticles = deathParticles;
      return this;
    }

    public Builder spawnSound(MobSoundSpec spawnSound) {
      this.spawnSound = spawnSound;
      return this;
    }

    public Builder deathSound(MobSoundSpec deathSound) {
      this.deathSound = deathSound;
      return this;
    }

    public Builder bossBroadcast(MobBroadcastSpec bossBroadcast) {
      this.bossBroadcast = bossBroadcast;
      return this;
    }

    public Builder mainHand(ItemStack mainHand) {
      this.mainHand = cloneItem(mainHand);
      return this;
    }

    public Builder offHand(ItemStack offHand) {
      this.offHand = cloneItem(offHand);
      return this;
    }

    public Builder head(ItemStack head) {
      this.head = cloneItem(head);
      return this;
    }

    public Builder chest(ItemStack chest) {
      this.chest = cloneItem(chest);
      return this;
    }

    public Builder legs(ItemStack legs) {
      this.legs = cloneItem(legs);
      return this;
    }

    public Builder feet(ItemStack feet) {
      this.feet = cloneItem(feet);
      return this;
    }

    public Builder aiSpec(MobAiSpec aiSpec) {
      this.aiSpec = aiSpec;
      return this;
    }

    public Builder mainAttack(MobAttackSpec mainAttack) {
      this.mainAttack = mainAttack;
      return this;
    }

    public Builder secondaryAttack(MobAttackSpec secondaryAttack) {
      this.secondaryAttack = secondaryAttack;
      return this;
    }

    public Builder addPassive(MobPassiveSpec passive) {
      this.passives.add(Objects.requireNonNull(passive, "passive"));
      return this;
    }

    public Builder attribute(Attribute attribute, double value) {
      this.attributes.put(Objects.requireNonNull(attribute, "attribute"), value);
      return this;
    }

    public Builder addVariant(MobVariantSpec variant) {
      this.variants.add(Objects.requireNonNull(variant, "variant"));
      return this;
    }

    public Builder addTrait(MobTraitSpec trait) {
      this.traits.add(Objects.requireNonNull(trait, "trait"));
      return this;
    }

    public Builder addPhase(MobPhaseSpec phase) {
      this.phases.add(Objects.requireNonNull(phase, "phase"));
      return this;
    }

    public Builder resistance(DamageType type, double multiplier) {
      this.resistances.put(Objects.requireNonNull(type, "type"), multiplier);
      return this;
    }

    public Builder manaDrop(MobManaDropSpec manaDrop) {
      this.manaDrop = manaDrop;
      return this;
    }

    public Builder manaDrain(MobManaDrainSpec manaDrain) {
      this.manaDrain = manaDrain;
      return this;
    }

    public Builder loot(MobLootSpec loot) {
      this.loot = loot;
      return this;
    }

    public Builder addDamageBonus(MobDamageBonusSpec bonus) {
      if (bonus != null) {
        this.damageBonuses.add(bonus);
      }
      return this;
    }

    public Builder combatSpec(MobCombatSpec combatSpec) {
      this.combatSpec = combatSpec;
      return this;
    }

    public Builder summonSpec(MobSummonSpec summonSpec) {
      this.summonSpec = summonSpec;
      return this;
    }

    public Builder progressionSpec(MobProgressionSpec progressionSpec) {
      this.progressionSpec = progressionSpec;
      return this;
    }

    public Builder advancementRewards(MobAdvancementRewardSpec advancementRewards) {
      this.advancementRewards = advancementRewards;
      return this;
    }

    public Builder minXpLevel(int minXpLevel) {
      if (minXpLevel < 0) {
        throw new IllegalArgumentException("minXpLevel must be >= 0");
      }
      this.minXpLevel = minXpLevel;
      return this;
    }

    public Builder allowBlockDamage(boolean allowBlockDamage) {
      this.allowBlockDamage = allowBlockDamage;
      return this;
    }

    public Builder invulnerable(Boolean invulnerable) {
      this.invulnerable = invulnerable;
      return this;
    }

    public Builder collidable(Boolean collidable) {
      this.collidable = collidable;
      return this;
    }

    public Builder silent(boolean silent) {
      this.silent = silent;
      return this;
    }

    public Builder scaleVariance(double scaleVariance) {
      if (scaleVariance < 0.0) {
        throw new IllegalArgumentException("scaleVariance must be >= 0");
      }
      this.scaleVariance = scaleVariance;
      return this;
    }

    public Builder composite(MobCompositeSpec composite) {
      this.composite = composite;
      return this;
    }

    public Builder events(MobEventSpec events) {
      this.events = events;
      return this;
    }

    public Builder onSpawn(Consumer<MobContext> onSpawn) {
      this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
      return this;
    }

    public Builder onDeath(Consumer<MobContext> onDeath) {
      this.onDeath = Objects.requireNonNull(onDeath, "onDeath");
      return this;
    }

    public Builder onRemove(BiConsumer<MobContext, MobRemovalReason> onRemove) {
      this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
      return this;
    }

    public MobSpec build() {
      return new MobSpec(this);
    }
  }

  private static ItemStack cloneItem(ItemStack item) {
    return item == null ? null : item.clone();
  }
}
