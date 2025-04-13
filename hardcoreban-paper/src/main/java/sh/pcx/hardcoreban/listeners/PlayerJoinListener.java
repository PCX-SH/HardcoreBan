package sh.pcx.hardcoreban.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.minimessage.MiniMessage;
import sh.pcx.hardcoreban.HardcoreBanPlugin;
import sh.pcx.hardcoreban.util.TimeFormatter;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for handling player join events and enforcing bans.
 */
public class PlayerJoinListener implements Listener {
    private final HardcoreBanPlugin plugin;
    private final MiniMessage miniMessage;

    /**
     * Creates a new PlayerJoinListener.
     *
     * @param plugin The main plugin instance
     */
    public PlayerJoinListener(HardcoreBanPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Handles player join events. Enforces bans and resets player gamemodes as needed.
     *
     * @param event The player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.log(Level.INFO, "Player " + player.getName() + " joined. Checking ban status...");

        // Check if player is banned
        boolean banned = plugin.isBanned(uuid);
        plugin.log(Level.INFO, "Ban check for " + player.getName() + ": " + (banned ? "BANNED" : "NOT BANNED"));

        if (banned) {
            long timeLeft = plugin.getTimeLeft(uuid);
            plugin.log(Level.INFO, "Ban time left for " + player.getName() + ": " + timeLeft + "ms");

            // If still banned, kick them
            if (timeLeft > 0) {
                // Create the kick message outside the inner class so it's effectively final
                final String kickMessage = plugin.getConfig().getString("messages.join-banned",
                                "<red>You are still banned from hardcore mode for {time}.")
                        .replace("{time}", TimeFormatter.formatTime(timeLeft));

                // Give them a moment to see the message before kicking
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            player.kick(miniMessage.deserialize(kickMessage));
                        }
                    }
                }.runTaskLater(plugin, 5L);
            } else {
                // Ban expired, remove it and reset gamemode
                plugin.log(Level.INFO, "Ban for " + player.getName() + " has expired. Removing ban and resetting gamemode.");
                plugin.removeBan(uuid);
                plugin.resetPlayerGameMode(player);
            }
        } else {
            // Not banned, but check if they're in spectator mode and reset them if needed
            // This handles the case where they might have been in spectator mode when banned
            if (player.getGameMode() == GameMode.SPECTATOR) {
                // Check if we're in the right world
                if (plugin.getConfig().getBoolean("affect-all-worlds", false) ||
                        player.getWorld().getName().equals(plugin.getConfig().getString("hardcore-world", "world"))) {

                    plugin.log(Level.INFO, "Player " + player.getName() + " is in spectator mode but not banned. Resetting gamemode.");
                    plugin.resetPlayerGameMode(player);
                }
            }
        }
    }
}