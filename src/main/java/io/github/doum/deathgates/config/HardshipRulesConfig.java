package io.github.doum.deathgates.config;

import java.util.Objects;

public record HardshipRulesConfig(
        Crafting crafting,
        Furnace furnace,
        Storage storage,
        Sleep sleep,
        Fishing fishing,
        Fall fall,
        Health health,
        Biomes biomes,
        BlockRetaliation blockRetaliation) {
    private static final HardshipRulesConfig DISABLED = new HardshipRulesConfig(
            new Crafting(false, 0, 70, 95),
            new Furnace(false, 0, 300, 0, 75),
            new Storage(false),
            new Sleep(false, false, 100),
            new Fishing(false, 150),
            new Fall(false, 3, 1),
            new Health(false, 6, 100, 0),
            new Biomes(false, 100, 0),
            new BlockRetaliation(false, 0, 20));

    public HardshipRulesConfig {
        Objects.requireNonNull(crafting, "crafting");
        Objects.requireNonNull(furnace, "furnace");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(sleep, "sleep");
        Objects.requireNonNull(fishing, "fishing");
        Objects.requireNonNull(fall, "fall");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(biomes, "biomes");
        Objects.requireNonNull(blockRetaliation, "blockRetaliation");
    }

    public static HardshipRulesConfig disabled() {
        return DISABLED;
    }

    public record Crafting(
            boolean enabled,
            int failChancePercent,
            int toolDurabilityMinPercent,
            int toolDurabilityMaxPercent) {
        public Crafting {
            requirePercent(failChancePercent, "crafting.fail-chance-percent");
            requirePercent(toolDurabilityMinPercent, "crafting.tool-durability-min-percent");
            requirePercent(toolDurabilityMaxPercent, "crafting.tool-durability-max-percent");
            if (toolDurabilityMinPercent > toolDurabilityMaxPercent) {
                throw new IllegalArgumentException(
                        "crafting.tool-durability-min-percent cannot exceed crafting.tool-durability-max-percent");
            }
        }
    }

    public record Furnace(
            boolean enabled,
            int jamChancePercent,
            int jamCookTimePercent,
            int burntFoodChancePercent,
            int fuelBurnTimePercent) {
        public Furnace {
            requirePercent(jamChancePercent, "furnace.jam-chance-percent");
            requirePositive(jamCookTimePercent, "furnace.jam-cook-time-percent");
            requirePercent(burntFoodChancePercent, "furnace.burnt-food-chance-percent");
            requirePositive(fuelBurnTimePercent, "furnace.fuel-burn-time-percent");
        }
    }

    public record Storage(boolean preventDoubleChests) {}

    public record Sleep(boolean preventNightSkip, boolean requireFullSleepRespawn, int fullSleepTicks) {
        public Sleep {
            requirePositive(fullSleepTicks, "sleep.full-sleep-ticks");
        }
    }

    public record Fishing(boolean enabled, int waitTimePercent) {
        public Fishing {
            requirePositive(waitTimePercent, "fishing.wait-time-percent");
        }
    }

    public record Fall(boolean enabled, int minimumFallDistanceBlocks, int minimumFallDamage) {
        public Fall {
            requirePositive(minimumFallDistanceBlocks, "fall.minimum-fall-distance-blocks");
            requirePositive(minimumFallDamage, "fall.minimum-fall-damage");
        }
    }

    public record Health(boolean enabled, int thresholdHealth, int effectDurationTicks, int amplifier) {
        public Health {
            requirePositive(thresholdHealth, "health.threshold-health");
            requirePositive(effectDurationTicks, "health.effect-duration-ticks");
            requireNonNegative(amplifier, "health.amplifier");
        }
    }

    public record Biomes(boolean enabled, int effectDurationTicks, int amplifier) {
        public Biomes {
            requirePositive(effectDurationTicks, "biomes.effect-duration-ticks");
            requireNonNegative(amplifier, "biomes.amplifier");
        }
    }

    public record BlockRetaliation(boolean enabled, int chancePercent, int cooldownTicks) {
        public BlockRetaliation {
            requirePercent(chancePercent, "block-retaliation.chance-percent");
            requirePositive(cooldownTicks, "block-retaliation.cooldown-ticks");
        }
    }

    private static void requirePercent(int value, String path) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(path + " must be in range 0..100: " + value);
        }
    }

    private static void requirePositive(int value, String path) {
        if (value <= 0) {
            throw new IllegalArgumentException(path + " must be positive: " + value);
        }
    }

    private static void requireNonNegative(int value, String path) {
        if (value < 0) {
            throw new IllegalArgumentException(path + " cannot be negative: " + value);
        }
    }
}
