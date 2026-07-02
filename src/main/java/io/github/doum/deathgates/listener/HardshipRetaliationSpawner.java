package io.github.doum.deathgates.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

final class HardshipRetaliationSpawner {
    void spawn(Block block) {
        Material material = block.getType();
        block.getWorld().spawn(
                block.getLocation().add(0.5, 0.0, 0.5),
                Zombie.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM,
                true,
                zombie -> decorate(zombie, material));
    }

    private void decorate(Zombie zombie, Material material) {
        zombie.setBaby();
        zombie.customName(Component.text(material.getKey().asString()));
        zombie.setCustomNameVisible(false);
        if (material.isItem()) {
            zombie.getEquipment().setHelmet(new ItemStack(material), true);
            zombie.getEquipment().setHelmetDropChance(0.0f);
        }
    }
}
