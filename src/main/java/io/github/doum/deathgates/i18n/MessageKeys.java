package io.github.doum.deathgates.i18n;

import io.github.doum.deathgates.model.OperationType;

/** Message bundle keys shared by the command, listeners, and the {@code .properties} files. */
public final class MessageKeys {
    public static final String COMMAND_USAGE = "command.usage";
    public static final String COMMAND_UNKNOWN_SUBCOMMAND = "command.unknown-subcommand";
    public static final String COMMAND_NO_PERMISSION = "command.no-permission";
    public static final String COMMAND_PLAYER_NOT_FOUND = "command.player-not-found";
    public static final String COMMAND_DEATHS = "command.deaths";
    public static final String COMMAND_SET_DEATHS = "command.set-deaths";
    public static final String COMMAND_INVALID_COUNT = "command.invalid-count";
    public static final String COMMAND_RELOAD_SUCCESS = "command.reload-success";
    public static final String COMMAND_RELOAD_FAILURE = "command.reload-failure";

    private MessageKeys() {}

    /** Deny-message key for an operation, e.g. {@code deny.block-break}. */
    public static String deny(OperationType operation) {
        return "deny." + operation.id();
    }
}
