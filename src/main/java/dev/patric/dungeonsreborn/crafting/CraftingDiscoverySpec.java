package dev.patric.dungeonsreborn.crafting;

import java.util.List;

public final class CraftingDiscoverySpec {
  private final boolean hidden;
  private final List<String> requires;
  private final List<String> grants;
  private final List<String> questUnlocks;
  private final List<String> dropItemIds;
  private final List<String> dropMaterials;
  private final boolean unlockOnCraft;
  private final boolean showInBook;
  private final int researchSeconds;

  public CraftingDiscoverySpec(boolean hidden,
                               List<String> requires,
                               List<String> grants,
                               List<String> questUnlocks,
                               List<String> dropItemIds,
                               List<String> dropMaterials,
                               boolean unlockOnCraft,
                               boolean showInBook,
                               int researchSeconds) {
    this.hidden = hidden;
    this.requires = List.copyOf(requires == null ? List.of() : requires);
    this.grants = List.copyOf(grants == null ? List.of() : grants);
    this.questUnlocks = List.copyOf(questUnlocks == null ? List.of() : questUnlocks);
    this.dropItemIds = List.copyOf(dropItemIds == null ? List.of() : dropItemIds);
    this.dropMaterials = List.copyOf(dropMaterials == null ? List.of() : dropMaterials);
    this.unlockOnCraft = unlockOnCraft;
    this.showInBook = showInBook;
    this.researchSeconds = Math.max(0, researchSeconds);
  }

  public static CraftingDiscoverySpec empty() {
    return new CraftingDiscoverySpec(false, List.of(), List.of(), List.of(), List.of(), List.of(), false, false, 0);
  }

  public boolean hidden() {
    return hidden;
  }

  public List<String> requires() {
    return requires;
  }

  public List<String> grants() {
    return grants;
  }

  public List<String> questUnlocks() {
    return questUnlocks;
  }

  public List<String> dropItemIds() {
    return dropItemIds;
  }

  public List<String> dropMaterials() {
    return dropMaterials;
  }

  public boolean unlockOnCraft() {
    return unlockOnCraft;
  }

  public boolean showInBook() {
    return showInBook;
  }

  public int researchSeconds() {
    return researchSeconds;
  }
}
