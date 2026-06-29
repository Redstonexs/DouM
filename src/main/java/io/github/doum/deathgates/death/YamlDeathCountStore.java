package io.github.doum.deathgates.death;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlDeathCountStore implements DeathCountStore {
    private final Path dataFile;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public YamlDeathCountStore(Path dataFile) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile").toAbsolutePath();
        reload();
    }

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
        save();
        return updated;
    }

    @Override
    public synchronized void setDeaths(UUID playerId, int deaths, String latestKnownPlayerName) {
        Objects.requireNonNull(playerId, "playerId");
        if (deaths < 0) {
            throw new IllegalArgumentException("deaths must not be negative");
        }
        entries.put(playerId, new Entry(deaths, normalizeName(latestKnownPlayerName)));
        save();
    }

    @Override
    public synchronized void reload() {
        try {
            Map<UUID, Entry> loadedEntries = readEntries();
            entries.clear();
            entries.putAll(loadedEntries);
        } catch (IOException | InvalidConfigurationException exception) {
            throw storageFailure("read", exception);
        }
    }

    @Override
    public synchronized void save() {
        try {
            writeEntries();
        } catch (IOException exception) {
            throw storageFailure("write", exception);
        }
    }

    private Map<UUID, Entry> readEntries() throws IOException, InvalidConfigurationException {
        if (!Files.exists(dataFile)) {
            return Map.of();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(dataFile.toFile());
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return Map.of();
        }

        Map<UUID, Entry> loaded = new HashMap<>();
        for (String rawPlayerId : players.getKeys(false)) {
            UUID playerId = parsePlayerId(rawPlayerId);
            String deathPath = rawPlayerId + ".deaths";
            if (!players.isInt(deathPath)) {
                throw malformed(rawPlayerId, "missing integer deaths");
            }

            int deaths = players.getInt(deathPath);
            if (deaths < 0) {
                throw malformed(rawPlayerId, "deaths must not be negative");
            }

            loaded.put(
                    playerId,
                    new Entry(deaths, normalizeName(players.getString(rawPlayerId + ".latest-name"))));
        }
        return loaded;
    }

    private void writeEntries() throws IOException {
        Path parent = dataFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryFile = Files.createTempFile(parent, "deathgates-data", ".yml.tmp");
        boolean moved = false;
        try {
            toYaml().save(temporaryFile.toFile());
            moveAtomicallyIfPossible(temporaryFile);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private YamlConfiguration toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        entries.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    String path = "players." + entry.getKey();
                    yaml.set(path + ".deaths", entry.getValue().deaths());
                    String name = entry.getValue().latestKnownPlayerName();
                    if (name != null) {
                        yaml.set(path + ".latest-name", name);
                    }
                });
        return yaml;
    }

    private void moveAtomicallyIfPossible(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    dataFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static UUID parsePlayerId(String rawPlayerId) {
        try {
            return UUID.fromString(rawPlayerId);
        } catch (IllegalArgumentException exception) {
            throw malformed(rawPlayerId, "invalid player UUID");
        }
    }

    private static String normalizeName(String latestKnownPlayerName) {
        if (latestKnownPlayerName == null || latestKnownPlayerName.isBlank()) {
            return null;
        }
        return latestKnownPlayerName.trim();
    }

    private IllegalStateException storageFailure(String action, Exception exception) {
        return new IllegalStateException("Could not " + action + " death counts at " + dataFile, exception);
    }

    private static IllegalArgumentException malformed(String playerId, String message) {
        return new IllegalArgumentException("Malformed death count data.yml for " + playerId + ": " + message);
    }

    private record Entry(int deaths, String latestKnownPlayerName) {}
}
