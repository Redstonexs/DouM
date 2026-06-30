package io.github.doum.deathgates.listener;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Resolves a player-facing name {@link Component} for a material. It prefers Minecraft's own
 * translation key so every client renders the name in its own language (e.g. Stone / 石头); if that
 * lookup is unavailable it falls back to a humanized registry key ({@code diamond_ore} → "Diamond
 * Ore"). {@link #of(Material)} touches the server (via the material's translation key) and so runs
 * only inside live event handlers; tests inject their own name resolver instead.
 */
public final class TargetNames {
    private TargetNames() {}

    public static Component of(Material material) {
        try {
            return Component.translatable(material);
        } catch (RuntimeException ignored) {
            // No translation available off-server or for this material; fall back to the key below.
            return Component.text(humanize(material.getKey()));
        }
    }

    static String humanize(NamespacedKey key) {
        String[] words = key.getKey().replace('/', ' ').replace('_', ' ').trim().split(" +");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            builder.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.isEmpty() ? key.getKey() : builder.toString();
    }
}
