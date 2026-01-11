package dev.patric.dungeonsreborn.crafting;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

public final class CraftingRecipeTemplate {
  private final CraftingRecipeSpec spec;
  private final java.util.List<ItemStack> outputTemplates;

  public CraftingRecipeTemplate(CraftingRecipeSpec spec, java.util.List<ItemStack> outputTemplates) {
    this.spec = Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(outputTemplates, "outputTemplates");
    java.util.ArrayList<ItemStack> clones = new java.util.ArrayList<>(outputTemplates.size());
    for (ItemStack stack : outputTemplates) {
      clones.add(stack == null ? null : stack.clone());
    }
    this.outputTemplates = java.util.Collections.unmodifiableList(clones);
  }

  public CraftingRecipeSpec spec() {
    return spec;
  }

  public java.util.List<ItemStack> outputTemplates() {
    return outputTemplates;
  }

  public ItemStack outputTemplate() {
    if (outputTemplates.isEmpty()) {
      return null;
    }
    ItemStack first = outputTemplates.get(0);
    return first == null ? null : first.clone();
  }
}
