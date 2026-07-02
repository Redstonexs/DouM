package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.HardshipRulesConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class HardshipRulesEngine {
    private final Supplier<DeathGatesConfig> configSupplier;
    private final IntSupplier randomPercent;
    private final LongSupplier currentTimeMillis;
    private final Map<UUID, Long> retaliationCooldowns = new ConcurrentHashMap<>();

    HardshipRulesEngine(Supplier<DeathGatesConfig> configSupplier, IntSupplier randomPercent) {
        this(configSupplier, randomPercent, System::currentTimeMillis);
    }

    HardshipRulesEngine(
            Supplier<DeathGatesConfig> configSupplier,
            IntSupplier randomPercent,
            LongSupplier currentTimeMillis) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.randomPercent = Objects.requireNonNull(randomPercent, "randomPercent");
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    boolean shouldCancelCraft() {
        HardshipRulesConfig.Crafting crafting = currentRules().crafting();
        return crafting.enabled() && roll(crafting.failChancePercent());
    }

    int craftedToolDamage(Material material) {
        HardshipRulesConfig.Crafting crafting = currentRules().crafting();
        if (!crafting.enabled()) {
            return 0;
        }
        return craftedToolDamage(material.getMaxDurability(), material.getMaxStackSize());
    }

    int craftedToolDamage(int maxDurability, int maxStackSize) {
        HardshipRulesConfig.Crafting crafting = currentRules().crafting();
        if (!crafting.enabled() || maxDurability <= 0 || maxStackSize != 1) {
            return 0;
        }
        int remainingPercent = boundedRandom(crafting.toolDurabilityMinPercent(), crafting.toolDurabilityMaxPercent());
        int remainingDurability = Math.max(1, maxDurability * remainingPercent / 100);
        return Math.max(0, maxDurability - remainingDurability);
    }

    int adjustedCookTime(int originalTicks) {
        HardshipRulesConfig.Furnace furnace = currentRules().furnace();
        if (!furnace.enabled() || !roll(furnace.jamChancePercent())) {
            return originalTicks;
        }
        return multiplyPercent(originalTicks, furnace.jamCookTimePercent());
    }

    int adjustedBurnTime(int originalTicks) {
        HardshipRulesConfig.Furnace furnace = currentRules().furnace();
        return furnace.enabled() ? multiplyPercent(originalTicks, furnace.fuelBurnTimePercent()) : originalTicks;
    }

    boolean shouldBurnSmeltedFood(Material result) {
        HardshipRulesConfig.Furnace furnace = currentRules().furnace();
        return furnace.enabled() && isFoodResult(result) && roll(furnace.burntFoodChancePercent());
    }

    boolean shouldCancelChestPlacement(Material placed, Material neighbor) {
        return currentRules().storage().preventDoubleChests()
                && (placed == Material.CHEST || placed == Material.TRAPPED_CHEST)
                && placed == neighbor;
    }

    boolean preventDoubleChests() {
        return currentRules().storage().preventDoubleChests();
    }

    boolean shouldCancelDeepSleep() {
        return currentRules().sleep().preventNightSkip();
    }

    boolean requireFullSleepRespawn() {
        return currentRules().sleep().requireFullSleepRespawn();
    }

    boolean shouldRestoreRespawnBeforeFullSleep(boolean fullSleepObserved, int sleepTicks) {
        HardshipRulesConfig.Sleep sleep = currentRules().sleep();
        return sleep.requireFullSleepRespawn() && !fullSleepObserved && sleepTicks < sleep.fullSleepTicks();
    }

    FishingWait adjustedFishingWait(int minTicks, int maxTicks) {
        HardshipRulesConfig.Fishing fishing = currentRules().fishing();
        if (!fishing.enabled()) {
            return new FishingWait(minTicks, maxTicks);
        }
        return new FishingWait(
                multiplyPercent(minTicks, fishing.waitTimePercent()),
                multiplyPercent(maxTicks, fishing.waitTimePercent()));
    }

    double adjustedFallDamage(double originalDamage, float fallDistance) {
        HardshipRulesConfig.Fall fall = currentRules().fall();
        if (!fall.enabled() || fallDistance < fall.minimumFallDistanceBlocks()) {
            return originalDamage;
        }
        return Math.max(originalDamage, fall.minimumFallDamage());
    }

    double manualLandingDamage(double fallDistance) {
        HardshipRulesConfig.Fall fall = currentRules().fall();
        if (!fall.enabled() || fallDistance < fall.minimumFallDistanceBlocks() || fallDistance >= 4.0) {
            return 0.0;
        }
        return fall.minimumFallDamage();
    }

    List<PotionEffect> lowHealthEffects(double health) {
        HardshipRulesConfig.Health healthRules = currentRules().health();
        return lowHealthEffectKeys(health).stream()
                .map(type -> effect(effectType(type), healthRules.effectDurationTicks(), healthRules.amplifier()))
                .toList();
    }

    List<String> lowHealthEffectKeys(double health) {
        HardshipRulesConfig.Health healthRules = currentRules().health();
        if (!healthRules.enabled() || health > healthRules.thresholdHealth()) {
            return List.of();
        }
        return List.of("minecraft:slowness", "minecraft:blindness");
    }

    List<PotionEffect> biomeEffects(String biomeKey) {
        HardshipRulesConfig.Biomes biomeRules = currentRules().biomes();
        return biomeEffectKeys(biomeKey).stream()
                .map(type -> effect(effectType(type), biomeRules.effectDurationTicks(), biomeRules.amplifier()))
                .toList();
    }

    List<String> biomeEffectKeys(String biomeKey) {
        HardshipRulesConfig.Biomes biomeRules = currentRules().biomes();
        if (!biomeRules.enabled()) {
            return List.of();
        }
        List<String> effects = new ArrayList<>(1);
        if (isSwamp(biomeKey)) {
            effects.add("minecraft:poison");
        } else if (isHighAltitude(biomeKey)) {
            effects.add("minecraft:weakness");
        }
        return List.copyOf(effects);
    }

    boolean shouldSpawnRetaliation(UUID playerId) {
        HardshipRulesConfig.BlockRetaliation blockRetaliation = currentRules().blockRetaliation();
        if (!blockRetaliation.enabled()) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        Long nextAllowedAt = retaliationCooldowns.get(playerId);
        if (nextAllowedAt != null && nextAllowedAt > now) {
            return false;
        }
        if (!roll(blockRetaliation.chancePercent())) {
            return false;
        }
        retaliationCooldowns.put(playerId, now + blockRetaliation.cooldownTicks() * 50L);
        return true;
    }

    void clearRetaliationCooldown(UUID playerId) {
        retaliationCooldowns.remove(playerId);
    }

    private boolean roll(int chancePercent) {
        return chancePercent > 0 && randomPercent.getAsInt() < chancePercent;
    }

    private int boundedRandom(int minInclusive, int maxInclusive) {
        if (minInclusive == maxInclusive) {
            return minInclusive;
        }
        return minInclusive + Math.floorMod(randomPercent.getAsInt(), maxInclusive - minInclusive + 1);
    }

    private HardshipRulesConfig currentRules() {
        return configSupplier.get().hardshipRules();
    }

    private static boolean isFoodResult(Material material) {
        return switch (material) {
            case BAKED_POTATO,
                    COOKED_BEEF,
                    COOKED_CHICKEN,
                    COOKED_COD,
                    COOKED_MUTTON,
                    COOKED_PORKCHOP,
                    COOKED_RABBIT,
                    COOKED_SALMON,
                    DRIED_KELP -> true;
            default -> false;
        };
    }

    private static int multiplyPercent(int value, int percent) {
        return Math.max(1, value * percent / 100);
    }

    private static PotionEffect effect(PotionEffectType type, int durationTicks, int amplifier) {
        return new PotionEffect(type, durationTicks, amplifier, true, true, true);
    }

    private static PotionEffectType effectType(String key) {
        return switch (key) {
            case "minecraft:slowness" -> PotionEffectType.SLOWNESS;
            case "minecraft:blindness" -> PotionEffectType.BLINDNESS;
            case "minecraft:poison" -> PotionEffectType.POISON;
            case "minecraft:weakness" -> PotionEffectType.WEAKNESS;
            default -> throw new IllegalArgumentException("Unknown hardship effect: " + key);
        };
    }

    private static boolean isSwamp(String biomeKey) {
        return "minecraft:swamp".equals(biomeKey) || "minecraft:mangrove_swamp".equals(biomeKey);
    }

    private static boolean isHighAltitude(String biomeKey) {
        return switch (biomeKey) {
            case "minecraft:meadow",
                    "minecraft:grove",
                    "minecraft:cherry_grove",
                    "minecraft:jagged_peaks",
                    "minecraft:frozen_peaks",
                    "minecraft:stony_peaks" -> true;
            default -> false;
        };
    }
}

record FishingWait(int minTicks, int maxTicks) {}
