package io.github.doum.deathgates.config;

import io.github.doum.deathgates.model.OperationType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record DeathGatesConfig(Map<OperationType, OperationGateConfig> operations) {
    public DeathGatesConfig {
        Objects.requireNonNull(operations, "operations");
        EnumMap<OperationType, OperationGateConfig> copiedOperations = new EnumMap<>(OperationType.class);
        copiedOperations.putAll(operations);
        for (OperationType type : OperationType.values()) {
            if (!copiedOperations.containsKey(type)) {
                throw new IllegalArgumentException("Missing operation config: " + type.id());
            }
        }
        operations = Collections.unmodifiableMap(copiedOperations);
    }

    public OperationGateConfig operation(OperationType operation) {
        OperationGateConfig config = operations.get(operation);
        if (config == null) {
            throw new IllegalArgumentException("Missing operation config: " + operation.id());
        }
        return config;
    }
}
