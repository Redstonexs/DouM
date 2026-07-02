package io.github.doum.deathgates.listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class HardshipSleepRespawnTracker {
    private final HardshipRulesEngine engine;
    private final Map<UUID, RespawnSnapshot> respawnBeforeSleep = new ConcurrentHashMap<>();
    private final Set<UUID> fullSleepReached = ConcurrentHashMap.newKeySet();

    HardshipSleepRespawnTracker(HardshipRulesEngine engine) {
        this.engine = engine;
    }

    void recordBeforeSleep(Player player) {
        UUID playerId = player.getUniqueId();
        if (!engine.requireFullSleepRespawn()) {
            clear(player);
            return;
        }
        respawnBeforeSleep.put(playerId, RespawnSnapshot.capture(player.getRespawnLocation(false)));
        fullSleepReached.remove(playerId);
    }

    void recordFullSleepReached(Player player) {
        UUID playerId = player.getUniqueId();
        if (engine.requireFullSleepRespawn() && respawnBeforeSleep.containsKey(playerId)) {
            fullSleepReached.add(playerId);
        }
    }

    void restoreIfNeeded(Player player, int sleepTicks) {
        UUID playerId = player.getUniqueId();
        RespawnSnapshot snapshot = respawnBeforeSleep.remove(playerId);
        boolean fullSleepObserved = fullSleepReached.remove(playerId);
        if (snapshot == null || !engine.shouldRestoreRespawnBeforeFullSleep(fullSleepObserved, sleepTicks)) {
            return;
        }
        snapshot.restore(player);
    }

    void clear(Player player) {
        UUID playerId = player.getUniqueId();
        respawnBeforeSleep.remove(playerId);
        fullSleepReached.remove(playerId);
    }

    private record RespawnSnapshot(Location previousRespawn) {
        static RespawnSnapshot capture(Location previousRespawn) {
            return new RespawnSnapshot(previousRespawn == null ? null : previousRespawn.clone());
        }

        void restore(Player player) {
            player.setRespawnLocation(previousRespawn == null ? null : previousRespawn.clone(), false);
        }
    }
}
