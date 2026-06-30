package io.github.doum.deathgates.config;

import io.github.doum.deathgates.model.OperationType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record DeathGatesConfig(Map<OperationType, OperationGateConfig> operations, String messagePrefix) {
    /** MiniMessage prefix shown before every DouM message when none is configured; blank disables it. */
    public static final String DEFAULT_MESSAGE_PREFIX = "<dark_gray>[<aqua>DouM</aqua>]</dark_gray> ";

    public DeathGatesConfig {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(messagePrefix, "messagePrefix");
        EnumMap<OperationType, OperationGateConfig> copiedOperations = new EnumMap<>(OperationType.class);
        copiedOperations.putAll(operations);
        for (OperationType type : OperationType.values()) {
            if (!copiedOperations.containsKey(type)) {
                throw new IllegalArgumentException("Missing operation config: " + type.id());
            }
        }
        operations = Collections.unmodifiableMap(copiedOperations);
    }

    /** Builds a config with the {@link #DEFAULT_MESSAGE_PREFIX}; convenient for tests and callers
     * that do not customise the prefix. */
    public DeathGatesConfig(Map<OperationType, OperationGateConfig> operations) {
        this(operations, DEFAULT_MESSAGE_PREFIX);
    }

    public OperationGateConfig operation(OperationType operation) {
        OperationGateConfig config = operations.get(operation);
        if (config == null) {
            throw new IllegalArgumentException("Missing operation config: " + operation.id());
        }
        return config;
    }
}
