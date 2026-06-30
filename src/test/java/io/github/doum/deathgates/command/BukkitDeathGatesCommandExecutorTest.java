package io.github.doum.deathgates.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.command.DeathGatesCommand.ConfigReloadResult;
import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.death.InMemoryDeathCountStore;
import io.github.doum.deathgates.i18n.TranslationsLoader;
import io.github.doum.deathgates.message.ChatRenderer;
import io.github.doum.deathgates.model.OperationType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BukkitDeathGatesCommandExecutorTest {
    private static final UUID ALEX_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void setDeathsUsesOnlineBukkitPlayerUuidNameAndCommandSenderMessages() {
        Player alex = player("Alex", ALEX_ID);
        Server server = server(alex);
        InMemoryDeathCountStore store = new InMemoryDeathCountStore();
        BukkitDeathGatesCommandExecutor executor = new BukkitDeathGatesCommandExecutor(
                server,
                store,
                () -> ConfigReloadResult.success(config()),
                TranslationsLoader.load(),
                new ChatRenderer(() -> ""));
        List<String> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of("deathgates.admin.set"), messages);

        boolean handled = executor.onCommand(
                sender,
                null,
                "doum",
                new String[] {"setdeaths", "Alex", "5"});

        assertTrue(handled);
        assertEquals(5, store.getDeaths(ALEX_ID));
        assertEquals(Optional.of("Alex"), store.getLatestKnownPlayerName(ALEX_ID));
        assertEquals(List.of("Set Alex deaths to 5."), messages);
    }

    private static Server server(Player player) {
        return proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getPlayerExact" -> "Alex".equals(arguments[0]) ? player : null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Player player(String name, UUID playerId) {
        return proxy(Player.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getName" -> name;
            case "getUniqueId" -> playerId;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static CommandSender sender(Set<String> permissions, List<String> messages) {
        return proxy(CommandSender.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "hasPermission" -> permissions.contains(arguments[0]);
            case "sendMessage" -> {
                messages.add(plainText(arguments[0]));
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private static String plainText(Object message) {
        return message instanceof Component component
                ? PlainTextComponentSerializer.plainText().serialize(component)
                : String.valueOf(message);
    }

    private static DeathGatesConfig config() {
        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            operations.put(
                    operation,
                    new OperationGateConfig(
                            operation,
                            true,
                            0,
                            "deathgates.bypass." + operation.id(),
                            "",
                            Map.of()));
        }
        return new DeathGatesConfig(operations);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        return type.cast(proxy);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }
}
