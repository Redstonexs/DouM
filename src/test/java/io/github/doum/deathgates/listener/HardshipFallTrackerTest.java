package io.github.doum.deathgates.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.HardshipRulesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.model.OperationType;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HardshipFallTrackerTest {
    private static final UUID PLAYER_ID = new UUID(0L, 7L);

    @Test
    void threeBlockLandingTriggersConfiguredDamage() {
        HardshipFallTracker tracker = tracker(new HardshipRulesConfig.Fall(true, 3, 2));

        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 70.0, 69.0, false, false));
        assertEquals(2.0, tracker.landingDamage(PLAYER_ID, 69.0, 67.0, true, false));
    }

    @Test
    void belowThresholdLandingDoesNothing() {
        HardshipFallTracker tracker = tracker(new HardshipRulesConfig.Fall(true, 3, 2));

        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 70.0, 69.0, false, false));
        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 69.0, 67.01, true, false));
    }

    @Test
    void vanillaFallDamageDistanceIsLeftToDamageEvent() {
        HardshipFallTracker tracker = tracker(new HardshipRulesConfig.Fall(true, 3, 2));

        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 70.0, 69.0, false, false));
        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 69.0, 66.0, true, false));
    }

    @Test
    void disabledFallRuleDoesNothing() {
        HardshipFallTracker tracker = tracker(new HardshipRulesConfig.Fall(false, 3, 2));

        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 70.0, 69.0, false, false));
        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 69.0, 67.0, true, false));
    }

    @Test
    void cushionedLandingClearsTrackedFall() {
        HardshipFallTracker tracker = tracker(new HardshipRulesConfig.Fall(true, 3, 2));

        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 70.0, 69.0, false, false));
        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 69.0, 67.0, true, true));
        assertEquals(0.0, tracker.landingDamage(PLAYER_ID, 67.0, 67.0, true, false));
    }

    private static HardshipFallTracker tracker(HardshipRulesConfig.Fall fall) {
        HardshipRulesConfig defaults = HardshipRulesConfig.disabled();
        HardshipRulesConfig rules = new HardshipRulesConfig(
                defaults.crafting(),
                defaults.furnace(),
                defaults.storage(),
                defaults.sleep(),
                defaults.fishing(),
                fall,
                defaults.health(),
                defaults.biomes(),
                defaults.blockRetaliation());
        return new HardshipFallTracker(new HardshipRulesEngine(() -> config(rules), () -> 99));
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
}
