package sh.pcx.hardcoreban;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import sh.pcx.hardcoreban.commands.HardcoreBanCommand;
import sh.pcx.hardcoreban.database.DatabaseManager;
import sh.pcx.hardcoreban.listeners.PlayerDeathListener;
import sh.pcx.hardcoreban.listeners.PlayerJoinListener;
import sh.pcx.hardcoreban.listeners.PlayerRespawnListener;
import sh.pcx.hardcoreban.messaging.VelocityMessageListener;
import sh.pcx.hardcoreban.model.Ban;
import sh.pcx.hardcoreban.util.ConfigManager;
import sh.pcx.hardcoreban.util.TimeFormatter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Main plugin class for HardcoreBan Paper implementation.
 * Coordinates all plugin functionality.
 */
public class HardcoreBanPlugin extends JavaPlugin {
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private MiniMessage miniMessage;

    @Override
    public void onEnable() {
        try {
            // Initialize Mini Message
            miniMessage = MiniMessage.miniMessage();

            // Load configuration
            configManager = new ConfigManager(this);

            // Suppress MySQL driver warnings
            try {
                java.util.logging.Logger.getLogger("com.mysql.jdbc.Driver").setLevel(java.util.logging.Level.OFF);
                java.util.logging.Logger.getLogger("com.mysql.cj.jdbc.Driver").setLevel(java.util.logging.Level.OFF);
            } catch (Exception e) {
                // Ignore any errors when trying to configure the logger
            }

            // Initialize database
            databaseManager = new DatabaseManager(this);
            boolean dbConnected = false;

            try {
                dbConnected = databaseManager.connect();
            } catch (Exception e) {
                log(Level.SEVERE, "Error connecting to database: " + e.getMessage());
                e.printStackTrace();
            }

            if (!dbConnected) {
                log(Level.SEVERE, "Could not connect to database. Plugin will be disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Register plugin messaging channels
            setupPluginMessaging();

            // Register event listeners
            registerEventListeners();

            // Register commands
            getCommand("hardcoreban").setExecutor(new HardcoreBanCommand(this));

            // Setup scheduled tasks
            setupScheduledTasks();

            log(Level.INFO, "HardcoreBan has been enabled!");
        } catch (Exception e) {
            log(Level.SEVERE, "Error initializing plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Disconnect from database
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        // Unregister plugin channels
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);

        log(Level.INFO, "HardcoreBan has been disabled!");
    }

    /**
     * Sets up plugin messaging channels for communication with Velocity.
     */
    private void setupPluginMessaging() {
        // Register outgoing plugin channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "hardcoreban:channel");

        // Register incoming plugin channel
        getServer().getMessenger().registerIncomingPluginChannel(this, "hardcoreban:channel",
                new VelocityMessageListener(this));
    }

    /**
     * Registers all event listeners.
     */
    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
    }

    /**
     * Sets up scheduled tasks that run periodically.
     */
    private void setupScheduledTasks() {
        // Setup ban check task
        long checkInterval = getConfig().getLong("check-interval", 60) * 20; // Convert seconds to ticks
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkBans();
                } catch (Exception e) {
                    log(Level.SEVERE, "Error checking bans: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskTimer(this, checkInterval, checkInterval);

        // Setup database heartbeat task (every 10 minutes - 12000 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    databaseManager.keepConnectionAlive();
                } catch (Exception e) {
                    log(Level.WARNING, "Error in database heartbeat: " + e.getMessage());
                }
            }
        }.runTaskTimer(this, 12000, 12000);
    }

    /**
     * Checks all bans, removing expired ones and resetting player gamemodes.
     */
    public void checkBans() {
        try {
            // Clean up expired bans in the database
            databaseManager.cleanupExpiredBans();

            // Get all active bans to check for online players who need gamemode reset
            Map<UUID, Long> bannedPlayers = databaseManager.getAllBans();
            long now = System.currentTimeMillis();

            for (Map.Entry<UUID, Long> entry : bannedPlayers.entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();

                // Check if the ban just expired
                if (now >= expiry) {
                    // If the player is online, reset their gamemode
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        resetPlayerGameMode(player);
                    }
                }
            }
        } catch (Exception e) {
            log(Level.SEVERE, "Error checking bans: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Bans a player for the specified expiry time.
     *
     * @param uuid The UUID of the player to ban
     * @param expiry The time when the ban will expire (in milliseconds)
     * @return true if the ban was applied successfully, false otherwise
     */
    public boolean banPlayer(UUID uuid, long expiry) {
        try {
            Player player = Bukkit.getPlayer(uuid);
            String playerName = player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName == null) playerName = uuid.toString();

            boolean success = databaseManager.addBan(uuid, playerName, expiry);

            if (success) {
                // Notify Velocity of the ban
                sendBanToVelocity(uuid, expiry);

                log(Level.INFO, "Player " + playerName + " (" + uuid + ") has been banned until " + new java.util.Date(expiry));
            } else {
                log(Level.WARNING, "Failed to add ban for player " + playerName + " (" + uuid + ")");
            }

            return success;
        } catch (Exception e) {
            log(Level.SEVERE, "Error banning player " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes a ban for a player.
     *
     * @param uuid The UUID of the player
     */
    public void removeBan(UUID uuid) {
        try {
            boolean removed = databaseManager.removeBan(uuid);

            if (removed) {
                // Also try to notify Velocity of the ban removal
                sendBanRemovalToVelocity(uuid);

                log(Level.INFO, "Ban removed for player " + uuid);
            } else {
                log(Level.FINE, "Attempted to remove ban for player " + uuid + " but they weren't banned");
            }
        } catch (Exception e) {
            log(Level.SEVERE, "Error removing ban for player " + uuid + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears all bans from the database.
     */
    public void clearAllBans() {
        try {
            databaseManager.clearAllBans();

            // Notify Velocity to clear all bans
            sendClearAllBansToVelocity();

            log(Level.INFO, "All bans cleared");
        } catch (Exception e) {
            log(Level.SEVERE, "Error clearing all bans: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if a player is currently banned.
     *
     * @param uuid The UUID of the player
     * @return true if the player is banned, false otherwise
     */
    public boolean isBanned(UUID uuid) {
        try {
            return databaseManager.isBanned(uuid);
        } catch (Exception e) {
            log(Level.SEVERE, "Error checking if player " + uuid + " is banned: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the time left on a player's ban in milliseconds.
     *
     * @param uuid The UUID of the player
     * @return The time left in milliseconds, or 0 if the player isn't banned
     */
    public long getTimeLeft(UUID uuid) {
        try {
            return databaseManager.getTimeLeft(uuid);
        } catch (Exception e) {
            log(Level.SEVERE, "Error getting time left for player " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Gets all currently banned players.
     *
     * @return A map of UUID to expiry time
     */
    public Map<UUID, Long> getBannedPlayers() {
        try {
            return databaseManager.getAllBans();
        } catch (Exception e) {
            log(Level.SEVERE, "Error getting banned players: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * Resets a player's gamemode after a ban expires.
     *
     * @param player The player to reset
     */
    public void resetPlayerGameMode(Player player) {
        // Get the configured gamemode to reset to
        String gameModeStr = getConfig().getString("reset-gamemode", "SURVIVAL").toUpperCase();
        GameMode targetGameMode;

        try {
            targetGameMode = GameMode.valueOf(gameModeStr);
        } catch (IllegalArgumentException e) {
            log(Level.WARNING, "Invalid reset-gamemode in config: " + gameModeStr + ". Defaulting to SURVIVAL.");
            targetGameMode = GameMode.SURVIVAL;
        }

        // Store original gamemode for logging
        GameMode originalGameMode = player.getGameMode();

        // Set the player's gamemode
        player.setGameMode(targetGameMode);

        // Verify that the gamemode change took effect
        if (player.getGameMode() != targetGameMode) {
            log(Level.WARNING, "Failed to reset gamemode for player " + player.getName() +
                    ". Target: " + targetGameMode + ", Actual: " + player.getGameMode());

            // Try again one more time after a short delay
            GameMode finalTargetGameMode = targetGameMode;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.setGameMode(finalTargetGameMode);

                        // Check if it worked this time
                        if (player.getGameMode() != finalTargetGameMode) {
                            log(Level.SEVERE, "Failed to reset gamemode for player " + player.getName() +
                                    " after second attempt. Something is preventing gamemode changes.");
                        } else {
                            log(Level.INFO, "Successfully reset gamemode for player " + player.getName() +
                                    " on second attempt.");
                        }
                    }
                }
            }.runTaskLater(this, 5L); // 5 tick delay
        } else {
            log(Level.INFO, "Reset gamemode for player " + player.getName() +
                    " from " + originalGameMode + " to " + targetGameMode);
        }

        // Send the gamemode reset message
        String resetMessage = getConfig().getString("messages.gamemode-reset",
                "<green>Your hardcore ban has expired. Your gamemode has been set to survival.");
        player.sendMessage(miniMessage.deserialize(resetMessage));
    }

    /**
     * Sends a ban message to Velocity proxy.
     *
     * @param uuid The UUID of the banned player
     * @param expiry The time when the ban expires
     */
    private void sendBanToVelocity(UUID uuid, long expiry) {
        // Try to notify Velocity, but don't worry if it fails
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            log(Level.FINE, "Cannot notify Velocity of ban: no players online");
            return;
        }

        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("BAN");
        out.writeUTF(uuid.toString());
        out.writeLong(expiry);

        player.sendPluginMessage(this, "hardcoreban:channel", out.toByteArray());
        log(Level.FINE, "Notified Velocity of ban for player " + uuid);
    }

    /**
     * Sends a ban removal message to Velocity proxy.
     *
     * @param uuid The UUID of the player to unban
     */
    private void sendBanRemovalToVelocity(UUID uuid) {
        // Try to notify Velocity, but don't worry if it fails
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            log(Level.FINE, "Cannot notify Velocity of ban removal: no players online");
            return;
        }

        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("UNBAN");
        out.writeUTF(uuid.toString());

        player.sendPluginMessage(this, "hardcoreban:channel", out.toByteArray());
        log(Level.FINE, "Notified Velocity of ban removal for player " + uuid);
    }

    /**
     * Sends a message to Velocity to clear all bans.
     */
    private void sendClearAllBansToVelocity() {
        // Try to notify Velocity, but don't worry if it fails
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            log(Level.FINE, "Cannot notify Velocity of ban clearance: no players online");
            return;
        }

        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CLEAR_ALL");

        player.sendPluginMessage(this, "hardcoreban:channel", out.toByteArray());
        log(Level.FINE, "Notified Velocity of ban clearance");
    }

    /**
     * Logs a message with the specified log level.
     * Only logs if the level is at or above the configured log level.
     *
     * @param level The log level
     * @param message The message to log
     */
    public void log(Level level, String message) {
        Level configLevel = Level.parse(getConfig().getString("log-level", "INFO").toUpperCase());
        if (level.intValue() >= configLevel.intValue()) {
            getLogger().log(level, message);
        }
    }

    /**
     * Checks if the database connection is active.
     *
     * @return true if connected, false otherwise
     */
    public boolean checkDatabaseConnection() {
        return databaseManager.connect();
    }

    /**
     * Gets the database manager instance.
     *
     * @return The database manager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Gets the config manager instance.
     *
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the MiniMessage instance.
     *
     * @return The MiniMessage instance
     */
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    /**
     * Executes a raw SQL query. Should only be used for admin commands.
     *
     * @param sql The SQL query to execute
     * @param sender The command sender who will receive the results
     */
    public void executeRawSql(String sql, CommandSender sender) {
        try {
            databaseManager.executeRawSql(sql, sender);
        } catch (Exception e) {
            log(Level.SEVERE, "Error executing raw SQL: " + e.getMessage());
            if (sender != null) {
                sender.sendMessage("§cError executing SQL: " + e.getMessage());
            }
        }
    }
}