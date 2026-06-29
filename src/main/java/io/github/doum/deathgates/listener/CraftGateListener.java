package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.death.DeathCountStore;
import io.github.doum.deathgates.gate.GateDecision;
import io.github.doum.deathgates.gate.GateEvaluator;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    public CraftGateListener(
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
            Consumer<String> messageSink) {
        return GateEventSupport.enforce(
                configSupplier,
                deathCountStore,
                gateEvaluator,
                translations,
                OperationType.CRAFT_ITEM,
                player,
                craftTargets(recipe),
                cancelDenied,
                messageSink);
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
