package io.github.doum.deathgates.config;

import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import io.github.doum.deathgates.model.TargetKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public record OperationGateConfig(
        OperationType operation,
        boolean enabled,
        int defaultRequiredDeaths,
        String bypassPermission,
        String denyMessage,
        Map<TargetKey, Integer> targets) {
    public OperationGateConfig {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(bypassPermission, "bypassPermission");
        Objects.requireNonNull(denyMessage, "denyMessage");
        Objects.requireNonNull(targets, "targets");
        requireNonNegative(defaultRequiredDeaths, operation.id() + ".default-required-deaths");

        Map<TargetKey, Integer> copiedTargets = new LinkedHashMap<>();
        for (Map.Entry<TargetKey, Integer> entry : targets.entrySet()) {
            TargetKey key = Objects.requireNonNull(entry.getKey(), "target key");
            Integer value = Objects.requireNonNull(entry.getValue(), "target threshold");
            requireNonNegative(value, operation.id() + ".targets." + key.asConfigKey());
            copiedTargets.put(key, value);
        }
        targets = Collections.unmodifiableMap(copiedTargets);
    }

    public int requiredDeathsFor(TargetKey targetKey) {
        return targetRequiredDeaths(targetKey).orElse(defaultRequiredDeaths);
    }

    public OptionalInt targetRequiredDeaths(TargetKey targetKey) {
        Integer requiredDeaths = targets.get(targetKey);
        return requiredDeaths == null ? OptionalInt.empty() : OptionalInt.of(requiredDeaths);
    }

    public boolean supportsTarget(TargetKey targetKey) {
        return switch (operation) {
            case BLOCK_BREAK, BLOCK_PLACE -> targetKey.kind() == TargetKind.MATERIAL;
            case CRAFT_ITEM -> targetKey.kind() == TargetKind.RESULT || targetKey.kind() == TargetKind.RECIPE;
        };
    }

    private static void requireNonNegative(int value, String path) {
        if (value < 0) {
            throw new IllegalArgumentException(path + " cannot be negative: " + value);
        }
    }
}
