package io.github.doum.deathgates.listener;

import io.github.doum.deathgates.death.DeathRecorder;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class DeathListener implements Listener {
    private final DeathRecorder deathRecorder;

    public DeathListener(DeathRecorder deathRecorder) {
        this.deathRecorder = Objects.requireNonNull(deathRecorder, "deathRecorder");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        deathRecorder.recordDeath(player.getUniqueId(), player.getName());
    }
}
