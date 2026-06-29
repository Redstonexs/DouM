package io.github.doum.deathgates.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GateEvaluatorTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final TargetKey DIAMOND_ORE = TargetKey.parse("material:minecraft:diamond_ore");
    private static final TargetKey OAK_PLANKS_RESULT = TargetKey.parse("result:minecraft:oak_planks");
    private static final TargetKey OAK_PLANKS_RECIPE = TargetKey.parse("recipe:minecraft:oak_planks");

    private final GateEvaluator evaluator = new GateEvaluator();

    @Test
    void disabledOperationAllowsBeforeThresholds() {
        DeathGatesConfig config = config(operation(
                OperationType.BLOCK_BREAK,
                false,
                4,
                Map.of(DIAMOND_ORE, 8)));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_BREAK, 0, false, DIAMOND_ORE));

        assertTrue(decision.allowed());
        assertEquals(0, decision.requiredDeaths());
        assertEquals(Optional.empty(), decision.matchedTargetKey());
    }

    @Test
    void bypassAllowsBeforeThresholds() {
        DeathGatesConfig config = config(operation(
                OperationType.BLOCK_BREAK,
                true,
                4,
                Map.of(DIAMOND_ORE, 8)));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_BREAK, 0, true, DIAMOND_ORE));

        assertTrue(decision.allowed());
        assertEquals(0, decision.requiredDeaths());
        assertEquals(Optional.empty(), decision.matchedTargetKey());
    }

    @Test
    void targetOverrideBeatsOperationDefault() {
        DeathGatesConfig config = config(operation(
                OperationType.BLOCK_BREAK,
                true,
                2,
                Map.of(DIAMOND_ORE, 5)));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_BREAK, 4, false, DIAMOND_ORE));

        assertFalse(decision.allowed());
        assertEquals(5, decision.requiredDeaths());
        assertEquals(4, decision.actualDeaths());
        assertEquals(Optional.of(DIAMOND_ORE), decision.matchedTargetKey());
    }

    @Test
    void independentOperationsDoNotShareThresholds() {
        DeathGatesConfig config = config(
                operation(OperationType.BLOCK_BREAK, true, 8, Map.of()),
                operation(OperationType.BLOCK_PLACE, true, 2, Map.of()));

        GateDecision breakDecision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_BREAK, 2, false));
        GateDecision placeDecision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_PLACE, 2, false));

        assertFalse(breakDecision.allowed());
        assertEquals(8, breakDecision.requiredDeaths());
        assertTrue(placeDecision.allowed());
        assertEquals(2, placeDecision.requiredDeaths());
    }

    @Test
    void tooFewDeathsDenies() {
        DeathGatesConfig config = config(operation(OperationType.BLOCK_PLACE, true, 3, Map.of()));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_PLACE, 2, false));

        assertFalse(decision.allowed());
        assertEquals(3, decision.requiredDeaths());
        assertEquals(2, decision.actualDeaths());
    }

    @Test
    void exactThresholdAllows() {
        DeathGatesConfig config = config(operation(OperationType.BLOCK_PLACE, true, 3, Map.of()));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_PLACE, 3, false));

        assertTrue(decision.allowed());
        assertEquals(3, decision.requiredDeaths());
        assertEquals(3, decision.actualDeaths());
    }

    @Test
    void craftRecipeKeyBeatsResultKeyWhenBothArePresentInPriorityOrder() {
        DeathGatesConfig config = config(operation(
                OperationType.CRAFT_ITEM,
                true,
                99,
                Map.of(
                        OAK_PLANKS_RESULT, 2,
                        OAK_PLANKS_RECIPE, 5)));

        GateDecision decision = evaluator.evaluate(
                config,
                request(
                        OperationType.CRAFT_ITEM,
                        5,
                        false,
                        OAK_PLANKS_RECIPE,
                        OAK_PLANKS_RESULT));

        assertTrue(decision.allowed());
        assertEquals(5, decision.requiredDeaths());
        assertEquals(Optional.of(OAK_PLANKS_RECIPE), decision.matchedTargetKey());
    }

    @Test
    void deniedDecisionIncludesActualAndRequiredCountsInContext() {
        DeathGatesConfig config = config(operation(OperationType.CRAFT_ITEM, true, 4, Map.of()));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.CRAFT_ITEM, 3, false, OAK_PLANKS_RESULT));

        assertFalse(decision.allowed());
        assertEquals(4, decision.requiredDeaths());
        assertEquals(3, decision.actualDeaths());
        assertEquals("4", decision.denialContext().get("required"));
        assertEquals("3", decision.denialContext().get("actual"));
        assertEquals("craft-item", decision.denialContext().get("operation"));
        assertEquals("result:minecraft:oak_planks", decision.denialContext().get("target"));
    }

    @Test
    void emptyTargetListUsesOperationDefault() {
        DeathGatesConfig config = config(operation(OperationType.BLOCK_BREAK, true, 6, Map.of()));

        GateDecision decision = evaluator.evaluate(
                config,
                request(OperationType.BLOCK_BREAK, 5, false));

        assertFalse(decision.allowed());
        assertEquals(6, decision.requiredDeaths());
        assertEquals(Optional.empty(), decision.matchedTargetKey());
        assertEquals("", decision.denialContext().get("target"));
    }

    @Test
    void nullTargetListIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new GateRequest(OperationType.BLOCK_BREAK, PLAYER_ID, 0, null, false));
    }

    private static GateRequest request(
            OperationType operation, int deaths, boolean hasBypassPermission, TargetKey... targetKeys) {
        return new GateRequest(operation, PLAYER_ID, deaths, List.of(targetKeys), hasBypassPermission);
    }

    private static DeathGatesConfig config(OperationGateConfig... overrides) {
        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            operations.put(operation, operation(operation, true, 0, Map.of()));
        }
        for (OperationGateConfig override : overrides) {
            operations.put(override.operation(), override);
        }
        return new DeathGatesConfig(operations);
    }

    private static OperationGateConfig operation(
            OperationType operation,
            boolean enabled,
            int defaultRequiredDeaths,
            Map<TargetKey, Integer> targets) {
        return new OperationGateConfig(
                operation,
                enabled,
                defaultRequiredDeaths,
                "deathgates.bypass." + operation.id(),
                "Denied {operation} {target} {required} {actual}",
                targets);
    }
}
