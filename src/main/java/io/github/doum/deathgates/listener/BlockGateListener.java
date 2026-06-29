package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.gate.GateDecision;
import io.github.doum.deathgates.gate.GateEvaluator;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.model.OperationType;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockGateListener implements Listener {
    private final Supplier<DeathGatesConfig> configSupplier;
    private final DeathCountStore deathCountStore;
    private final GateEvaluator gateEvaluator;
    private final Translations translations;

    public BlockGateListener(
            Supplier<DeathGatesConfig> configSupplier,
            DeathCountStore deathCountStore,
            GateEvaluator gateEvaluator,
            Translations translations) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.deathCountStore = Objects.requireNonNull(deathCountStore, "deathCountStore");
        this.gateEvaluator = Objects.requireNonNull(gateEvaluator, "gateEvaluator");
        this.translations = Objects.requireNonNull(translations, "translations");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        handleBlockBreak(
                event.getBlock().getType(),
                bukkitPlayer(player),
                () -> event.setCancelled(true),
                player::sendMessage);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        handleBlockPlace(
                event.getBlockPlaced().getType(),
                bukkitPlayer(player),
                () -> event.setCancelled(true),
                player::sendMessage);
    }

    GateDecision handleBlockBreak(
            Material material,
            GatePlayer player,
            Runnable cancelDenied,
            Consumer<String> messageSink) {
        return handleBlockGate(OperationType.BLOCK_BREAK, material, player, cancelDenied, messageSink);
    }

    GateDecision handleBlockPlace(
            Material material,
            GatePlayer player,
            Runnable cancelDenied,
            Consumer<String> messageSink) {
        return handleBlockGate(OperationType.BLOCK_PLACE, material, player, cancelDenied, messageSink);
    }

    private GateDecision handleBlockGate(
            OperationType operation,
            Material material,
            GatePlayer player,
            Runnable cancelDenied,
            Consumer<String> messageSink) {
        return GateEventSupport.enforce(
                configSupplier,
                deathCountStore,
                gateEvaluator,
                translations,
                operation,
                player,
                List.of(GateEventSupport.materialTarget(material)),
                cancelDenied,
                messageSink);
    }

    private static GatePlayer bukkitPlayer(Player player) {
        return new GatePlayer(
                player.getUniqueId(),
                player.getName(),
                Language.fromLocale(player.locale()),
                player::hasPermission);
    }
}
