package io.github.doum.deathgates.death;

import java.util.Optional;
import java.util.UUID;

public interface DeathCountStore {
    int getDeaths(UUID playerId);

    Optional<String> getLatestKnownPlayerName(UUID playerId);

    int incrementDeaths(UUID playerId, String latestKnownPlayerName);

    void setDeaths(UUID playerId, int deaths, String latestKnownPlayerName);

    void reload();

    void save();
}
