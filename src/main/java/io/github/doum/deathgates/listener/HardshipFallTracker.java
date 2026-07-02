package io.github.doum.deathgates.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HardshipFallTracker {
    private final HardshipRulesEngine engine;
    private final Map<UUID, Double> highestAirborneY = new ConcurrentHashMap<>();

    HardshipFallTracker(HardshipRulesEngine engine) {
        this.engine = engine;
    }

    double landingDamage(UUID playerId, double fromY, double toY, boolean grounded, boolean cushioned) {
        if (cushioned) {
            clear(playerId);
            return 0.0;
        }

        double highestY = Math.max(Math.max(fromY, toY), highestAirborneY.getOrDefault(playerId, fromY));
        if (!grounded) {
            highestAirborneY.put(playerId, highestY);
            return 0.0;
        }

        highestAirborneY.remove(playerId);
        double fallDistance = Math.max(0.0, highestY - toY);
        return engine.manualLandingDamage(fallDistance);
    }

    void clear(UUID playerId) {
        highestAirborneY.remove(playerId);
    }
}
