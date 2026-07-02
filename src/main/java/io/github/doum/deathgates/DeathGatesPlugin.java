package io.github.doum.deathgates;

import io.github.doum.deathgates.command.BukkitDeathGatesCommandExecutor;
import io.github.doum.deathgates.command.DeathGatesCommand.ConfigReloadResult;
import io.github.doum.deathgates.config.BukkitDeathGatesConfigLoader;
import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.death.DeathRecorder;
import io.github.doum.deathgates.death.YamlDeathCountStore;
import io.github.doum.deathgates.gate.GateEvaluator;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.i18n.TranslationsLoader;
import io.github.doum.deathgates.listener.BlockGateListener;
import io.github.doum.deathgates.listener.CraftGateListener;
import io.github.doum.deathgates.listener.DeathListener;
import io.github.doum.deathgates.listener.HardshipRulesListener;
import io.github.doum.deathgates.listener.TargetNames;
import io.github.doum.deathgates.message.ChatRenderer;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathGatesPlugin extends JavaPlugin {
    private volatile DeathGatesConfig config;
    private DeathCountStore deathCountStore;
    private Translations translations;
    private ChatRenderer chatRenderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = BukkitDeathGatesConfigLoader.load(getConfig());
        translations = TranslationsLoader.load();
        chatRenderer = new ChatRenderer(() -> currentConfig().messagePrefix());

        Path dataFile = getDataFolder().toPath().resolve("data.yml");
        deathCountStore = new YamlDeathCountStore(dataFile);
        DeathRecorder deathRecorder = new DeathRecorder(deathCountStore);
        GateEvaluator gateEvaluator = new GateEvaluator();

        registerDeathGatesCommand();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new DeathListener(deathRecorder), this);
        pluginManager.registerEvents(
                new BlockGateListener(
                        this::currentConfig, deathCountStore, gateEvaluator, translations, chatRenderer,
                        TargetNames::of),
                this);
        pluginManager.registerEvents(
                new CraftGateListener(
                        this::currentConfig, deathCountStore, gateEvaluator, translations, chatRenderer,
                        TargetNames::of),
                this);
        pluginManager.registerEvents(new HardshipRulesListener(this::currentConfig), this);

        getLogger().info("DouM enabled.");
    }

    @Override
    public void onDisable() {
        if (deathCountStore != null) {
            deathCountStore.save();
        }
        getLogger().info("DouM disabled.");
    }

    private DeathGatesConfig currentConfig() {
        DeathGatesConfig currentConfig = config;
        if (currentConfig == null) {
            throw new IllegalStateException("DouM config is not loaded");
        }
        return currentConfig;
    }

    private void registerDeathGatesCommand() {
        PluginCommand pluginCommand = getCommand("doum");
        if (pluginCommand == null) {
            String message = "Missing /doum command metadata in plugin.yml";
            getLogger().severe(message);
            throw new IllegalStateException(message);
        }

        letCoreCommandHandleSubcommandPermissions(pluginCommand);
        pluginCommand.setExecutor(new BukkitDeathGatesCommandExecutor(
                getServer(), deathCountStore, this::reloadDeathGatesConfig, translations, chatRenderer));
    }

    private static void letCoreCommandHandleSubcommandPermissions(PluginCommand pluginCommand) {
        pluginCommand.setPermission(null);
    }

    private ConfigReloadResult reloadDeathGatesConfig() {
        DeathGatesConfig previousConfig = currentConfig();
        try {
            saveDefaultConfig();
            reloadConfig();
            DeathGatesConfig loadedConfig = BukkitDeathGatesConfigLoader.load(getConfig());
            config = loadedConfig;
            return ConfigReloadResult.success(loadedConfig);
        } catch (RuntimeException error) {
            String message = failureMessage(error);
            getLogger().log(Level.WARNING, "Could not reload DouM config; keeping previous valid config.", error);
            return ConfigReloadResult.failure(previousConfig, message);
        }
    }

    private static String failureMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
