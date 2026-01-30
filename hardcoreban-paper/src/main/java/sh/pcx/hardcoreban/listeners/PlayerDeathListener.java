package sh.pcx.hardcoreban.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.minimessage.MiniMessage;
import sh.pcx.hardcoreban.HardcoreBanBootstrap;
import sh.pcx.hardcoreban.util.TimeFormatter;

import java.util.logging.Level;

/**
 * Listener for handling player death events and implementing the hardcore ban mechanism.
 */
public class PlayerDeathListener implements Listener {
    private final HardcoreBanBootstrap plugin;
    private final MiniMessage miniMessage;

    /**
     * Creates a new PlayerDeathListener.
     *
     * @param plugin The main plugin instance
     */
    public PlayerDeathListener(HardcoreBanBootstrap plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Handles player death events - bans players who die in hardcore mode.
     * Uses MONITOR priority to run last and only if not cancelled by other plugins.
     *
     * @param event The player death event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        // Log that we're processing a death event
        plugin.log(Level.INFO, "Processing death event for player " + player.getName());

        // Check if we're in the right world
        if (!plugin.getPlugin().getConfig().getBoolean("affect-all-worlds", false)) {
            String hardcoreWorld = plugin.getPlugin().getConfig().getString("hardcore-world", "world");
            if (!player.getWorld().getName().equals(hardcoreWorld)) {
                plugin.log(Level.INFO, "Player " + player.getName() + " died in world " + player.getWorld().getName() +
                        " which is not the hardcore world (" + hardcoreWorld + "). Ignoring.");
                return;
            }
        }

        // Check if player has bypass permission
        if (player.hasPermission("hardcoreban.bypass")) {
            plugin.log(Level.INFO, player.getName() + " died but has the bypass permission.");
            return;
        }

        // Calculate ban duration in milliseconds based on the config
        String unit = plugin.getPlugin().getConfig().getString("ban-duration.unit", "hours").toLowerCase();
        int amount = plugin.getPlugin().getConfig().getInt("ban-duration.amount", 24);

        long banDuration;
        if ("minutes".equals(unit)) {
            banDuration = amount * 60 * 1000; // Convert minutes to ms
        } else {
            // Default to hours
            banDuration = amount * 60 * 60 * 1000; // Convert hours to ms
        }

        long expiry = System.currentTimeMillis() + banDuration;
        plugin.log(Level.INFO, "Attempting to ban player " + player.getName() + " until " + new java.util.Date(expiry));

        // Ban the player
        boolean banSuccess = plugin.banPlayer(player.getUniqueId(), expiry);

        if (banSuccess) {
            // Format the ban time for messages
            String formattedBanTime = TimeFormatter.formatBanTime(banDuration, unit);

            // Notify the player about the ban
            String deathMessage = plugin.getPlugin().getConfig().getString("messages.death-ban", "<red>You died in hardcore mode! You are banned for {time}.");
            deathMessage = deathMessage.replace("{time}", formattedBanTime);
            player.sendMessage(miniMessage.deserialize(deathMessage));

            // Set gamemode to spectator if configured to do so
            boolean setSpectatorOnDeath = plugin.getPlugin().getConfig().getBoolean("set-spectator-on-death", true);
            if (setSpectatorOnDeath) {
                // Set to spectator immediately to allow them to see their death location
                player.setGameMode(GameMode.SPECTATOR);

                // Create the kick message
                final String kickMessage = plugin.getPlugin().getConfig().getString("messages.kick-message",
                                "<red>You died in hardcore mode! You are banned for {time}.")
                        .replace("{time}", formattedBanTime);

                // Schedule a task to kick the player after a short delay
                int kickDelayTicks = plugin.getPlugin().getConfig().getInt("kick-delay-ticks", 60);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            // Double check they're still banned before kicking
                            if (plugin.isBanned(player.getUniqueId())) {
                                player.kick(miniMessage.deserialize(kickMessage));
                            } else {
                                plugin.log(Level.WARNING, "Player " + player.getName() +
                                        " was supposed to be banned but isn't. Not kicking.");
                                plugin.resetPlayerGameMode(player);
                            }
                        }
                    }
                }.runTaskLater(plugin.getPlugin(), kickDelayTicks);
            } else {
                // Kick immediately if not using spectator mode
                String kickMessage = plugin.getPlugin().getConfig().getString("messages.kick-message",
                        "<red>You died in hardcore mode! You are banned for {time}.");
                kickMessage = kickMessage.replace("{time}", formattedBanTime);
                player.kick(miniMessage.deserialize(kickMessage));
            }
        } else {
            plugin.log(Level.SEVERE, "Failed to ban player " + player.getName() + " after death!");
        }
    }
}