package io.github.doum.deathgates.death;

import java.util.Objects;
import java.util.UUID;

public final class DeathRecorder {
    private final DeathCountStore deathCountStore;

    public DeathRecorder(DeathCountStore deathCountStore) {
        this.deathCountStore = Objects.requireNonNull(deathCountStore, "deathCountStore");
    }

    public int recordDeath(UUID playerId, String latestKnownPlayerName) {
        return deathCountStore.incrementDeaths(playerId, latestKnownPlayerName);
    }
}
