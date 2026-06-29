package io.github.doum.deathgates.gate;

import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GateRequest(
        OperationType operation,
        UUID playerId,
        int playerDeaths,
        List<TargetKey> targetKeys,
        boolean hasBypassPermission) {
    public GateRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetKeys, "targetKeys");
        if (playerDeaths < 0) {
            throw new IllegalArgumentException("playerDeaths cannot be negative: " + playerDeaths);
        }
        targetKeys = List.copyOf(targetKeys);
    }
}
