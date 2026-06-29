package io.github.doum.deathgates.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DeathCountStoreTest {
    @TempDir
    private Path tempDir;

    @Test
    void unknownUuidHasCountZero() {
        DeathCountStore store = new YamlDeathCountStore(dataFile());

        assertEquals(0, store.getDeaths(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertTrue(store.getLatestKnownPlayerName(UUID.fromString("00000000-0000-0000-0000-000000000001")).isEmpty());
    }

    @Test
    void deathRecorderIncrementPersistsAfterReloadingDataYml() throws Exception {
        Path dataFile = dataFile();
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        DeathRecorder recorder = new DeathRecorder(new YamlDeathCountStore(dataFile));

        assertEquals(1, recorder.recordDeath(playerId, "Alex"));

        DeathCountStore reloaded = new YamlDeathCountStore(dataFile);
        assertEquals(1, reloaded.getDeaths(playerId));
        assertEquals(Optional.of("Alex"), reloaded.getLatestKnownPlayerName(playerId));
        assertTrue(Files.readString(dataFile).contains(playerId.toString()));
    }

    @Test
    void setDeathsOverwritesExistingCount() {
        Path dataFile = dataFile();
        UUID playerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        DeathCountStore store = new YamlDeathCountStore(dataFile);

        store.setDeaths(playerId, 3, "Blake");
        store.setDeaths(playerId, 9, "Blake");

        assertEquals(9, store.getDeaths(playerId));
        assertEquals(9, new YamlDeathCountStore(dataFile).getDeaths(playerId));
    }

    @Test
    void negativeSetDeathsIsRejectedAndLeavesExistingDataUnchanged() {
        Path dataFile = dataFile();
        UUID playerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        DeathCountStore store = new YamlDeathCountStore(dataFile);
        store.setDeaths(playerId, 4, "Casey");

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> store.setDeaths(playerId, -1, "Casey"));

        assertTrue(thrown.getMessage().contains("deaths"));
        assertEquals(4, store.getDeaths(playerId));
        DeathCountStore reloaded = new YamlDeathCountStore(dataFile);
        assertEquals(4, reloaded.getDeaths(playerId));
        assertEquals(Optional.of("Casey"), reloaded.getLatestKnownPlayerName(playerId));
    }

    @Test
    void nameChangeKeepsUuidCount() {
        Path dataFile = dataFile();
        UUID playerId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        DeathRecorder recorder = new DeathRecorder(new YamlDeathCountStore(dataFile));

        assertEquals(1, recorder.recordDeath(playerId, "Drew"));
        assertEquals(2, recorder.recordDeath(playerId, "DrewNewName"));

        DeathCountStore reloaded = new YamlDeathCountStore(dataFile);
        assertEquals(2, reloaded.getDeaths(playerId));
        assertEquals(Optional.of("DrewNewName"), reloaded.getLatestKnownPlayerName(playerId));
    }

    private Path dataFile() {
        return tempDir.resolve("data.yml");
    }
}
