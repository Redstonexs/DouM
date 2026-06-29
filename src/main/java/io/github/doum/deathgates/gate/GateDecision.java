package io.github.doum.deathgates.gate;

import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record GateDecision(
        boolean allowed,
        int requiredDeaths,
        int actualDeaths,
        OperationType operation,
        Optional<TargetKey> matchedTargetKey,
        Map<String, String> denialContext) {
    public GateDecision {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(matchedTargetKey, "matchedTargetKey");
        Objects.requireNonNull(denialContext, "denialContext");
        if (requiredDeaths < 0) {
            throw new IllegalArgumentException("requiredDeaths cannot be negative: " + requiredDeaths);
        }
        if (actualDeaths < 0) {
            throw new IllegalArgumentException("actualDeaths cannot be negative: " + actualDeaths);
        }
        denialContext = Map.copyOf(denialContext);
    }
}
