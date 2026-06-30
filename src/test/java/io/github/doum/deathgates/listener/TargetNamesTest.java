package io.github.doum.deathgates.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class TargetNamesTest {
    @Test
    void humanizeTitleCasesRegistryKey() {
        assertEquals("Diamond Ore", TargetNames.humanize(new NamespacedKey("minecraft", "diamond_ore")));
        assertEquals("Stone", TargetNames.humanize(new NamespacedKey("minecraft", "stone")));
    }
}
