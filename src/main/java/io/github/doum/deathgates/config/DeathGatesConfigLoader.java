package io.github.doum.deathgates.config;

import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import io.github.doum.deathgates.model.TargetKind;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public final class DeathGatesConfigLoader {
    private static final String OPERATIONS_PATH = "operations";
    private static final String HARDSHIP_RULES_PATH = "hardship-rules";

    private DeathGatesConfigLoader() {}

    interface Section {
        Section section(String path);

        boolean isSet(String path);

        boolean isInt(String path);

        boolean isBoolean(String path);

        boolean isString(String path);

        int getInt(String path);

        boolean getBoolean(String path, boolean defaultValue);

        String getString(String path, String defaultValue);

        Set<String> keys();
    }

    static DeathGatesConfig load(Section root) {
        if (root == null) {
            throw new DeathGatesConfigException("Config root cannot be null");
        }

        Section operationsSection = root.section(OPERATIONS_PATH);
        if (operationsSection == null) {
            throw new DeathGatesConfigException("Missing config section: " + OPERATIONS_PATH);
        }

        rejectUnknownOperations(operationsSection);

        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            operations.put(operation, loadOperation(operationsSection, operation));
        }

        String messagePrefix = readOptionalString(
                root, "message-prefix", "message-prefix", DeathGatesConfig.DEFAULT_MESSAGE_PREFIX);
        Section hardshipRulesSection = root.section(HARDSHIP_RULES_PATH);
        if (hardshipRulesSection == null && root.isSet(HARDSHIP_RULES_PATH)) {
            throw new DeathGatesConfigException("Expected config section at " + HARDSHIP_RULES_PATH);
        }
        HardshipRulesConfig hardshipRules = HardshipRulesConfigLoader.load(hardshipRulesSection);
        return new DeathGatesConfig(operations, messagePrefix, hardshipRules);
    }

    public static DeathGatesConfig load(ConfigurationSection root) {
        return load(root == null ? null : new BukkitSection(root));
    }

    private static void rejectUnknownOperations(Section operationsSection) {
        for (String operationId : operationsSection.keys()) {
            try {
                OperationType.fromId(operationId);
            } catch (IllegalArgumentException error) {
                throw new DeathGatesConfigException(
                        "Unknown operation id at operations." + operationId + ": " + operationId, error);
            }
        }
    }

    private static OperationGateConfig loadOperation(
            Section operationsSection, OperationType operation) {
        String operationPath = OPERATIONS_PATH + "." + operation.id();
        Section section = operationsSection.section(operation.id());
        if (section == null) {
            if (operationsSection.isSet(operation.id())) {
                throw new DeathGatesConfigException("Expected config section at " + operationPath);
            }
            throw new DeathGatesConfigException("Missing config section: " + operationPath);
        }

        boolean enabled = readOptionalBoolean(section, "enabled", operationPath + ".enabled", true);
        int defaultRequiredDeaths = readOptionalNonNegativeInt(
                section, "default-required-deaths", operationPath + ".default-required-deaths", 0);
        String bypassPermission = readOptionalString(
                section,
                "bypass-permission",
                operationPath + ".bypass-permission",
                "doum.deathnum.bypass." + operation.id());
        String denyMessage = readOptionalString(section, "deny-message", operationPath + ".deny-message", "");
        Map<TargetKey, Integer> targets = loadTargets(section, operation, operationPath);

        return new OperationGateConfig(
                operation,
                enabled,
                defaultRequiredDeaths,
                bypassPermission,
                denyMessage,
                targets);
    }

    private static Map<TargetKey, Integer> loadTargets(
            Section operationSection, OperationType operation, String operationPath) {
        Section targetSection = operationSection.section("targets");
        if (targetSection == null) {
            if (operationSection.isSet("targets")) {
                throw new DeathGatesConfigException("Expected config section at " + operationPath + ".targets");
            }
            return Map.of();
        }

        Map<TargetKey, Integer> targets = new LinkedHashMap<>();
        for (String rawKey : targetSection.keys()) {
            String targetPath = operationPath + ".targets." + rawKey;
            TargetKey targetKey = parseTargetKey(rawKey, targetPath);
            if (!isTargetKindSupported(operation, targetKey)) {
                throw new DeathGatesConfigException(
                        "Unsupported target kind at " + targetPath + " for operation " + operation.id());
            }
            targets.put(targetKey, readRequiredNonNegativeInt(targetSection, rawKey, targetPath));
        }
        return targets;
    }

    private static TargetKey parseTargetKey(String rawKey, String targetPath) {
        try {
            return TargetKey.parse(rawKey);
        } catch (IllegalArgumentException error) {
            throw new DeathGatesConfigException(
                    "Malformed target key at " + targetPath + ": " + error.getMessage(), error);
        }
    }

    private static boolean isTargetKindSupported(OperationType operation, TargetKey targetKey) {
        return switch (operation) {
            case BLOCK_BREAK, BLOCK_PLACE -> targetKey.kind() == TargetKind.MATERIAL;
            case CRAFT_ITEM -> targetKey.kind() == TargetKind.RESULT || targetKey.kind() == TargetKind.RECIPE;
        };
    }

    private static int readOptionalNonNegativeInt(
            Section section, String childPath, String fullPath, int defaultValue) {
        if (!section.isSet(childPath)) {
            return defaultValue;
        }
        return readRequiredNonNegativeInt(section, childPath, fullPath);
    }

    private static boolean readOptionalBoolean(
            Section section, String childPath, String fullPath, boolean defaultValue) {
        if (!section.isSet(childPath)) {
            return defaultValue;
        }
        if (!section.isBoolean(childPath)) {
            throw new DeathGatesConfigException("Expected boolean value at " + fullPath);
        }
        return section.getBoolean(childPath, defaultValue);
    }

    private static String readOptionalString(
            Section section, String childPath, String fullPath, String defaultValue) {
        if (!section.isSet(childPath)) {
            return defaultValue;
        }
        if (!section.isString(childPath)) {
            throw new DeathGatesConfigException("Expected string value at " + fullPath);
        }
        return section.getString(childPath, defaultValue);
    }

    private static int readRequiredNonNegativeInt(
            Section section, String childPath, String fullPath) {
        if (!section.isSet(childPath)) {
            throw new DeathGatesConfigException("Missing integer value at " + fullPath);
        }
        if (!section.isInt(childPath)) {
            throw new DeathGatesConfigException("Expected integer value at " + fullPath);
        }

        int value = section.getInt(childPath);
        if (value < 0) {
            throw new DeathGatesConfigException("Required deaths at " + fullPath + " cannot be negative: " + value);
        }
        return value;
    }

    private record BukkitSection(ConfigurationSection section) implements Section {
        @Override
        public Section section(String path) {
            ConfigurationSection child = section.getConfigurationSection(path);
            return child == null ? null : new BukkitSection(child);
        }

        @Override
        public boolean isSet(String path) {
            return section.isSet(path);
        }

        @Override
        public boolean isInt(String path) {
            return section.isInt(path);
        }

        @Override
        public boolean isBoolean(String path) {
            return section.isBoolean(path);
        }

        @Override
        public boolean isString(String path) {
            return section.isString(path);
        }

        @Override
        public int getInt(String path) {
            return section.getInt(path);
        }

        @Override
        public boolean getBoolean(String path, boolean defaultValue) {
            return section.getBoolean(path, defaultValue);
        }

        @Override
        public String getString(String path, String defaultValue) {
            return section.getString(path, defaultValue);
        }

        @Override
        public Set<String> keys() {
            return section.getKeys(false);
        }
    }
}
