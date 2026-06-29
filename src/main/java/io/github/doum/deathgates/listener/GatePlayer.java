package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.i18n.Language;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

record GatePlayer(UUID playerId, String playerName, Language language, Predicate<String> permissionChecker) {
    GatePlayer {
        Objects.requireNonNull(playerId, "playerId");
        playerName = playerName == null ? "" : playerName;
        language = language == null ? Language.DEFAULT : language;
        Objects.requireNonNull(permissionChecker, "permissionChecker");
    }

    boolean hasPermission(String permission) {
        return permission != null && !permission.isBlank() && permissionChecker.test(permission);
    }
}
