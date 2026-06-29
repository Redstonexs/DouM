package io.github.doum.deathgates.death;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class InMemoryDeathCountStore implements DeathCountStore {
    private final Map<UUID, Entry> entries = new HashMap<>();

    @Override
    public synchronized int getDeaths(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Entry entry = entries.get(playerId);
        return entry == null ? 0 : entry.deaths();
    }

    @Override
    public synchronized Optional<String> getLatestKnownPlayerName(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Entry entry = entries.get(playerId);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.latestKnownPlayerName());
    }

    @Override
    public synchronized int incrementDeaths(UUID playerId, String latestKnownPlayerName) {
        Objects.requireNonNull(playerId, "playerId");
        Entry previous = entries.get(playerId);
        int updated = previous == null ? 1 : previous.deaths() + 1;
        entries.put(playerId, new Entry(updated, normalizeName(latestKnownPlayerName)));
        return updated;
    }

    @Override
    public synchronized void setDeaths(UUID playerId, int deaths, String latestKnownPlayerName) {
        Objects.requireNonNull(playerId, "playerId");
        if (deaths < 0) {
            throw new IllegalArgumentException("deaths must not be negative");
        }
        entries.put(playerId, new Entry(deaths, normalizeName(latestKnownPlayerName)));
    }

    @Override
    public void reload() {
    }

    @Override
    public void save() {
    }

    private static String normalizeName(String latestKnownPlayerName) {
        if (latestKnownPlayerName == null || latestKnownPlayerName.isBlank()) {
            return null;
        }
        return latestKnownPlayerName.trim();
    }

    private record Entry(int deaths, String latestKnownPlayerName) {}
}
