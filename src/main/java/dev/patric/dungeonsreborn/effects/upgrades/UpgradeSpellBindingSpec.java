package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import dev.patric.dungeonsreborn.effects.Ids;

public record UpgradeSpellBindingSpec(
    String upgradeId,
    String abilityId,
    UpgradeActivator activator,
    Long cooldownTicks,
    Integer manaCost,
    Integer durabilityCost,
    Integer consumeAmount,
    UpgradeCooldownScope cooldownScope,
    boolean requireSneaking,
    boolean requireSprinting,
    boolean requireAirborne,
    boolean requireOnGround
) {
  public UpgradeSpellBindingSpec {
    Objects.requireNonNull(upgradeId, "upgradeId");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(activator, "activator");
    Objects.requireNonNull(cooldownScope, "cooldownScope");
  }

  public String toRecord() {
    StringBuilder out = new StringBuilder();
    out.append(Ids.normalize(upgradeId)).append('|');
    out.append(Ids.normalize(abilityId)).append('|');
    out.append(activator.name()).append('|');
    out.append(cooldownTicks == null ? "" : cooldownTicks).append('|');
    out.append(manaCost == null ? "" : manaCost).append('|');
    out.append(durabilityCost == null ? "" : durabilityCost).append('|');
    out.append(consumeAmount == null ? "" : consumeAmount).append('|');
    out.append(cooldownScope.name()).append('|');
    out.append(formatFlags());
    return out.toString();
  }

  public static UpgradeSpellBindingSpec fromRecord(String record) {
    if (record == null || record.isBlank()) {
      return null;
    }
    String[] parts = record.split("\\|", -1);
    if (parts.length < 3) {
      return null;
    }
    String upgradeId = parts[0].trim();
    String abilityId = parts[1].trim();
    String activatorRaw = parts[2].trim();
    if (upgradeId.isEmpty() || abilityId.isEmpty() || activatorRaw.isEmpty()) {
      return null;
    }
    UpgradeActivator activator;
    try {
      activator = UpgradeActivator.parse(activatorRaw, "upgrade_spell_binding.activator");
    } catch (Exception ex) {
      return null;
    }
    Long cooldown = parseLong(parts, 3);
    Integer mana = parseInt(parts, 4);
    Integer durability = parseInt(parts, 5);
    Integer consume = parseInt(parts, 6);
    UpgradeCooldownScope scope = parseScope(parts, 7);
    FlagSet flags = parseFlags(parts, 8);
    return new UpgradeSpellBindingSpec(
        upgradeId,
        abilityId,
        activator,
        cooldown,
        mana,
        durability,
        consume,
        scope,
        flags.requireSneaking,
        flags.requireSprinting,
        flags.requireAirborne,
        flags.requireOnGround
    );
  }

  public static List<UpgradeSpellBindingSpec> parseRecords(List<String> records) {
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    List<UpgradeSpellBindingSpec> out = new ArrayList<>();
    for (String record : records) {
      UpgradeSpellBindingSpec spec = fromRecord(record);
      if (spec != null) {
        out.add(spec);
      }
    }
    return List.copyOf(out);
  }

  public boolean matchesUpgrade(String id) {
    return id != null && Ids.normalize(id).equals(Ids.normalize(upgradeId));
  }

  private String formatFlags() {
    ArrayList<String> flags = new ArrayList<>(4);
    if (requireSneaking) {
      flags.add("SNEAK");
    }
    if (requireSprinting) {
      flags.add("SPRINT");
    }
    if (requireAirborne) {
      flags.add("AIR");
    }
    if (requireOnGround) {
      flags.add("GROUND");
    }
    return String.join(",", flags);
  }

  private static Long parseLong(String[] parts, int index) {
    if (parts.length <= index) {
      return null;
    }
    String raw = parts[index].trim();
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(raw);
    } catch (Exception ex) {
      return null;
    }
  }

  private static Integer parseInt(String[] parts, int index) {
    if (parts.length <= index) {
      return null;
    }
    String raw = parts[index].trim();
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(raw);
    } catch (Exception ex) {
      return null;
    }
  }

  private static UpgradeCooldownScope parseScope(String[] parts, int index) {
    if (parts.length <= index) {
      return UpgradeCooldownScope.PER_PLAYER;
    }
    String raw = parts[index].trim();
    if (raw.isEmpty()) {
      return UpgradeCooldownScope.PER_PLAYER;
    }
    try {
      return UpgradeCooldownScope.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      return UpgradeCooldownScope.PER_PLAYER;
    }
  }

  private static FlagSet parseFlags(String[] parts, int index) {
    if (parts.length <= index) {
      return new FlagSet();
    }
    String raw = parts[index].trim();
    if (raw.isEmpty()) {
      return new FlagSet();
    }
    FlagSet flags = new FlagSet();
    for (String entry : raw.split(",")) {
      if (entry == null) {
        continue;
      }
      String token = entry.trim().toUpperCase(Locale.ROOT);
      switch (token) {
        case "SNEAK", "SNEAKING" -> flags.requireSneaking = true;
        case "SPRINT", "SPRINTING" -> flags.requireSprinting = true;
        case "AIR", "AIRBORNE" -> flags.requireAirborne = true;
        case "GROUND", "ONGROUND", "ON_GROUND" -> flags.requireOnGround = true;
        default -> {
        }
      }
    }
    return flags;
  }

  private static final class FlagSet {
    private boolean requireSneaking;
    private boolean requireSprinting;
    private boolean requireAirborne;
    private boolean requireOnGround;
  }
}
