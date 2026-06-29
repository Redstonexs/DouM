package io.github.doum.deathgates.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.command.DeathGatesCommand.CommandSender;
import io.github.doum.deathgates.command.DeathGatesCommand.ConfigReloadResult;
import io.github.doum.deathgates.command.DeathGatesCommand.ConfigReloader;
import io.github.doum.deathgates.command.DeathGatesCommand.MessageSink;
import io.github.doum.deathgates.command.DeathGatesCommand.OnlinePlayer;
import io.github.doum.deathgates.command.DeathGatesCommand.PlayerResolver;
import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.death.InMemoryDeathCountStore;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.i18n.TranslationsLoader;
import io.github.doum.deathgates.model.OperationType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeathGatesCommandTest {
    private static final UUID ALEX_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final OnlinePlayer ALEX = new OnlinePlayer(ALEX_ID, "Alex");

    private final InMemoryDeathCountStore store = new InMemoryDeathCountStore();
    private final FakePlayerResolver players = new FakePlayerResolver(ALEX);
    private final FakeConfigReloader reloader = new FakeConfigReloader(config(2), config(7));
    private final CapturingMessages messages = new CapturingMessages();
    private final Translations translations = TranslationsLoader.load();
    private final DeathGatesCommand command =
            new DeathGatesCommand(players, store, reloader, messages, translations);

    @Test
    void missingPermissionDeniesReloadViewAndSetCommands() {
        FakeSender sender = new FakeSender();

        command.execute(sender, "reload");
        assertTrue(messages.last().contains("permission"));
        assertEquals(0, reloader.reloadCalls());

        messages.clear();
        command.execute(sender, "deaths", "Alex");
        assertTrue(messages.last().contains("permission"));

        messages.clear();
        command.execute(sender, "setdeaths", "Alex", "5");
        assertTrue(messages.last().contains("permission"));
        assertEquals(0, store.getDeaths(ALEX_ID));
    }

    @Test
    void setDeathsUpdatesStoreAndDeathsReportsCurrentCount() {
        FakeSender setter = new FakeSender("deathgates.admin.set");
        FakeSender viewer = new FakeSender("deathgates.admin.view");

        command.execute(setter, "setdeaths", "Alex", "5");
        assertEquals(5, store.getDeaths(ALEX_ID));
        assertTrue(messages.last().contains("Alex"));
        assertTrue(messages.last().contains("5"));

        command.execute(viewer, "deaths", "Alex");
        assertTrue(messages.last().contains("Alex"));
        assertTrue(messages.last().contains("5"));
    }

    @Test
    void invalidCountIsRejectedAndDoesNotUpdateStore() {
        FakeSender sender = new FakeSender("deathgates.admin.set");
        store.setDeaths(ALEX_ID, 3, "Alex");

        command.execute(sender, "setdeaths", "Alex", "nope");
        assertTrue(messages.last().contains("count"));
        assertEquals(3, store.getDeaths(ALEX_ID));

        command.execute(sender, "setdeaths", "Alex", "-1");
        assertTrue(messages.last().contains("count"));
        assertEquals(3, store.getDeaths(ALEX_ID));
    }

    @Test
    void reloadFailurePreservesPreviousConfigAndReportsFailure() {
        FakeSender sender = new FakeSender("deathgates.admin.reload");
        reloader.failNextReload("Required deaths cannot be negative");

        command.execute(sender, "reload");

        assertEquals(1, reloader.reloadCalls());
        assertEquals(2, reloader.currentConfig()
                .operation(OperationType.BLOCK_BREAK)
                .defaultRequiredDeaths());
        assertTrue(messages.last().contains("Reload failed"));
        assertTrue(messages.last().contains("Required deaths cannot be negative"));
    }

    @Test
    void malformedInputAndUnknownPlayersAreRejected() {
        FakeSender viewer = new FakeSender("deathgates.admin.view");
        FakeSender setter = new FakeSender("deathgates.admin.set");

        command.execute(viewer);
        assertTrue(messages.last().contains("Usage"));

        command.execute(viewer, "unknown");
        assertTrue(messages.last().contains("Unknown subcommand"));

        command.execute(viewer, "deaths");
        assertTrue(messages.last().contains("Usage"));

        command.execute(viewer, "deaths", "Missing");
        assertTrue(messages.last().contains("online player"));

        command.execute(setter, "setdeaths", "Missing", "5");
        assertTrue(messages.last().contains("online player"));
    }

    @Test
    void chineseSenderReceivesLocalizedMessages() {
        FakeSender setter = new FakeSender(Language.CHINESE, "deathgates.admin.set");

        command.execute(setter, "setdeaths", "Alex", "5");

        assertEquals(5, store.getDeaths(ALEX_ID));
        assertTrue(messages.last().contains("死亡次数"));
        assertTrue(messages.last().contains("Alex"));
        assertTrue(messages.last().contains("5"));
    }

    private static DeathGatesConfig config(int blockBreakDefaultDeaths) {
        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            int requiredDeaths = operation == OperationType.BLOCK_BREAK ? blockBreakDefaultDeaths : 0;
            operations.put(
                    operation,
                    new OperationGateConfig(
                            operation,
                            true,
                            requiredDeaths,
                            "deathgates.bypass." + operation.id(),
                            "",
                            Map.of()));
        }
        return new DeathGatesConfig(operations);
    }

    private static final class FakeSender implements CommandSender {
        private final Set<String> permissions;
        private final Language language;

        private FakeSender(String... permissions) {
            this(Language.ENGLISH, permissions);
        }

        private FakeSender(Language language, String... permissions) {
            this.language = language;
            this.permissions = Set.of(permissions);
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        @Override
        public Language language() {
            return language;
        }
    }

    private static final class FakePlayerResolver implements PlayerResolver {
        private final Map<String, OnlinePlayer> playersByName = new HashMap<>();

        private FakePlayerResolver(OnlinePlayer... players) {
            for (OnlinePlayer player : players) {
                playersByName.put(player.name(), player);
            }
        }

        @Override
        public Optional<OnlinePlayer> onlinePlayer(String playerName) {
            return Optional.ofNullable(playersByName.get(playerName));
        }
    }

    private static final class FakeConfigReloader implements ConfigReloader {
        private DeathGatesConfig currentConfig;
        private final DeathGatesConfig nextConfig;
        private int reloadCalls;
        private String failureMessage;

        private FakeConfigReloader(DeathGatesConfig currentConfig, DeathGatesConfig nextConfig) {
            this.currentConfig = currentConfig;
            this.nextConfig = nextConfig;
        }

        @Override
        public ConfigReloadResult reload() {
            reloadCalls++;
            if (failureMessage != null) {
                String message = failureMessage;
                failureMessage = null;
                return ConfigReloadResult.failure(currentConfig, message);
            }

            currentConfig = nextConfig;
            return ConfigReloadResult.success(currentConfig);
        }

        private void failNextReload(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        private DeathGatesConfig currentConfig() {
            return currentConfig;
        }

        private int reloadCalls() {
            return reloadCalls;
        }
    }

    private static final class CapturingMessages implements MessageSink {
        private final List<String> sent = new ArrayList<>();

        @Override
        public void send(CommandSender sender, String message) {
            sent.add(message);
        }

        private String last() {
            return sent.getLast();
        }

        private void clear() {
            sent.clear();
        }
    }
}
