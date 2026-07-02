package io.github.doum.deathgates.config;

final class HardshipRulesConfigLoader {
    private static final String ROOT = "hardship-rules";

    private HardshipRulesConfigLoader() {}

    static HardshipRulesConfig load(DeathGatesConfigLoader.Section section) {
        if (section == null) {
            return HardshipRulesConfig.disabled();
        }

        return new HardshipRulesConfig(
                loadCrafting(optionalSection(section, "crafting")),
                loadFurnace(optionalSection(section, "furnace")),
                loadStorage(optionalSection(section, "storage")),
                loadSleep(optionalSection(section, "sleep")),
                loadFishing(optionalSection(section, "fishing")),
                loadFall(optionalSection(section, "fall")),
                loadHealth(optionalSection(section, "health")),
                loadBiomes(optionalSection(section, "biomes")),
                loadBlockRetaliation(optionalSection(section, "block-retaliation")));
    }

    private static DeathGatesConfigLoader.Section optionalSection(
            DeathGatesConfigLoader.Section section,
            String child) {
        DeathGatesConfigLoader.Section childSection = section.section(child);
        if (childSection == null && section.isSet(child)) {
            throw new DeathGatesConfigException("Expected config section at " + ROOT + "." + child);
        }
        return childSection;
    }

    private static HardshipRulesConfig.Crafting loadCrafting(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Crafting defaults = HardshipRulesConfig.disabled().crafting();
        int minDurability = readPercent(
                section,
                "crafting",
                "tool-durability-min-percent",
                defaults.toolDurabilityMinPercent());
        int maxDurability = readPercent(
                section,
                "crafting",
                "tool-durability-max-percent",
                defaults.toolDurabilityMaxPercent());
        if (minDurability > maxDurability) {
            throw new DeathGatesConfigException(
                    path("crafting", "tool-durability-min-percent")
                            + " cannot exceed "
                            + path("crafting", "tool-durability-max-percent"));
        }
        return new HardshipRulesConfig.Crafting(
                readBoolean(section, "crafting", "enabled", defaults.enabled()),
                readPercent(section, "crafting", "fail-chance-percent", defaults.failChancePercent()),
                minDurability,
                maxDurability);
    }

    private static HardshipRulesConfig.Furnace loadFurnace(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Furnace defaults = HardshipRulesConfig.disabled().furnace();
        return new HardshipRulesConfig.Furnace(
                readBoolean(section, "furnace", "enabled", defaults.enabled()),
                readPercent(section, "furnace", "jam-chance-percent", defaults.jamChancePercent()),
                readPositiveInt(section, "furnace", "jam-cook-time-percent", defaults.jamCookTimePercent()),
                readPercent(section, "furnace", "burnt-food-chance-percent", defaults.burntFoodChancePercent()),
                readPositiveInt(section, "furnace", "fuel-burn-time-percent", defaults.fuelBurnTimePercent()));
    }

    private static HardshipRulesConfig.Storage loadStorage(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Storage defaults = HardshipRulesConfig.disabled().storage();
        return new HardshipRulesConfig.Storage(
                readBoolean(section, "storage", "prevent-double-chests", defaults.preventDoubleChests()));
    }

    private static HardshipRulesConfig.Sleep loadSleep(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Sleep defaults = HardshipRulesConfig.disabled().sleep();
        return new HardshipRulesConfig.Sleep(
                readBoolean(section, "sleep", "prevent-night-skip", defaults.preventNightSkip()),
                readBoolean(
                        section,
                        "sleep",
                        "require-full-sleep-respawn",
                        defaults.requireFullSleepRespawn()),
                readPositiveInt(section, "sleep", "full-sleep-ticks", defaults.fullSleepTicks()));
    }

    private static HardshipRulesConfig.Fishing loadFishing(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Fishing defaults = HardshipRulesConfig.disabled().fishing();
        return new HardshipRulesConfig.Fishing(
                readBoolean(section, "fishing", "enabled", defaults.enabled()),
                readPositiveInt(section, "fishing", "wait-time-percent", defaults.waitTimePercent()));
    }

    private static HardshipRulesConfig.Fall loadFall(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Fall defaults = HardshipRulesConfig.disabled().fall();
        return new HardshipRulesConfig.Fall(
                readBoolean(section, "fall", "enabled", defaults.enabled()),
                readPositiveInt(
                        section,
                        "fall",
                        "minimum-fall-distance-blocks",
                        defaults.minimumFallDistanceBlocks()),
                readPositiveInt(section, "fall", "minimum-fall-damage", defaults.minimumFallDamage()));
    }

    private static HardshipRulesConfig.Health loadHealth(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Health defaults = HardshipRulesConfig.disabled().health();
        return new HardshipRulesConfig.Health(
                readBoolean(section, "health", "enabled", defaults.enabled()),
                readPositiveInt(section, "health", "threshold-health", defaults.thresholdHealth()),
                readPositiveInt(section, "health", "effect-duration-ticks", defaults.effectDurationTicks()),
                readNonNegativeInt(section, "health", "amplifier", defaults.amplifier()));
    }

    private static HardshipRulesConfig.Biomes loadBiomes(DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.Biomes defaults = HardshipRulesConfig.disabled().biomes();
        return new HardshipRulesConfig.Biomes(
                readBoolean(section, "biomes", "enabled", defaults.enabled()),
                readPositiveInt(section, "biomes", "effect-duration-ticks", defaults.effectDurationTicks()),
                readNonNegativeInt(section, "biomes", "amplifier", defaults.amplifier()));
    }

    private static HardshipRulesConfig.BlockRetaliation loadBlockRetaliation(
            DeathGatesConfigLoader.Section section) {
        HardshipRulesConfig.BlockRetaliation defaults = HardshipRulesConfig.disabled().blockRetaliation();
        return new HardshipRulesConfig.BlockRetaliation(
                readBoolean(section, "block-retaliation", "enabled", defaults.enabled()),
                readPercent(section, "block-retaliation", "chance-percent", defaults.chancePercent()),
                readPositiveInt(section, "block-retaliation", "cooldown-ticks", defaults.cooldownTicks()));
    }

    private static boolean readBoolean(
            DeathGatesConfigLoader.Section section,
            String parent,
            String child,
            boolean defaultValue) {
        if (section == null || !section.isSet(child)) {
            return defaultValue;
        }
        String path = path(parent, child);
        if (!section.isBoolean(child)) {
            throw new DeathGatesConfigException("Expected boolean value at " + path);
        }
        return section.getBoolean(child, defaultValue);
    }

    private static int readPercent(
            DeathGatesConfigLoader.Section section,
            String parent,
            String child,
            int defaultValue) {
        int value = readInt(section, parent, child, defaultValue);
        if (value < 0 || value > 100) {
            throw new DeathGatesConfigException(path(parent, child) + " must be in range 0..100: " + value);
        }
        return value;
    }

    private static int readPositiveInt(
            DeathGatesConfigLoader.Section section,
            String parent,
            String child,
            int defaultValue) {
        int value = readInt(section, parent, child, defaultValue);
        if (value <= 0) {
            throw new DeathGatesConfigException(path(parent, child) + " must be positive: " + value);
        }
        return value;
    }

    private static int readNonNegativeInt(
            DeathGatesConfigLoader.Section section,
            String parent,
            String child,
            int defaultValue) {
        int value = readInt(section, parent, child, defaultValue);
        if (value < 0) {
            throw new DeathGatesConfigException(path(parent, child) + " cannot be negative: " + value);
        }
        return value;
    }

    private static int readInt(
            DeathGatesConfigLoader.Section section,
            String parent,
            String child,
            int defaultValue) {
        if (section == null || !section.isSet(child)) {
            return defaultValue;
        }
        String path = path(parent, child);
        if (!section.isInt(child)) {
            throw new DeathGatesConfigException("Expected integer value at " + path);
        }
        return section.getInt(child);
    }

    private static String path(String parent, String child) {
        return ROOT + "." + parent + "." + child;
    }
}
