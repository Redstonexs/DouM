package io.github.doum.deathgates.gate;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.model.TargetKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class GateEvaluator {
    public GateDecision evaluate(DeathGatesConfig config, GateRequest request) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        OperationGateConfig operationConfig = config.operation(request.operation());
        if (!operationConfig.enabled() || request.hasBypassPermission()) {
            return decision(request, 0, Optional.empty(), firstTargetKey(request), true);
        }

        Match match = firstTargetMatch(operationConfig, request);
        int requiredDeaths = match.requiredDeaths().orElse(operationConfig.defaultRequiredDeaths());
        boolean allowed = request.playerDeaths() >= requiredDeaths;
        TargetKey contextTarget = match.targetKey().orElseGet(() -> firstTargetKey(request).orElse(null));

        return decision(request, requiredDeaths, match.targetKey(), Optional.ofNullable(contextTarget), allowed);
    }

    private static Match firstTargetMatch(OperationGateConfig operationConfig, GateRequest request) {
        for (TargetKey targetKey : request.targetKeys()) {
            if (!operationConfig.supportsTarget(targetKey)) {
                continue;
            }
            OptionalInt requiredDeaths = operationConfig.targetRequiredDeaths(targetKey);
            if (requiredDeaths.isPresent()) {
                return new Match(Optional.of(targetKey), requiredDeaths);
            }
        }
        return new Match(Optional.empty(), OptionalInt.empty());
    }

    private static Optional<TargetKey> firstTargetKey(GateRequest request) {
        return request.targetKeys().isEmpty() ? Optional.empty() : Optional.of(request.targetKeys().getFirst());
    }

    private static GateDecision decision(
            GateRequest request,
            int requiredDeaths,
            Optional<TargetKey> matchedTargetKey,
            Optional<TargetKey> contextTarget,
            boolean allowed) {
        return new GateDecision(
                allowed,
                requiredDeaths,
                request.playerDeaths(),
                request.operation(),
                matchedTargetKey,
                denialContext(request, requiredDeaths, contextTarget));
    }

    private static Map<String, String> denialContext(
            GateRequest request, int requiredDeaths, Optional<TargetKey> contextTarget) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("operation", request.operation().id());
        context.put("target", contextTarget.map(TargetKey::asConfigKey).orElse(""));
        context.put("required", Integer.toString(requiredDeaths));
        context.put("actual", Integer.toString(request.playerDeaths()));
        return context;
    }

    private record Match(Optional<TargetKey> targetKey, OptionalInt requiredDeaths) {}
}
