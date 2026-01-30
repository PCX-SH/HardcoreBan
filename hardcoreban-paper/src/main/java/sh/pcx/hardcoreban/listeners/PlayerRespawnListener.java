package sh.pcx.hardcoreban.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.minimessage.MiniMessage;
import sh.pcx.hardcoreban.HardcoreBanPlugin;
import sh.pcx.hardcoreban.util.TimeFormatter;

/**
 * Listener for handling player respawn events.
 * Ensures banned players in spectator mode are properly kicked.
 */
public class PlayerRespawnListener implements Listener {
    private final HardcoreBanPlugin plugin;
    private final MiniMessage miniMessage;

    /**
     * Creates a new PlayerRespawnListener.
     *
     * @param plugin The main plugin instance
     */
    public PlayerRespawnListener(HardcoreBanPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Handles player respawn events. Ensures banned players in spectator mode are properly kicked.
     * Uses HIGH priority for respawn handling.
     *
     * @param event The player respawn event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Check if we're in the right world
        if (!plugin.getConfig().getBoolean("affect-all-worlds", false)) {
            String hardcoreWorld = plugin.getConfig().getString("hardcore-world", "world");
            if (!player.getWorld().getName().equals(hardcoreWorld)) {
                return;
            }
        }

        // Check if the player had died and is in spectator mode
        if (player.getGameMode() == GameMode.SPECTATOR && plugin.isBanned(player.getUniqueId())) {
            // This should never happen as the player should have been kicked,
            // but just in case they're still here
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        long timeLeft = plugin.getTimeLeft(player.getUniqueId());
                        String kickMessage = plugin.getConfig().getString("messages.join-banned",
                                "<red>You are still banned from hardcore mode for {time}.");
                        kickMessage = kickMessage.replace("{time}", TimeFormatter.formatTime(timeLeft));
                        player.kick(miniMessage.deserialize(kickMessage));
                    }
                }
            }.runTaskLater(plugin, 5L); // Short delay
        }
    }
}