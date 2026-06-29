package io.github.doum.deathgates.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageFormatterTest {
    @Test
    void placeholdersRenderExactValuesAndUnknownPlaceholdersRemain() {
        String rendered = MessageFormatter.format(
                "{player}|{operation}|{target}|{required}|{actual}|{unknown}",
                Map.of(
                        "player", "Alex",
                        "operation", "block-break",
                        "target", "material:minecraft:diamond_ore",
                        "required", "5",
                        "actual", "4"));

        assertEquals(
                "Alex|block-break|material:minecraft:diamond_ore|5|4|{unknown}",
                rendered);
    }

    @Test
    void repeatedPlaceholdersRenderEveryOccurrence() {
        String rendered = MessageFormatter.format(
                "{player} needs {required}; {player} has {actual}",
                Map.of("player", "Alex", "required", "5", "actual", "4"));

        assertEquals("Alex needs 5; Alex has 4", rendered);
    }
}
