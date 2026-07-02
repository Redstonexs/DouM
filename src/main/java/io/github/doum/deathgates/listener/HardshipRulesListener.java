package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.config.DeathGatesConfig;
import io.github.doum.deathgates.model.OperationType;
import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;

public final class HardshipRulesListener implements Listener {
    private static final String WILDCARD_BYPASS_PERMISSION = "doum.deathnum.bypass.*";
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final Supplier<DeathGatesConfig> configSupplier;
    private final HardshipRulesEngine engine;
    private final HardshipFallTracker fallTracker;
    private final HardshipRetaliationSpawner retaliationSpawner;
    private final HardshipSleepRespawnTracker sleepRespawnTracker;

    public HardshipRulesListener(Supplier<DeathGatesConfig> configSupplier) {
        this(configSupplier, () -> ThreadLocalRandom.current().nextInt(100));
    }

    HardshipRulesListener(Supplier<DeathGatesConfig> configSupplier, IntSupplier randomPercent) {
        this.configSupplier = configSupplier;
        this.engine = new HardshipRulesEngine(configSupplier, randomPercent);
        this.fallTracker = new HardshipFallTracker(engine);
        this.retaliationSpawner = new HardshipRetaliationSpawner();
        this.sleepRespawnTracker = new HardshipSleepRespawnTracker(engine);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        HumanEntity whoClicked = event.getWhoClicked();
        if (whoClicked instanceof Player player && hasOperationBypass(player, OperationType.CRAFT_ITEM)) {
            return;
        }
        if (engine.shouldCancelCraft()) {
            event.setCancelled(true);
            return;
        }

        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        ItemStack damaged = withCraftedToolDamage(result);
        if (damaged != result) {
            event.getInventory().setResult(damaged);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        event.setTotalCookTime(engine.adjustedCookTime(event.getTotalCookTime()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        event.setBurnTime(engine.adjustedBurnTime(event.getBurnTime()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (engine.shouldBurnSmeltedFood(event.getResult().getType())) {
            event.setResult(ItemStack.empty());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (hasOperationBypass(event.getPlayer(), OperationType.BLOCK_PLACE)) {
            return;
        }
        if (event.getBlockPlaced().getType() != Material.CHEST
                && event.getBlockPlaced().getType() != Material.TRAPPED_CHEST) {
            return;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            if (engine.shouldCancelChestPlacement(
                    event.getBlockPlaced().getType(), event.getBlockPlaced().getRelative(face).getType())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (shouldCancelInventoryOpen(event.getPlayer(), event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.enterAction().canSetSpawn().success() && !hasWildcardBypass(event.getPlayer())) {
            sleepRespawnTracker.recordBeforeSleep(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeepSleep(PlayerDeepSleepEvent event) {
        if (hasWildcardBypass(event.getPlayer())) {
            return;
        }
        sleepRespawnTracker.recordFullSleepReached(event.getPlayer());
        if (engine.shouldCancelDeepSleep()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        if (hasWildcardBypass(event.getPlayer())) {
            sleepRespawnTracker.clear(event.getPlayer());
            return;
        }
        sleepRespawnTracker.restoreIfNeeded(event.getPlayer(), event.getPlayer().getSleepTicks());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        fallTracker.clear(playerId);
        engine.clearRetaliationCooldown(playerId);
        sleepRespawnTracker.clear(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        fallTracker.clear(playerId);
        engine.clearRetaliationCooldown(playerId);
        sleepRespawnTracker.clear(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (hasWildcardBypass(event.getPlayer())) {
            return;
        }
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        FishingWait wait = engine.adjustedFishingWait(event.getHook().getMinWaitTime(), event.getHook().getMaxWaitTime());
        event.getHook().setWaitTime(wait.minTicks(), wait.maxTicks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (hasWildcardBypass(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(engine.adjustedFallDamage(event.getDamage(), player.getFallDistance()));
        }

        double healthAfterDamage = Math.max(0.0, player.getHealth() - event.getFinalDamage());
        for (PotionEffect effect : engine.lowHealthEffects(healthAfterDamage)) {
            player.addPotionEffect(effect);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (hasWildcardBypass(player)) {
            return;
        }
        applyLandingDamage(event);
        if (event.hasChangedBlock()) {
            Block block = event.getTo().getBlock();
            for (PotionEffect effect : engine.biomeEffects(block.getBiome().getKey().asString())) {
                player.addPotionEffect(effect);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (hasOperationBypass(event.getPlayer(), OperationType.BLOCK_BREAK)) {
            return;
        }
        if (engine.shouldSpawnRetaliation(event.getPlayer().getUniqueId())) {
            retaliationSpawner.spawn(event.getBlock());
        }
    }

    private ItemStack withCraftedToolDamage(ItemStack result) {
        int damage = engine.craftedToolDamage(result.getType());
        if (damage <= 0) {
            return result;
        }
        ItemStack copy = result.clone();
        copy.editMeta(Damageable.class, meta -> meta.setDamage(damage));
        return copy;
    }

    private void applyLandingDamage(PlayerMoveEvent event) {
        Location to = event.getTo();
        Block feet = to.getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        double damage = fallTracker.landingDamage(
                event.getPlayer().getUniqueId(),
                event.getFrom().getY(),
                to.getY(),
                below.getType().isSolid(),
                feet.isLiquid() || below.isLiquid());
        if (damage > 0.0) {
            event.getPlayer().damage(damage, fallDamageSource());
        }
    }

    private static DamageSource fallDamageSource() {
        return DamageSource.builder(DamageType.FALL).build();
    }

    boolean shouldCancelInventoryOpen(HumanEntity viewer, Inventory inventory) {
        if (viewer instanceof Player player && hasOperationBypass(player, OperationType.BLOCK_PLACE)) {
            return false;
        }
        return engine.preventDoubleChests() && inventory instanceof DoubleChestInventory;
    }

    boolean hasOperationBypass(Player player, OperationType operation) {
        String bypassPermission = configSupplier.get().operation(operation).bypassPermission();
        return hasWildcardBypass(player) || player.hasPermission(bypassPermission);
    }

    static boolean hasWildcardBypass(Player player) {
        return player.hasPermission(WILDCARD_BYPASS_PERMISSION);
    }
}
