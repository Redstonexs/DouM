package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.gate.GateDecision;
import io.github.doum.deathgates.gate.GateEvaluator;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.message.ChatRenderer;
import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.Recipe;

public final class CraftGateListener implements Listener {
    private final Supplier<DeathGatesConfig> configSupplier;
    private final DeathCountStore deathCountStore;
    private final GateEvaluator gateEvaluator;
    private final Translations translations;
    private final ChatRenderer chatRenderer;
    private final Function<Material, Component> targetNamer;

    public CraftGateListener(
            Supplier<DeathGatesConfig> configSupplier,
            DeathCountStore deathCountStore,
            GateEvaluator gateEvaluator,
            Translations translations,
            ChatRenderer chatRenderer,
            Function<Material, Component> targetNamer) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.deathCountStore = Objects.requireNonNull(deathCountStore, "deathCountStore");
        this.gateEvaluator = Objects.requireNonNull(gateEvaluator, "gateEvaluator");
        this.translations = Objects.requireNonNull(translations, "translations");
        this.chatRenderer = Objects.requireNonNull(chatRenderer, "chatRenderer");
        this.targetNamer = Objects.requireNonNull(targetNamer, "targetNamer");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        HumanEntity whoClicked = event.getWhoClicked();
        if (!(whoClicked instanceof Player player)) {
            return;
        }

        handleCraftItem(
                craftRecipeView(event.getRecipe()),
                new GatePlayer(
                        player.getUniqueId(),
                        player.getName(),
                        Language.fromLocale(player.locale()),
                        player::hasPermission),
                () -> event.setCancelled(true),
                player::sendMessage);
    }

    GateDecision handleCraftItem(
            CraftRecipeView recipe,
            GatePlayer player,
            Runnable cancelDenied,
            Consumer<Component> messageSink) {
        return GateEventSupport.enforce(
                configSupplier,
                deathCountStore,
                gateEvaluator,
                translations,
                chatRenderer,
                OperationType.CRAFT_ITEM,
                player,
                craftTargets(recipe),
                craftTargetName(recipe),
                cancelDenied,
                messageSink);
    }

    private Component craftTargetName(CraftRecipeView recipe) {
        Material result = recipe == null ? null : recipe.resultType();
        if (result == null || result == Material.AIR) {
            return Component.empty();
        }
        return targetNamer.apply(result);
    }

    static CraftRecipeView craftRecipeView(Recipe recipe) {
        return new CraftRecipeView(
                GateEventSupport.recipeKey(recipe),
                GateEventSupport.resultType(recipe));
    }

    static List<TargetKey> craftTargets(CraftRecipeView recipe) {
        List<TargetKey> targets = new ArrayList<>(2);

        if (recipe == null) {
            return List.of();
        }

        NamespacedKey recipeKey = recipe.recipeKey();
        if (recipeKey != null) {
            targets.add(GateEventSupport.recipeTarget(recipeKey));
        }

        Material resultType = recipe.resultType();
        if (resultType != null && resultType != Material.AIR) {
            targets.add(GateEventSupport.resultTarget(resultType));
        }

        return List.copyOf(targets);
    }

    record CraftRecipeView(NamespacedKey recipeKey, Material resultType) {}
}
