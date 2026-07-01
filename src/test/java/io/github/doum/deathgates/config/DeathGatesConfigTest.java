package io.github.doum.deathgates.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DeathGatesConfigTest {
    @Test
    void loadsIndependentOperationDefaultsAndCraftTargetOverride() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.default-required-deaths", 1);
        config.set("operations.block-place.default-required-deaths", 2);
        config.set("operations.craft-item.default-required-deaths", 9);
        config.set("operations.craft-item.targets.result:minecraft:oak_planks", 3);

        DeathGatesConfig loaded = DeathGatesConfigLoader.load(config);

        assertEquals(1, loaded.operation(OperationType.BLOCK_BREAK).defaultRequiredDeaths());
        assertEquals(2, loaded.operation(OperationType.BLOCK_PLACE).defaultRequiredDeaths());
        assertEquals(9, loaded.operation(OperationType.CRAFT_ITEM).defaultRequiredDeaths());
        assertEquals(
                3,
                loaded.operation(OperationType.CRAFT_ITEM)
                        .requiredDeathsFor(TargetKey.parse("result:minecraft:oak_planks")));
    }

    @Test
    void loadsFromBukkitYamlConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        addOperation(config, "block-break");
        addOperation(config, "block-place");
        addOperation(config, "craft-item");
        config.set("operations.block-break.default-required-deaths", 1);
        config.set("operations.block-place.default-required-deaths", 2);
        config.set("operations.craft-item.default-required-deaths", 9);
        config.set("operations.craft-item.targets.result:minecraft:oak_planks", 3);

        DeathGatesConfig loaded = DeathGatesConfigLoader.load(config);

        assertEquals(1, loaded.operation(OperationType.BLOCK_BREAK).defaultRequiredDeaths());
        assertEquals(2, loaded.operation(OperationType.BLOCK_PLACE).defaultRequiredDeaths());
        assertEquals(
                3,
                loaded.operation(OperationType.CRAFT_ITEM)
                        .requiredDeathsFor(TargetKey.parse("result:minecraft:oak_planks")));
    }

    @Test
    void malformedTargetKeyWithoutKindPrefixThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.craft-item.targets.minecraft:oak_planks", 3);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("minecraft:oak_planks"));
    }

    @Test
    void negativeDefaultRequiredDeathsThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.default-required-deaths", -1);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("operations.block-break.default-required-deaths"));
        assertTrue(error.getMessage().contains("negative"));
    }

    @Test
    void negativeTargetRequiredDeathsThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.targets.material:minecraft:diamond_ore", -1);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("operations.block-break.targets.material:minecraft:diamond_ore"));
        assertTrue(error.getMessage().contains("negative"));
    }

    @Test
    void wrongTypeEnabledThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.enabled", "yes");

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected boolean value"));
        assertTrue(error.getMessage().contains("operations.block-break.enabled"));
    }

    @Test
    void wrongTypeBypassPermissionThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.bypass-permission", 123);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected string value"));
        assertTrue(error.getMessage().contains("operations.block-break.bypass-permission"));
    }

    @Test
    void wrongTypeDenyMessageThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.deny-message", 123);

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected string value"));
        assertTrue(error.getMessage().contains("operations.block-break.deny-message"));
    }

    @Test
    void wrongTypeTargetsThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.targets", java.util.List.of("material:minecraft:diamond_ore"));

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected config section"));
        assertTrue(error.getMessage().contains("operations.block-break.targets"));
    }

    @Test
    void wrongTypeTargetRequiredDeathsThrowsConfigException() {
        FixtureSection config = fixtureConfig();
        config.set("operations.block-break.targets.material:minecraft:diamond_ore", "three");

        DeathGatesConfigException error =
                assertThrows(DeathGatesConfigException.class, () -> DeathGatesConfigLoader.load(config));

        assertTrue(error.getMessage().contains("Expected integer value"));
        assertTrue(error.getMessage().contains("operations.block-break.targets.material:minecraft:diamond_ore"));
    }

    @Test
    void defaultsMessagePrefixWhenUnset() {
        DeathGatesConfig loaded = DeathGatesConfigLoader.load(fixtureConfig());

        assertEquals(DeathGatesConfig.DEFAULT_MESSAGE_PREFIX, loaded.messagePrefix());
    }

    @Test
    void readsConfiguredMessagePrefix() {
        FixtureSection config = fixtureConfig();
        config.set("message-prefix", "<gold>DouM</gold> ");

        DeathGatesConfig loaded = DeathGatesConfigLoader.load(config);

        assertEquals("<gold>DouM</gold> ", loaded.messagePrefix());
    }

    private static FixtureSection fixtureConfig() {
        FixtureSection config = new FixtureSection();
        addOperation(config, "block-break");
        addOperation(config, "block-place");
        addOperation(config, "craft-item");
        return config;
    }

    private static void addOperation(FixtureSection config, String operationId) {
        String path = "operations." + operationId;
        config.set(path + ".enabled", true);
        config.set(path + ".default-required-deaths", 0);
        config.set(path + ".bypass-permission", "doum.deathnum.bypass." + operationId);
        config.set(path + ".deny-message", "Denied {operation} {target} {required} {actual}");
    }

    private static void addOperation(YamlConfiguration config, String operationId) {
        String path = "operations." + operationId;
        config.set(path + ".enabled", true);
        config.set(path + ".default-required-deaths", 0);
        config.set(path + ".bypass-permission", "doum.deathnum.bypass." + operationId);
        config.set(path + ".deny-message", "Denied {operation} {target} {required} {actual}");
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
