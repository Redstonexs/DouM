package io.github.doum.deathgates.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.doum.deathgates.model.TargetKey;
import io.github.doum.deathgates.model.TargetKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TargetKeyTest {
    @Test
    void parsesMaterialTargetExactly() {
        TargetKey key = TargetKey.parse("material:minecraft:diamond_ore");

        assertEquals(TargetKind.MATERIAL, key.kind());
        assertEquals("minecraft", key.namespace());
        assertEquals("diamond_ore", key.key());
        assertEquals("material:minecraft:diamond_ore", key.asConfigKey());
    }

    @Test
    void parsesResultAndRecipeTargetsExactly() {
        assertEquals(
                new TargetKey(TargetKind.RESULT, "minecraft", "oak_planks"),
                TargetKey.parse("result:minecraft:oak_planks"));
        assertEquals(
                new TargetKey(TargetKind.RECIPE, "minecraft", "oak_planks"),
                TargetKey.parse("recipe:minecraft:oak_planks"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "minecraft:oak_planks",
        "material:minecraft",
        "material::oak_planks",
        "material:minecraft:",
        "material:minecraft:oak:planks",
        "unknown:minecraft:oak_planks",
        "material:Minecraft:oak_planks"
    })
    void malformedKeysFail(String rawKey) {
        assertThrows(IllegalArgumentException.class, () -> TargetKey.parse(rawKey));
    }
}
