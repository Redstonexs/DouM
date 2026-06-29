package io.github.doum.deathgates.command;

import io.github.doum.deathgates.command.DeathGatesCommand.ConfigReloader;
import io.github.doum.deathgates.command.DeathGatesCommand.OnlinePlayer;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.Translations;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class BukkitDeathGatesCommandExecutor implements CommandExecutor {
    private final Server server;
    private final DeathGatesCommand command;

    public BukkitDeathGatesCommandExecutor(
            Server server,
            DeathCountStore deathCountStore,
            ConfigReloader configReloader,
            Translations translations) {
        this.server = Objects.requireNonNull(server, "server");
        this.command = new DeathGatesCommand(
                this::onlinePlayer,
                deathCountStore,
                configReloader,
                BukkitDeathGatesCommandExecutor::sendMessage,
                translations);
    }

    @Override
    public boolean onCommand(
            @NotNull org.bukkit.command.CommandSender sender,
            @NotNull Command bukkitCommand,
            @NotNull String label,
            @NotNull String[] args) {
        return command.execute(new BukkitCommandSender(sender), args);
    }

    private Optional<OnlinePlayer> onlinePlayer(String playerName) {
        Player player = server.getPlayerExact(playerName);
        if (player == null) {
            return Optional.empty();
        }
        return Optional.of(new OnlinePlayer(player.getUniqueId(), player.getName()));
    }

    private static void sendMessage(DeathGatesCommand.CommandSender sender, String message) {
        if (!(sender instanceof BukkitCommandSender bukkitSender)) {
            throw new IllegalArgumentException("Unsupported command sender: " + sender.getClass().getName());
        }
        bukkitSender.sender().sendMessage(message);
    }

    private record BukkitCommandSender(org.bukkit.command.CommandSender sender)
            implements DeathGatesCommand.CommandSender {
        private BukkitCommandSender {
            Objects.requireNonNull(sender, "sender");
        }

        @Override
        public boolean hasPermission(String permission) {
            return sender.hasPermission(permission);
        }

        @Override
        public Language language() {
            return sender instanceof Player player ? Language.fromLocale(player.locale()) : Language.DEFAULT;
        }
    }
}
