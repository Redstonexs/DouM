package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.gate.GateDecision;
import io.github.doum.deathgates.gate.GateEvaluator;
import io.github.doum.deathgates.gate.GateRequest;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.MessageKeys;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.message.ChatRenderer;
import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import io.github.doum.deathgates.model.TargetKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

final class GateEventSupport {
    private static final String WILDCARD_BYPASS_PERMISSION = "doum.deathnum.bypass.*";

    private GateEventSupport() {}

    static GateDecision enforce(
            Supplier<DeathGatesConfig> configSupplier,
            DeathCountStore deathCountStore,
            GateEvaluator gateEvaluator,
            Translations translations,
            ChatRenderer chatRenderer,
            OperationType operation,
            GatePlayer player,
            List<TargetKey> targetKeys,
            Component targetName,
            Runnable cancelDenied,
            Consumer<Component> messageSink) {
        Objects.requireNonNull(configSupplier, "configSupplier");
        Objects.requireNonNull(deathCountStore, "deathCountStore");
        Objects.requireNonNull(gateEvaluator, "gateEvaluator");
        Objects.requireNonNull(translations, "translations");
        Objects.requireNonNull(chatRenderer, "chatRenderer");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(targetKeys, "targetKeys");
        Objects.requireNonNull(cancelDenied, "cancelDenied");
        Objects.requireNonNull(messageSink, "messageSink");

        DeathGatesConfig config = configSupplier.get();
        OperationGateConfig operationConfig = config.operation(operation);
        int deaths = deathCountStore.getDeaths(player.playerId());
        boolean hasBypassPermission = player.hasPermission(operationConfig.bypassPermission())
                || player.hasPermission(WILDCARD_BYPASS_PERMISSION);
        GateDecision decision = gateEvaluator.evaluate(
                config,
                new GateRequest(
                        operation,
                        player.playerId(),
                        deaths,
                        targetKeys,
                        hasBypassPermission));

        if (!decision.allowed()) {
            cancelDenied.run();
            String template = denyTemplate(translations, operationConfig, player.language(), operation);
            messageSink.accept(
                    chatRenderer.renderDeny(template, denyValues(translations, player, decision), targetName));
        }
        return decision;
    }

    static TargetKey materialTarget(Material material) {
        return targetKey(TargetKind.MATERIAL, materialKey(material));
    }

    static TargetKey resultTarget(Material material) {
        return targetKey(TargetKind.RESULT, materialKey(material));
    }

    static TargetKey recipeTarget(NamespacedKey key) {
        return targetKey(TargetKind.RECIPE, key);
    }

    static NamespacedKey recipeKey(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return keyed.getKey();
    }

    static Material resultType(Recipe recipe) {
        if (recipe == null) {
            return null;
        }

        ItemStack result = recipe.getResult();
        return result == null ? null : result.getType();
    }

    private static Map<String, String> denyValues(
            Translations translations, GatePlayer player, GateDecision decision) {
        Map<String, String> values = new HashMap<>();
        values.put("player", player.playerName());
        values.put("operation", translations.get(player.language(), MessageKeys.operation(decision.operation())));
        values.put("required", Integer.toString(decision.requiredDeaths()));
        values.put("actual", Integer.toString(decision.actualDeaths()));
        return values;
    }

    private static String denyTemplate(
            Translations translations,
            OperationGateConfig operationConfig,
            Language language,
            OperationType operation) {
        String configured = operationConfig.denyMessage();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return translations.get(language, MessageKeys.deny(operation));
    }

    private static NamespacedKey materialKey(Material material) {
        Objects.requireNonNull(material, "material");
        return material.getKey();
    }

    private static TargetKey targetKey(TargetKind kind, NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        return new TargetKey(kind, key.getNamespace(), key.getKey());
    }
}
