package io.github.doum.deathgates.command;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.MessageKeys;
import io.github.doum.deathgates.i18n.Translations;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class DeathGatesCommand {
    private static final String RELOAD_PERMISSION = "deathgates.admin.reload";
    private static final String VIEW_PERMISSION = "deathgates.admin.view";
    private static final String SET_PERMISSION = "deathgates.admin.set";

    private final PlayerResolver playerResolver;
    private final DeathCountStore deathCountStore;
    private final ConfigReloader configReloader;
    private final MessageSink messages;
    private final Translations translations;

    public DeathGatesCommand(
            PlayerResolver playerResolver,
            DeathCountStore deathCountStore,
            ConfigReloader configReloader,
            MessageSink messages,
            Translations translations) {
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
        this.deathCountStore = Objects.requireNonNull(deathCountStore, "deathCountStore");
        this.configReloader = Objects.requireNonNull(configReloader, "configReloader");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.translations = Objects.requireNonNull(translations, "translations");
    }

    public boolean execute(CommandSender sender, String... arguments) {
        Objects.requireNonNull(sender, "sender");
        String[] safeArguments = arguments == null ? new String[0] : arguments;
        if (safeArguments.length == 0 || isBlank(safeArguments[0])) {
            sendUsage(sender);
            return true;
        }

        return switch (safeArguments[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, safeArguments);
            case "deaths" -> deaths(sender, safeArguments);
            case "setdeaths" -> setDeaths(sender, safeArguments);
            default -> unknownSubcommand(sender);
        };
    }

    private boolean reload(CommandSender sender, String[] arguments) {
        if (!requirePermission(sender, RELOAD_PERMISSION)) {
            return true;
        }
        if (arguments.length != 1) {
            sendUsage(sender);
            return true;
        }

        try {
            ConfigReloadResult result = configReloader.reload();
            if (result.success()) {
                send(sender, MessageKeys.COMMAND_RELOAD_SUCCESS, Map.of());
            } else {
                send(sender, MessageKeys.COMMAND_RELOAD_FAILURE, Map.of("actual", result.errorMessage()));
            }
        } catch (RuntimeException error) {
            send(sender, MessageKeys.COMMAND_RELOAD_FAILURE, Map.of("actual", failureMessage(error)));
        }
        return true;
    }

    private boolean deaths(CommandSender sender, String[] arguments) {
        if (!requirePermission(sender, VIEW_PERMISSION)) {
            return true;
        }
        if (arguments.length != 2 || isBlank(arguments[1])) {
            sendUsage(sender);
            return true;
        }

        Optional<OnlinePlayer> player = playerResolver.onlinePlayer(arguments[1]);
        if (player.isEmpty()) {
            send(sender, MessageKeys.COMMAND_PLAYER_NOT_FOUND, Map.of("player", arguments[1]));
            return true;
        }

        int deaths = deathCountStore.getDeaths(player.get().playerId());
        send(
                sender,
                MessageKeys.COMMAND_DEATHS,
                Map.of("player", player.get().name(), "count", Integer.toString(deaths)));
        return true;
    }

    private boolean setDeaths(CommandSender sender, String[] arguments) {
        if (!requirePermission(sender, SET_PERMISSION)) {
            return true;
        }
        if (arguments.length != 3 || isBlank(arguments[1]) || isBlank(arguments[2])) {
            sendUsage(sender);
            return true;
        }

        Optional<OnlinePlayer> player = playerResolver.onlinePlayer(arguments[1]);
        if (player.isEmpty()) {
            send(sender, MessageKeys.COMMAND_PLAYER_NOT_FOUND, Map.of("player", arguments[1]));
            return true;
        }

        OptionalInt count = parseNonNegativeInt(arguments[2]);
        if (count.isEmpty()) {
            send(sender, MessageKeys.COMMAND_INVALID_COUNT, Map.of("count", arguments[2]));
            return true;
        }

        OnlinePlayer onlinePlayer = player.get();
        deathCountStore.setDeaths(onlinePlayer.playerId(), count.getAsInt(), onlinePlayer.name());
        send(
                sender,
                MessageKeys.COMMAND_SET_DEATHS,
                Map.of("player", onlinePlayer.name(), "count", Integer.toString(count.getAsInt())));
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }

        send(sender, MessageKeys.COMMAND_NO_PERMISSION, Map.of());
        return false;
    }

    private boolean unknownSubcommand(CommandSender sender) {
        send(sender, MessageKeys.COMMAND_UNKNOWN_SUBCOMMAND, Map.of());
        return true;
    }

    private void sendUsage(CommandSender sender) {
        send(sender, MessageKeys.COMMAND_USAGE, Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> values) {
        messages.send(sender, translations.format(sender.language(), key, values));
    }

    private static OptionalInt parseNonNegativeInt(String rawCount) {
        try {
            int count = Integer.parseInt(rawCount);
            return count < 0 ? OptionalInt.empty() : OptionalInt.of(count);
        } catch (NumberFormatException error) {
            return OptionalInt.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String failureMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public interface CommandSender {
        boolean hasPermission(String permission);

        Language language();
    }

    @FunctionalInterface
    public interface PlayerResolver {
        Optional<OnlinePlayer> onlinePlayer(String playerName);
    }

    @FunctionalInterface
    public interface ConfigReloader {
        ConfigReloadResult reload();
    }

    @FunctionalInterface
    public interface MessageSink {
        void send(CommandSender sender, String message);
    }

    public record OnlinePlayer(UUID playerId, String name) {
        public OnlinePlayer {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    public record ConfigReloadResult(
            boolean success, DeathGatesConfig activeConfig, String errorMessage) {
        public ConfigReloadResult {
            Objects.requireNonNull(activeConfig, "activeConfig");
            if (success) {
                errorMessage = "";
            } else if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "unknown error";
            }
        }

        public static ConfigReloadResult success(DeathGatesConfig activeConfig) {
            return new ConfigReloadResult(true, activeConfig, "");
        }

        public static ConfigReloadResult failure(
                DeathGatesConfig preservedConfig, String errorMessage) {
            return new ConfigReloadResult(false, preservedConfig, errorMessage);
        }
    }
}
