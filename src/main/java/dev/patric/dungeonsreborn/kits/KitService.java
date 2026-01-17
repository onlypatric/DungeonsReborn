package dev.patric.dungeonsreborn.kits;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;

public final class KitService {
  public record ClaimResult(boolean success, String message) {
  }

  public record KitStatus(boolean available, String message, long remainingMillis) {
  }

  private final KitYamlRegistry registry;
  private final KitClaimRepository claims;
  private final ShopYamlRegistry shopRegistry;
  private final Function<String, ItemStack> itemResolver;
  private final AdvancementService advancements;
  private final CustomXpService customXpService;
  private final Logger logger;

  public KitService(KitYamlRegistry registry, KitClaimRepository claims,
      ShopYamlRegistry shopRegistry, Function<String, ItemStack> itemResolver,
      AdvancementService advancements, CustomXpService customXpService, Logger logger) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.claims = Objects.requireNonNull(claims, "claims");
    this.shopRegistry = shopRegistry;
    this.itemResolver = itemResolver;
    this.advancements = advancements;
    this.customXpService = customXpService;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public KitYamlRegistry registry() {
    return registry;
  }

  public KitStatus status(Player player, KitSpec kit) {
    if (player == null || kit == null) {
      return new KitStatus(false, Locales.text(null, "messages.kits.status.unavailable"), 0L);
    }
    UUID uuid = player.getUniqueId();
    OptionalLong last = claims.lastClaim(uuid, kit.id());
    if (kit.oneTime() && last.isPresent() && last.getAsLong() > 0L) {
      return new KitStatus(false, Locales.text(player, "messages.kits.status.alreadyClaimed"), 0L);
    }
    if (kit.cooldownSeconds() > 0 && last.isPresent() && last.getAsLong() > 0L) {
      long next = last.getAsLong() + kit.cooldownSeconds() * 1000L;
      long now = System.currentTimeMillis();
      if (now < next) {
        long remaining = next - now;
        return new KitStatus(false, Locales.text(player, "messages.kits.status.cooldown",
            Map.of("time", formatDuration(remaining))), remaining);
      }
    }
    return new KitStatus(true, Locales.text(player, "messages.kits.status.available"), 0L);
  }

  public List<ItemStack> previewItems(KitSpec kit) {
    if (kit == null) {
      return List.of();
    }
    return resolveItems(kit);
  }

  public ClaimResult claim(Player player, String kitId) {
    if (player == null) {
      return new ClaimResult(false, Locales.text(null, "messages.kits.claim.playersOnly"));
    }
    if (kitId == null || kitId.isBlank()) {
      return new ClaimResult(false, Locales.text(player, "messages.kits.claim.missingId"));
    }
    KitSpec kit = registry.kit(kitId);
    if (kit == null) {
      return new ClaimResult(false, Locales.text(player, "messages.kits.claim.unknown",
          Map.of("id", kitId)));
    }
    if (kit.permission() != null && !kit.permission().isBlank()
        && !player.hasPermission(kit.permission())) {
      return new ClaimResult(false, Locales.text(player, "messages.kits.claim.missingPermission",
          Map.of("permission", kit.permission())));
    }
    UUID uuid = player.getUniqueId();
    long now = System.currentTimeMillis();
    OptionalLong last = claims.lastClaim(uuid, kit.id());
    if (kit.oneTime() && last.isPresent() && last.getAsLong() > 0L) {
      return new ClaimResult(false, Locales.text(player, "messages.kits.claim.alreadyClaimed"));
    }
    if (kit.cooldownSeconds() > 0 && last.isPresent() && last.getAsLong() > 0L) {
      long next = last.getAsLong() + kit.cooldownSeconds() * 1000L;
      if (now < next) {
        return new ClaimResult(false, Locales.text(player, "messages.kits.claim.cooldown",
            Map.of("time", formatDuration(next - now))));
      }
    }
    List<ItemStack> items = resolveItems(kit);
    giveItems(player, items);
    giveRewards(player, kit.rewards());
    sendSummary(player, kit, items, kit.rewards());
    claims.markClaimed(uuid, kit.id(), now);
    return new ClaimResult(true, Locales.text(player, "messages.kits.claim.success",
        Map.of("title", kit.title())));
  }

  private List<ItemStack> resolveItems(KitSpec kit) {
    List<ItemStack> out = new ArrayList<>();
    for (KitItemSpec spec : kit.items()) {
      if (spec == null) {
        continue;
      }
      ItemStack stack = spec.resolve(itemResolver);
      if (stack == null) {
        logger.warning("[Kits] Item resolver returned null for kit " + kit.id());
        continue;
      }
      out.add(stack);
    }
    return out;
  }

  private void giveItems(Player player, List<ItemStack> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    for (ItemStack item : items) {
      giveItemOrDrop(player, item);
    }
  }

  private void giveRewards(Player player, KitRewards rewards) {
    if (rewards == null || rewards.isEmpty() || player == null) {
      return;
    }
    if (rewards.xp() > 0) {
      if (customXpService != null) {
        customXpService.awardXp(player, rewards.xp());
      } else {
        player.giveExp(rewards.xp());
      }
    }
    if (shopRegistry == null || shopRegistry.tokenSpec() == null || shopRegistry.tokenSpec().item() == null) {
      if (rewards.tokens() > 0 || rewards.compressed() > 0 || rewards.pallet() > 0) {
        logger.warning("[Kits] Token rewards skipped (shop tokens not configured).");
      }
      return;
    }
    int totalTokens = rewards.tokens() + rewards.compressed() * 64 + rewards.pallet() * 4096;
    giveTokenStacks(player, shopRegistry.tokenSpec().item(), rewards.tokens());
    ShopTokenTierSpec compressed = shopRegistry.tokenTier("compressed");
    if (compressed != null && compressed.item() != null) {
      giveTokenStacks(player, compressed.item(), rewards.compressed());
    }
    ShopTokenTierSpec pallet = shopRegistry.tokenTier("pallet");
    if (pallet != null && pallet.item() != null) {
      giveTokenStacks(player, pallet.item(), rewards.pallet());
    }
    if (advancements != null && totalTokens > 0) {
      advancements.recordTokensEarned(player, totalTokens);
    }
  }

  private void sendSummary(Player player, KitSpec kit, List<ItemStack> items, KitRewards rewards) {
    if (player == null || kit == null) {
      return;
    }
    int itemCount = items == null ? 0 : items.size();
    boolean hasRewards = rewards != null && !rewards.isEmpty();
    if (itemCount == 0 && !hasRewards) {
      player.sendMessage(Locales.component(player, "messages.kits.summary.claimedSimple",
          Locales.placeholders("title", kit.title())));
      return;
    }
    player.sendMessage(Locales.component(player, "messages.kits.summary.header"));
    player.sendMessage(Locales.component(player, "messages.kits.summary.kit",
        Locales.placeholders("title", kit.title())));
    if (itemCount > 0) {
      player.sendMessage(Locales.component(player, "messages.kits.summary.items",
          Locales.placeholders("count", String.valueOf(itemCount))));
    }
    if (rewards != null) {
      if (rewards.xp() > 0) {
        player.sendMessage(Locales.component(player, "messages.kits.summary.xp",
            Locales.placeholders("amount", String.valueOf(rewards.xp()))));
      }
      if (rewards.tokens() > 0) {
        player.sendMessage(Locales.component(player, "messages.kits.summary.tokens",
            Locales.placeholders("amount", String.valueOf(rewards.tokens()))));
      }
      if (rewards.compressed() > 0) {
        player.sendMessage(Locales.component(player, "messages.kits.summary.compressed",
            Locales.placeholders("amount", String.valueOf(rewards.compressed()))));
      }
      if (rewards.pallet() > 0) {
        player.sendMessage(Locales.component(player, "messages.kits.summary.pallets",
            Locales.placeholders("amount", String.valueOf(rewards.pallet()))));
      }
    }
    logger.info("[Kits] " + player.getName() + " claimed kit " + kit.id()
        + " items=" + itemCount
        + " xp=" + (rewards == null ? 0 : rewards.xp())
        + " tokens=" + (rewards == null ? 0 : rewards.tokens())
        + " compressed=" + (rewards == null ? 0 : rewards.compressed())
        + " pallets=" + (rewards == null ? 0 : rewards.pallet()));
  }

  private void giveTokenStacks(Player player, ItemStack template, int amount) {
    if (player == null || template == null || amount <= 0) {
      return;
    }
    int remaining = amount;
    int maxStack = Math.max(1, template.getMaxStackSize());
    while (remaining > 0) {
      int stackAmount = Math.min(maxStack, remaining);
      ItemStack stack = template.clone();
      stack.setAmount(stackAmount);
      giveItemOrDrop(player, stack);
      remaining -= stackAmount;
    }
  }

  private void giveItemOrDrop(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) {
      return;
    }
    var leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItem(player.getLocation(), stack);
      }
    }
  }

  private static String formatDuration(long millis) {
    long seconds = Math.max(0L, millis / 1000L);
    long hours = seconds / 3600L;
    long minutes = (seconds % 3600L) / 60L;
    long secs = seconds % 60L;
    if (hours > 0) {
      return hours + "h " + minutes + "m";
    }
    if (minutes > 0) {
      return minutes + "m " + secs + "s";
    }
    return secs + "s";
  }
}
