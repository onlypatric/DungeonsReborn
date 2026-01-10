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
  private final Component displayName;
  private final boolean showName;
  private final MobBossBarSpec bossBar;
  private final MobParticlesSpec spawnParticles;
  private final MobParticlesSpec deathParticles;
  private final MobSoundSpec spawnSound;
  private final MobSoundSpec deathSound;
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
  private final List<MobPhaseSpec> phases;
  private final Map<DamageType, Double> resistances;
  private final MobManaDropSpec manaDrop;
  private final MobLootSpec loot;
  private final MobSummonSpec summonSpec;
  private final Consumer<MobContext> onSpawn;
  private final Consumer<MobContext> onDeath;
  private final BiConsumer<MobContext, MobRemovalReason> onRemove;

  private MobSpec(Builder builder) {
    this.id = Ids.normalize(builder.id);
    this.entityType = Objects.requireNonNull(builder.entityType, "entityType");
    this.displayName = builder.displayName;
    this.showName = builder.showName;
    this.bossBar = builder.bossBar;
    this.spawnParticles = builder.spawnParticles;
    this.deathParticles = builder.deathParticles;
    this.spawnSound = builder.spawnSound;
    this.deathSound = builder.deathSound;
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
    this.phases = Collections.unmodifiableList(new ArrayList<>(builder.phases));
    this.resistances = Collections.unmodifiableMap(new java.util.EnumMap<>(builder.resistances));
    this.manaDrop = builder.manaDrop;
    this.loot = builder.loot;
    this.summonSpec = builder.summonSpec;
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

  public List<MobPhaseSpec> phases() {
    return phases;
  }

  public Map<DamageType, Double> resistances() {
    return resistances;
  }

  public MobManaDropSpec manaDrop() {
    return manaDrop;
  }

  public MobLootSpec loot() {
    return loot;
  }

  public MobSummonSpec summonSpec() {
    return summonSpec;
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
    private Component displayName;
    private boolean showName;
    private MobBossBarSpec bossBar;
    private MobParticlesSpec spawnParticles;
    private MobParticlesSpec deathParticles;
    private MobSoundSpec spawnSound;
    private MobSoundSpec deathSound;
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
    private final List<MobPhaseSpec> phases = new ArrayList<>();
    private final Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
    private MobManaDropSpec manaDrop;
    private MobLootSpec loot;
    private MobSummonSpec summonSpec;
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

    public Builder loot(MobLootSpec loot) {
      this.loot = loot;
      return this;
    }

    public Builder summonSpec(MobSummonSpec summonSpec) {
      this.summonSpec = summonSpec;
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
