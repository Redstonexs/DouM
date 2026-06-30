package io.github.doum.deathgates.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.death.InMemoryDeathCountStore;
import io.github.doum.deathgates.gate.GateDecision;
import io.github.doum.deathgates.gate.GateEvaluator;
import io.github.doum.deathgates.i18n.Language;
import io.github.doum.deathgates.i18n.Translations;
import io.github.doum.deathgates.i18n.TranslationsLoader;
import io.github.doum.deathgates.message.ChatRenderer;
import io.github.doum.deathgates.model.OperationType;
import io.github.doum.deathgates.model.TargetKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class ListenerMappingTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final TargetKey DIAMOND_ORE = TargetKey.parse("material:minecraft:diamond_ore");
    private static final TargetKey STONE = TargetKey.parse("material:minecraft:stone");
    private static final TargetKey OAK_PLANKS_RECIPE = TargetKey.parse("recipe:minecraft:oak_planks");
    private static final TargetKey OAK_PLANKS_RESULT = TargetKey.parse("result:minecraft:oak_planks");
    private static final Translations TRANSLATIONS = TranslationsLoader.load();
    private static final ChatRenderer RENDERER = new ChatRenderer(() -> "");
    // Stub resolver: avoids the server-only translation lookup and yields a stable display name.
    private static final Function<Material, Component> NAMER =
            material -> Component.text(material.getKey().getKey());

    @Test
    void blockBreakMaterialMapsToMaterialTargetKey() {
        InMemoryDeathCountStore store = storeWithDeaths(1);
        BlockGateListener listener = blockListener(store, config(operation(
                OperationType.BLOCK_BREAK,
                0,
                Map.of(DIAMOND_ORE, 2))));
        FakeCancellable cancellable = new FakeCancellable();
        List<Component> messages = new ArrayList<>();

        GateDecision decision = listener.handleBlockBreak(
                Material.DIAMOND_ORE,
                player(permission -> false),
                cancellable::cancel,
                messages::add);

        assertFalse(decision.allowed());
        assertEquals("material:minecraft:diamond_ore", decision.denialContext().get("target"));
        assertEquals(DIAMOND_ORE, decision.matchedTargetKey().orElseThrow());
        assertTrue(cancellable.cancelled());
        assertEquals(List.of("Denied breaking blocks diamond_ore 2 1"), plain(messages));
    }

    @Test
    void blockPlaceMaterialMapsToMaterialTargetKey() {
        InMemoryDeathCountStore store = storeWithDeaths(0);
        BlockGateListener listener = blockListener(store, config(operation(
                OperationType.BLOCK_PLACE,
                0,
                Map.of(STONE, 1))));
        FakeCancellable cancellable = new FakeCancellable();
        List<Component> messages = new ArrayList<>();

        GateDecision decision = listener.handleBlockPlace(
                Material.STONE,
                player(permission -> false),
                cancellable::cancel,
                messages::add);

        assertFalse(decision.allowed());
        assertEquals("material:minecraft:stone", decision.denialContext().get("target"));
        assertEquals(STONE, decision.matchedTargetKey().orElseThrow());
        assertTrue(cancellable.cancelled());
        assertEquals(List.of("Denied placing blocks stone 1 0"), plain(messages));
    }

    @Test
    void craftTargetsUseRecipeKeyBeforeResultMaterialKey() {
        List<TargetKey> targets = CraftGateListener.craftTargets(
                craftRecipe("minecraft", "oak_planks", Material.OAK_PLANKS));

        assertEquals(List.of(OAK_PLANKS_RECIPE, OAK_PLANKS_RESULT), targets);
    }

    @Test
    void craftDeniedPathCancelsAndSendsOneMessage() {
        InMemoryDeathCountStore store = storeWithDeaths(2);
        CraftGateListener listener = craftListener(store, config(operation(
                OperationType.CRAFT_ITEM,
                0,
                Map.of(
                        OAK_PLANKS_RECIPE, 3,
                        OAK_PLANKS_RESULT, 1))));
        FakeCancellable cancellable = new FakeCancellable();
        List<Component> messages = new ArrayList<>();

        GateDecision decision = listener.handleCraftItem(
                craftRecipe("minecraft", "oak_planks", Material.OAK_PLANKS),
                player(permission -> false),
                cancellable::cancel,
                messages::add);

        assertFalse(decision.allowed());
        assertEquals(OAK_PLANKS_RECIPE, decision.matchedTargetKey().orElseThrow());
        assertTrue(cancellable.cancelled());
        // The denial names the crafted result item, not the recipe key that matched.
        assertEquals(List.of("Denied crafting items oak_planks 3 2"), plain(messages));
    }

    @Test
    void blankDenyMessageUsesPlayerLanguageTranslation() {
        InMemoryDeathCountStore store = storeWithDeaths(0);
        BlockGateListener listener = blockListener(store, config(new OperationGateConfig(
                OperationType.BLOCK_BREAK, true, 1, "deathgates.bypass.block-break", "", Map.of())));
        FakeCancellable cancellable = new FakeCancellable();
        List<Component> messages = new ArrayList<>();

        GateDecision decision = listener.handleBlockBreak(
                Material.STONE,
                new GatePlayer(PLAYER_ID, "Tester", Language.CHINESE, permission -> false),
                cancellable::cancel,
                messages::add);

        assertFalse(decision.allowed());
        assertTrue(cancellable.cancelled());
        assertEquals(1, messages.size());
        assertTrue(plain(messages.get(0)).contains("死亡"));
    }

    @Test
    void allowedPathDoesNotCancelOrSendMessage() {
        InMemoryDeathCountStore store = storeWithDeaths(2);
        BlockGateListener listener = blockListener(store, config(operation(
                OperationType.BLOCK_BREAK,
                1,
                Map.of())));
        FakeCancellable cancellable = new FakeCancellable();
        List<Component> messages = new ArrayList<>();

        GateDecision decision = listener.handleBlockBreak(
                Material.DIRT,
                player(permission -> false),
                cancellable::cancel,
                messages::add);

        assertTrue(decision.allowed());
        assertFalse(cancellable.cancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void wildcardBypassAllowsBeforeThreshold() {
        InMemoryDeathCountStore store = storeWithDeaths(0);
        BlockGateListener listener = blockListener(store, config(operation(
                OperationType.BLOCK_BREAK,
                5,
                Map.of())));
        FakeCancellable cancellable = new FakeCancellable();
        List<Component> messages = new ArrayList<>();

        GateDecision decision = listener.handleBlockBreak(
                Material.DEEPSLATE,
                player("deathgates.bypass.*"::equals),
                cancellable::cancel,
                messages::add);

        assertTrue(decision.allowed());
        assertEquals(0, decision.requiredDeaths());
        assertFalse(cancellable.cancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void craftTargetsSkipMissingRecipeKeyAndAirResult() {
        List<TargetKey> targets = CraftGateListener.craftTargets(craftRecipe(null, null, Material.AIR));

        assertTrue(targets.isEmpty());
    }

    private static BlockGateListener blockListener(InMemoryDeathCountStore store, DeathGatesConfig config) {
        return new BlockGateListener(() -> config, store, new GateEvaluator(), TRANSLATIONS, RENDERER, NAMER);
    }

    private static CraftGateListener craftListener(InMemoryDeathCountStore store, DeathGatesConfig config) {
        return new CraftGateListener(() -> config, store, new GateEvaluator(), TRANSLATIONS, RENDERER, NAMER);
    }

    private static InMemoryDeathCountStore storeWithDeaths(int deaths) {
        InMemoryDeathCountStore store = new InMemoryDeathCountStore();
        store.setDeaths(PLAYER_ID, deaths, "Tester");
        return store;
    }

    private static GatePlayer player(Predicate<String> permissions) {
        return new GatePlayer(PLAYER_ID, "Tester", Language.ENGLISH, permissions);
    }

    private static CraftGateListener.CraftRecipeView craftRecipe(
            String namespace,
            String recipeKey,
            Material resultType) {
        NamespacedKey key = namespace == null || recipeKey == null
                ? null
                : new NamespacedKey(namespace, recipeKey);
        return new CraftGateListener.CraftRecipeView(key, resultType);
    }

    private static DeathGatesConfig config(OperationGateConfig... overrides) {
        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            operations.put(operation, operation(operation, 0, Map.of()));
        }
        for (OperationGateConfig override : overrides) {
            operations.put(override.operation(), override);
        }
        return new DeathGatesConfig(operations);
    }

    private static OperationGateConfig operation(
            OperationType operation,
            int defaultRequiredDeaths,
            Map<TargetKey, Integer> targets) {
        return new OperationGateConfig(
                operation,
                true,
                defaultRequiredDeaths,
                "deathgates.bypass." + operation.id(),
                "Denied {operation} {target} {required} {actual}",
                targets);
    }

    private static List<String> plain(List<Component> messages) {
        List<String> rendered = new ArrayList<>();
        for (Component message : messages) {
            rendered.add(plain(message));
        }
        return rendered;
    }

    private static String plain(Component message) {
        return PlainTextComponentSerializer.plainText().serialize(message);
    }

    private static final class FakeCancellable {
        private boolean cancelled;

        void cancel() {
            cancelled = true;
        }

        boolean cancelled() {
            return cancelled;
        }
    }
}
