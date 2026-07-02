package io.github.doum.deathgates.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.HardshipRulesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.model.OperationType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class HardshipSleepRespawnTest {
    private static final UUID PLAYER_ID = new UUID(0L, 42L);

    @Test
    void earlyBedLeaveRestoresPreviousRespawn() {
        HardshipSleepRespawnTracker tracker = tracker(new HardshipRulesConfig.Sleep(false, true, 100));
        TestPlayer player = new TestPlayer(location(1, 64, 1));

        tracker.recordBeforeSleep(player.proxy());
        player.simulateVanillaRespawn(location(8, 64, 8));
        tracker.restoreIfNeeded(player.proxy(), 20);

        assertEquals(location(1, 64, 1), player.respawnLocation());
        assertEquals(1, player.respawnSetCalls());
    }

    @Test
    void deepSleepAllowsBedRespawnEvenWhenNightSkipWillBeCancelled() {
        HardshipSleepRespawnTracker tracker = tracker(new HardshipRulesConfig.Sleep(true, true, 100));
        TestPlayer player = new TestPlayer(location(1, 64, 1));
        Location bedRespawn = location(8, 64, 8);

        tracker.recordBeforeSleep(player.proxy());
        player.simulateVanillaRespawn(bedRespawn);
        tracker.recordFullSleepReached(player.proxy());
        tracker.restoreIfNeeded(player.proxy(), 20);

        assertEquals(bedRespawn, player.respawnLocation());
        assertEquals(0, player.respawnSetCalls());
    }

    @Test
    void configuredTickThresholdAllowsRespawnWithoutDeepSleepMarker() {
        HardshipSleepRespawnTracker tracker = tracker(new HardshipRulesConfig.Sleep(false, true, 80));
        TestPlayer player = new TestPlayer(location(1, 64, 1));
        Location bedRespawn = location(8, 64, 8);

        tracker.recordBeforeSleep(player.proxy());
        player.simulateVanillaRespawn(bedRespawn);
        tracker.restoreIfNeeded(player.proxy(), 80);

        assertEquals(bedRespawn, player.respawnLocation());
        assertEquals(0, player.respawnSetCalls());
    }

    @Test
    void disabledFullSleepRespawnLeavesRespawnUntouched() {
        HardshipSleepRespawnTracker tracker = tracker(new HardshipRulesConfig.Sleep(false, false, 100));
        TestPlayer player = new TestPlayer(location(1, 64, 1));
        Location bedRespawn = location(8, 64, 8);

        tracker.recordBeforeSleep(player.proxy());
        player.simulateVanillaRespawn(bedRespawn);
        tracker.restoreIfNeeded(player.proxy(), 20);

        assertEquals(bedRespawn, player.respawnLocation());
        assertEquals(0, player.respawnSetCalls());
    }

    @Test
    void earlyBedLeaveClearsNewRespawnWhenPlayerHadNoPreviousRespawn() {
        HardshipSleepRespawnTracker tracker = tracker(new HardshipRulesConfig.Sleep(false, true, 100));
        TestPlayer player = new TestPlayer(null);

        tracker.recordBeforeSleep(player.proxy());
        player.simulateVanillaRespawn(location(8, 64, 8));
        tracker.restoreIfNeeded(player.proxy(), 20);

        assertNull(player.respawnLocation());
        assertEquals(1, player.respawnSetCalls());
    }

    private static HardshipSleepRespawnTracker tracker(HardshipRulesConfig.Sleep sleep) {
        HardshipRulesConfig defaults = HardshipRulesConfig.disabled();
        HardshipRulesConfig rules = new HardshipRulesConfig(
                defaults.crafting(),
                defaults.furnace(),
                defaults.storage(),
                sleep,
                defaults.fishing(),
                defaults.fall(),
                defaults.health(),
                defaults.biomes(),
                defaults.blockRetaliation());
        return new HardshipSleepRespawnTracker(new HardshipRulesEngine(() -> config(rules), () -> 99));
    }

    private static DeathGatesConfig config(HardshipRulesConfig hardship) {
        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            operations.put(
                    operation,
                    new OperationGateConfig(
                            operation,
                            true,
                            0,
                            "doum.deathnum.bypass." + operation.id(),
                            "",
                            Map.of()));
        }
        return new DeathGatesConfig(operations, DeathGatesConfig.DEFAULT_MESSAGE_PREFIX, hardship);
    }

    private static Location location(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    private static final class TestPlayer implements InvocationHandler {
        private final Player proxy;
        private Location respawnLocation;
        private int respawnSetCalls;

        TestPlayer(Location respawnLocation) {
            this.respawnLocation = copy(respawnLocation);
            this.proxy = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[] {Player.class},
                    this);
        }

        Player proxy() {
            return proxy;
        }

        void simulateVanillaRespawn(Location location) {
            respawnLocation = copy(location);
        }

        Location respawnLocation() {
            return copy(respawnLocation);
        }

        int respawnSetCalls() {
            return respawnSetCalls;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "getUniqueId" -> PLAYER_ID;
                case "hasPermission" -> Set.<String>of().contains((String) args[0]);
                case "getRespawnLocation" -> copy(respawnLocation);
                case "setRespawnLocation" -> setRespawnLocation(args);
                case "toString" -> "sleep-test-player";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object setRespawnLocation(Object[] args) {
            respawnLocation = copy((Location) args[0]);
            respawnSetCalls++;
            return null;
        }

        private static Location copy(Location location) {
            return location == null ? null : location.clone();
        }
    }
}
