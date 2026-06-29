package io.github.doum.deathgates.config;

import org.bukkit.configuration.ConfigurationSection;

public final class BukkitDeathGatesConfigLoader {
    private BukkitDeathGatesConfigLoader() {}

    public static DeathGatesConfig load(ConfigurationSection root) {
        return DeathGatesConfigLoader.load(root);
    }
}
