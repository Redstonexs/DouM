package io.github.doum.deathgates.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.config.HardshipRulesConfig;
import io.github.doum.deathgates.config.OperationGateConfig;
import io.github.doum.deathgates.model.OperationType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

class HardshipRulesListenerTest {
    private static final UUID PLAYER_ID = new UUID(0L, 1L);

    @Test
    void disabledRulesDoNotMutateAdjacentGameplay() {
        HardshipRulesEngine listener = listener(HardshipRulesConfig.disabled(), () -> 0);

        assertFalse(listener.shouldCancelCraft());
        assertEquals(0, listener.craftedToolDamage(Material.WOODEN_PICKAXE));
        assertEquals(200, listener.adjustedCookTime(200));
        assertEquals(1600, listener.adjustedBurnTime(1600));
        assertFalse(listener.shouldBurnSmeltedFood(Material.COOKED_BEEF));
        assertFalse(listener.shouldCancelChestPlacement(Material.CHEST, Material.CHEST));
        assertEquals(0.0, listener.adjustedFallDamage(0.0, 3.0f));
        assertTrue(listener.lowHealthEffects(4.0).isEmpty());
        assertTrue(listener.biomeEffects("minecraft:swamp").isEmpty());
        assertFalse(listener.shouldCancelDeepSleep());
        assertEquals(new FishingWait(100, 600), listener.adjustedFishingWait(100, 600));
        assertFalse(listener.shouldSpawnRetaliation(PLAYER_ID));
    }

    @Test
    void craftingFailureAndDurabilityUseConfiguredPercentages() {
        HardshipRulesEngine listener = listener(enabledRules(), () -> 10);

        assertTrue(listener.shouldCancelCraft());
        assertEquals(12, listener.craftedToolDamage(59, 1));
        assertEquals(0, listener.craftedToolDamage(0, 64));
    }

    @Test
    void furnaceRulesSlowCookShortenFuelAndBurnFoodOnlyWhenRollHits() {
        HardshipRulesEngine listener = listener(enabledRules(), sequence(10, 5));

        assertEquals(600, listener.adjustedCookTime(200));
        assertEquals(1200, listener.adjustedBurnTime(1600));
        assertTrue(listener.shouldBurnSmeltedFood(Material.COOKED_BEEF));
        assertFalse(listener.shouldBurnSmeltedFood(Material.IRON_INGOT));
    }

    @Test
    void storageSleepFallFishingHealthBiomeAndRetaliationRulesAreDeterministic() {
        HardshipRulesEngine listener = listener(enabledRules(), () -> 10);

        assertTrue(listener.shouldCancelChestPlacement(Material.CHEST, Material.CHEST));
        assertFalse(listener.shouldCancelChestPlacement(Material.CHEST, Material.DIRT));
        assertTrue(listener.shouldCancelDeepSleep());
        assertEquals(2.0, listener.adjustedFallDamage(0.0, 3.0f));
        assertEquals(4.0, listener.adjustedFallDamage(4.0, 5.0f));
        assertEquals(
                List.of("minecraft:slowness", "minecraft:blindness"),
                listener.lowHealthEffectKeys(6.0));
        assertEquals(
                List.of("minecraft:poison"),
                listener.biomeEffectKeys("minecraft:swamp"));
        assertEquals(
                List.of("minecraft:weakness"),
                listener.biomeEffectKeys("minecraft:stony_peaks"));
        assertEquals(new FishingWait(175, 1050), listener.adjustedFishingWait(100, 600));
        assertTrue(listener.shouldSpawnRetaliation(PLAYER_ID));
    }

    @Test
    void blockRetaliationCooldownLimitsSpawnBudgetPerPlayer() {
        AtomicLong now = new AtomicLong(1_000L);
        HardshipRulesEngine listener = listener(enabledRules(), () -> 0, now::get);

        assertTrue(listener.shouldSpawnRetaliation(PLAYER_ID));
        assertFalse(listener.shouldSpawnRetaliation(PLAYER_ID));

        now.addAndGet(999L);
        assertFalse(listener.shouldSpawnRetaliation(PLAYER_ID));

        listener.clearRetaliationCooldown(PLAYER_ID);
        assertTrue(listener.shouldSpawnRetaliation(PLAYER_ID));

        now.incrementAndGet();
        assertFalse(listener.shouldSpawnRetaliation(PLAYER_ID));
    }

    @Test
    void bypassHelpersHonorWildcardAndOperationPermissions() {
        HardshipRulesListener listener = hardshipListener(
                enabledRules(),
                () -> 99,
                Map.of(
                        OperationType.BLOCK_BREAK, "custom.break",
                        OperationType.CRAFT_ITEM, "custom.craft"));
        Player wildcard = playerWithPermissions(Set.of("doum.deathnum.bypass.*"));
        Player customBlockBreak = playerWithPermissions(Set.of("custom.break"));
        Player customCraft = playerWithPermissions(Set.of("custom.craft"));
        Player defaultCraft = playerWithPermissions(Set.of("doum.deathnum.bypass.craft-item"));

        assertTrue(HardshipRulesListener.hasWildcardBypass(wildcard));
        assertTrue(listener.hasOperationBypass(wildcard, OperationType.CRAFT_ITEM));
        assertTrue(listener.hasOperationBypass(customBlockBreak, OperationType.BLOCK_BREAK));
        assertTrue(listener.hasOperationBypass(customCraft, OperationType.CRAFT_ITEM));
        assertFalse(listener.hasOperationBypass(defaultCraft, OperationType.CRAFT_ITEM));
        assertFalse(listener.hasOperationBypass(customBlockBreak, OperationType.CRAFT_ITEM));
    }

    @Test
    void doubleChestOpenHonorsConfiguredBlockPlaceAndWildcardBypass() {
        HardshipRulesListener listener = hardshipListener(
                enabledRules(),
                () -> 99,
                Map.of(OperationType.BLOCK_PLACE, "custom.place"));
        Inventory doubleChest = doubleChestInventory();

        InventoryOpenEvent configuredBypass =
                inventoryOpenEvent(playerWithPermissions(Set.of("custom.place")), doubleChest);
        listener.onInventoryOpen(configuredBypass);
        assertFalse(configuredBypass.isCancelled());

        InventoryOpenEvent wildcardBypass =
                inventoryOpenEvent(playerWithPermissions(Set.of("doum.deathnum.bypass.*")), doubleChest);
        listener.onInventoryOpen(wildcardBypass);
        assertFalse(wildcardBypass.isCancelled());

        InventoryOpenEvent notBypassed =
                inventoryOpenEvent(playerWithPermissions(Set.of("doum.deathnum.bypass.block-place")), doubleChest);
        listener.onInventoryOpen(notBypassed);
        assertTrue(notBypassed.isCancelled());
    }

    private static HardshipRulesEngine listener(HardshipRulesConfig hardship, IntSupplier randomPercent) {
        return new HardshipRulesEngine(() -> config(hardship), randomPercent);
    }

    private static HardshipRulesEngine listener(
            HardshipRulesConfig hardship,
            IntSupplier randomPercent,
            LongSupplier currentTimeMillis) {
        return new HardshipRulesEngine(() -> config(hardship), randomPercent, currentTimeMillis);
    }

    private static HardshipRulesListener hardshipListener(
            HardshipRulesConfig hardship,
            IntSupplier randomPercent,
            Map<OperationType, String> bypassPermissions) {
        return new HardshipRulesListener(() -> config(hardship, bypassPermissions), randomPercent);
    }

    private static DeathGatesConfig config(HardshipRulesConfig hardship) {
        return config(hardship, Map.of());
    }

    private static DeathGatesConfig config(
            HardshipRulesConfig hardship,
            Map<OperationType, String> bypassPermissions) {
        EnumMap<OperationType, OperationGateConfig> operations = new EnumMap<>(OperationType.class);
        for (OperationType operation : OperationType.values()) {
            operations.put(
                    operation,
                    new OperationGateConfig(
                            operation,
                            true,
                            0,
                            bypassPermissions.getOrDefault(
                                    operation, "doum.deathnum.bypass." + operation.id()),
                            "",
                            Map.of()));
        }
        return new DeathGatesConfig(operations, DeathGatesConfig.DEFAULT_MESSAGE_PREFIX, hardship);
    }

    private static HardshipRulesConfig enabledRules() {
        return new HardshipRulesConfig(
                new HardshipRulesConfig.Crafting(true, 25, 70, 95),
                new HardshipRulesConfig.Furnace(true, 50, 300, 10, 75),
                new HardshipRulesConfig.Storage(true),
                new HardshipRulesConfig.Sleep(true, true, 100),
                new HardshipRulesConfig.Fishing(true, 175),
                new HardshipRulesConfig.Fall(true, 3, 2),
                new HardshipRulesConfig.Health(true, 6, 120, 1),
                new HardshipRulesConfig.Biomes(true, 160, 0),
                new HardshipRulesConfig.BlockRetaliation(true, 40, 20));
    }

    private static Player playerWithPermissions(Set<String> permissions) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("hasPermission".equals(method.getName())
                    && args != null
                    && args.length == 1
                    && args[0] instanceof String permission) {
                return permissions.contains(permission);
            }
            if ("toString".equals(method.getName())) {
                return "test-player";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                handler);
    }

    private static InventoryOpenEvent inventoryOpenEvent(HumanEntity player, Inventory topInventory) {
        InventoryView view = (InventoryView) Proxy.newProxyInstance(
                InventoryView.class.getClassLoader(),
                new Class<?>[] {InventoryView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory", "getInventory" -> topInventory;
                    case "getPlayer" -> player;
                    case "toString" -> "test-inventory-view";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new InventoryOpenEvent(view);
    }

    private static Inventory doubleChestInventory() {
        return (Inventory) Proxy.newProxyInstance(
                DoubleChestInventory.class.getClassLoader(),
                new Class<?>[] {DoubleChestInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "test-double-chest";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static IntSupplier sequence(int... values) {
        return new IntSupplier() {
            private int index;

            @Override
            public int getAsInt() {
                int value = values[Math.min(index, values.length - 1)];
                index++;
                return value;
            }
        };
    }
}
