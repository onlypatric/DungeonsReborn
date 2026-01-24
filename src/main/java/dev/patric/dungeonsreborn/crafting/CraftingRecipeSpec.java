package dev.patric.dungeonsreborn.crafting;

import java.util.List;
import java.util.Objects;

public final class CraftingRecipeSpec {
  private final String id;
  private final String name;
  private final String description;
  private final List<String> permissions;
  private final double cooldownSeconds;
  private final List<CraftingRequirementSpec> requirements;
  private final List<CraftingCostSpec> costs;
  private final List<CraftingRecipeVariant> variants;
  private final List<CraftingOutputSpec> outputs;
  private final CraftingHookSpec hooks;
  private final CraftingDiscoverySpec discovery;
  private final String scriptFile;
  private final String scriptInline;

  public CraftingRecipeSpec(String id,
                            String name,
                            String description,
                            List<String> permissions,
                            double cooldownSeconds,
                            List<CraftingRequirementSpec> requirements,
                            List<CraftingCostSpec> costs,
                            List<CraftingRecipeVariant> variants,
                            List<CraftingOutputSpec> outputs,
                            CraftingHookSpec hooks,
                            CraftingDiscoverySpec discovery,
                            String scriptFile,
                            String scriptInline) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = name == null ? "" : name;
    this.description = description == null ? "" : description;
    this.permissions = List.copyOf(permissions == null ? List.of() : permissions);
    this.cooldownSeconds = Math.max(0.0, cooldownSeconds);
    this.requirements = List.copyOf(requirements == null ? List.of() : requirements);
    this.costs = List.copyOf(costs == null ? List.of() : costs);
    this.variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
    this.outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
    this.hooks = hooks;
    this.discovery = discovery == null ? CraftingDiscoverySpec.empty() : discovery;
    this.scriptFile = scriptFile;
    this.scriptInline = scriptInline;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public List<String> permissions() {
    return permissions;
  }

  public double cooldownSeconds() {
    return cooldownSeconds;
  }

  public List<CraftingRequirementSpec> requirements() {
    return requirements;
  }

  public List<CraftingCostSpec> costs() {
    return costs;
  }

  public List<CraftingRecipeVariant> variants() {
    return variants;
  }

  public List<CraftingOutputSpec> outputs() {
    return outputs;
  }

  public CraftingOutputSpec output() {
    return outputs.isEmpty() ? null : outputs.get(0);
  }

  public CraftingHookSpec hooks() {
    return hooks;
  }

  public CraftingDiscoverySpec discovery() {
    return discovery;
  }

  public String scriptFile() {
    return scriptFile;
  }

  public String scriptInline() {
    return scriptInline;
  }
}
