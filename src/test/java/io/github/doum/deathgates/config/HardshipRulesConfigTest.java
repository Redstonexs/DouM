package io.github.doum.deathgates.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HardshipRulesConfigTest {
    @Test
    void defaultsHardshipRulesToDisabledSafeValues() {
        DeathGatesConfig loaded = DeathGatesConfigLoader.load(fixtureConfig());
        HardshipRulesConfig hardship = loaded.hardshipRules();

        assertEquals(HardshipRulesConfig.disabled(), hardship);
        assertEquals(70, hardship.crafting().toolDurabilityMinPercent());
        assertEquals(95, hardship.crafting().toolDurabilityMaxPercent());
        assertEquals(100, hardship.sleep().fullSleepTicks());
        assertEquals(150, hardship.fishing().waitTimePercent());
        assertEquals(20, hardship.blockRetaliation().cooldownTicks());
    }

    @Test
    void loadsHardshipRulesFromFixtureAndBukkitYaml() {
        FixtureSection fixture = fixtureConfig();
        setHardship(fixture::set);
        YamlConfiguration yaml = fixtureYaml();
        setHardship(yaml::set);

        DeathGatesConfig fixtureLoaded = DeathGatesConfigLoader.load(fixture);
        DeathGatesConfig yamlLoaded = DeathGatesConfigLoader.load(yaml);

        assertEquals(fixtureLoaded.hardshipRules(), yamlLoaded.hardshipRules());
        assertTrue(fixtureLoaded.hardshipRules().crafting().enabled());
        assertEquals(25, fixtureLoaded.hardshipRules().crafting().failChancePercent());
        assertEquals(300, fixtureLoaded.hardshipRules().furnace().jamCookTimePercent());
        assertEquals(75, fixtureLoaded.hardshipRules().furnace().fuelBurnTimePercent());
        assertTrue(fixtureLoaded.hardshipRules().storage().preventDoubleChests());
        assertTrue(fixtureLoaded.hardshipRules().sleep().preventNightSkip());
        assertTrue(fixtureLoaded.hardshipRules().sleep().requireFullSleepRespawn());
        assertEquals(120, fixtureLoaded.hardshipRules().sleep().fullSleepTicks());
        assertEquals(3, fixtureLoaded.hardshipRules().fall().minimumFallDistanceBlocks());
        assertEquals(6, fixtureLoaded.hardshipRules().health().thresholdHealth());
        assertEquals(40, fixtureLoaded.hardshipRules().blockRetaliation().chancePercent());
        assertEquals(20, fixtureLoaded.hardshipRules().blockRetaliation().cooldownTicks());
    }

    @Test
    void malformedHardshipPercentageThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules.crafting.fail-chance-percent", 101);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("hardship-rules.crafting.fail-chance-percent"));
        assertTrue(error.getMessage().contains("0..100"));
    }

    @Test
    void malformedHardshipRangeThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules.crafting.tool-durability-min-percent", 96);
        config.set("hardship-rules.crafting.tool-durability-max-percent", 95);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("hardship-rules.crafting.tool-durability-min-percent"));
        assertTrue(error.getMessage().contains("cannot exceed"));
    }

    @Test
    void negativeHardshipDurationThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules.health.effect-duration-ticks", -1);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("hardship-rules.health.effect-duration-ticks"));
        assertTrue(error.getMessage().contains("positive"));
    }

    @Test
    void nonPositiveFullSleepTicksThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules.sleep.full-sleep-ticks", 0);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("hardship-rules.sleep.full-sleep-ticks"));
        assertTrue(error.getMessage().contains("positive"));
    }

    @Test
    void scalarHardshipRootThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules", true);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected config section"));
        assertTrue(error.getMessage().contains("hardship-rules"));
    }

    @Test
    void scalarHardshipChildThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules.crafting", true);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected config section"));
        assertTrue(error.getMessage().contains("hardship-rules.crafting"));
    }

    @Test
    void negativeBlockRetaliationCooldownThrowsConfigExceptionWithPath() {
        FixtureSection config = fixtureConfig();
        config.set("hardship-rules.block-retaliation.cooldown-ticks", 0);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("hardship-rules.block-retaliation.cooldown-ticks"));
        assertTrue(error.getMessage().contains("positive"));
    }

    private static FixtureSection fixtureConfig() {
        FixtureSection config = new FixtureSection();
        addOperation(config::set, "block-break");
        addOperation(config::set, "block-place");
        addOperation(config::set, "craft-item");
        return config;
    }

    private static YamlConfiguration fixtureYaml() {
        YamlConfiguration config = new YamlConfiguration();
        addOperation(config::set, "block-break");
        addOperation(config::set, "block-place");
        addOperation(config::set, "craft-item");
        return config;
    }

    private static void addOperation(BiConsumer<String, Object> set, String operationId) {
        String path = "operations." + operationId;
        set.accept(path + ".enabled", true);
        set.accept(path + ".default-required-deaths", 0);
        set.accept(path + ".bypass-permission", "doum.deathnum.bypass." + operationId);
        set.accept(path + ".deny-message", "Denied {operation} {target} {required} {actual}");
    }

    private static void setHardship(BiConsumer<String, Object> set) {
        set.accept("hardship-rules.crafting.enabled", true);
        set.accept("hardship-rules.crafting.fail-chance-percent", 25);
        set.accept("hardship-rules.crafting.tool-durability-min-percent", 70);
        set.accept("hardship-rules.crafting.tool-durability-max-percent", 95);
        set.accept("hardship-rules.furnace.enabled", true);
        set.accept("hardship-rules.furnace.jam-chance-percent", 50);
        set.accept("hardship-rules.furnace.jam-cook-time-percent", 300);
        set.accept("hardship-rules.furnace.burnt-food-chance-percent", 10);
        set.accept("hardship-rules.furnace.fuel-burn-time-percent", 75);
        set.accept("hardship-rules.storage.prevent-double-chests", true);
        set.accept("hardship-rules.sleep.prevent-night-skip", true);
        set.accept("hardship-rules.sleep.require-full-sleep-respawn", true);
        set.accept("hardship-rules.sleep.full-sleep-ticks", 120);
        set.accept("hardship-rules.fishing.enabled", true);
        set.accept("hardship-rules.fishing.wait-time-percent", 175);
        set.accept("hardship-rules.fall.enabled", true);
        set.accept("hardship-rules.fall.minimum-fall-distance-blocks", 3);
        set.accept("hardship-rules.fall.minimum-fall-damage", 2);
        set.accept("hardship-rules.health.enabled", true);
        set.accept("hardship-rules.health.threshold-health", 6);
        set.accept("hardship-rules.health.effect-duration-ticks", 120);
        set.accept("hardship-rules.health.amplifier", 1);
        set.accept("hardship-rules.biomes.enabled", true);
        set.accept("hardship-rules.biomes.effect-duration-ticks", 160);
        set.accept("hardship-rules.biomes.amplifier", 0);
        set.accept("hardship-rules.block-retaliation.enabled", true);
        set.accept("hardship-rules.block-retaliation.chance-percent", 40);
        set.accept("hardship-rules.block-retaliation.cooldown-ticks", 20);
    }

    private static final class FixtureSection implements DeathGatesConfigLoader.Section {
        private final Map<String, Object> values = new LinkedHashMap<>();

        void set(String path, Object value) {
            int separator = path.indexOf('.');
            if (separator < 0) {
                values.put(path, value);
                return;
            }

            String childName = path.substring(0, separator);
            String childPath = path.substring(separator + 1);
            FixtureSection child = (FixtureSection) values.computeIfAbsent(childName, ignored -> new FixtureSection());
            child.set(childPath, value);
        }

        @Override
        public DeathGatesConfigLoader.Section section(String path) {
            Object value = values.get(path);
            return value instanceof DeathGatesConfigLoader.Section section ? section : null;
        }

        @Override
        public boolean isSet(String path) {
            return values.containsKey(path);
        }

        @Override
        public boolean isInt(String path) {
            return values.get(path) instanceof Integer;
        }

        @Override
        public boolean isBoolean(String path) {
            return values.get(path) instanceof Boolean;
        }

        @Override
        public boolean isString(String path) {
            return values.get(path) instanceof String;
        }

        @Override
        public int getInt(String path) {
            return (Integer) values.get(path);
        }

        @Override
        public boolean getBoolean(String path, boolean defaultValue) {
            Object value = values.get(path);
            return value instanceof Boolean bool ? bool : defaultValue;
        }

        @Override
        public String getString(String path, String defaultValue) {
            Object value = values.get(path);
            return value instanceof String string ? string : defaultValue;
        }

        @Override
        public Set<String> keys() {
            return values.keySet();
        }
    }
}
