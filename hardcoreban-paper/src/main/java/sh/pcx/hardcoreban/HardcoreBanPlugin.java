package sh.pcx.hardcoreban;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import sh.pcx.hardcoreban.database.DatabaseManager;

public class HardcoreBanPlugin extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private DatabaseManager databaseManager;
    private MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        try {
            // Register this class as event listener
            getServer().getPluginManager().registerEvents(this, this);

            // Setup plugin messaging channel for communication with Velocity
            getServer().getMessenger().registerOutgoingPluginChannel(this, "hardcoreban:channel");

            // Register incoming plugin channel for Velocity messages
            getServer().getMessenger().registerIncomingPluginChannel(this, "hardcoreban:channel",
                    new VelocityMessageListener(this));

            // Load or create the configuration
            saveDefaultConfig();
            config = getConfig();

            // Suppress MySQL driver warnings
            try {
                java.util.logging.Logger.getLogger("com.mysql.jdbc.Driver").setLevel(java.util.logging.Level.OFF);
                java.util.logging.Logger.getLogger("com.mysql.cj.jdbc.Driver").setLevel(java.util.logging.Level.OFF);
            } catch (Exception e) {
                // Ignore any errors when trying to configure the logger
            }

            // Initialize database manager
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

            // Register commands
            getCommand("hardcoreban").setExecutor(new HardcoreBanCommand(this));

            // Setup checkBans task to run regularly
            long checkInterval = config.getLong("check-interval", 60) * 20; // Convert seconds to ticks
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        // Log that we're processing a death event
        log(Level.INFO, "Processing death event for player " + player.getName());

        // Check if we're in the right world
        if (!config.getBoolean("affect-all-worlds", false)) {
            String hardcoreWorld = config.getString("hardcore-world", "world");
            if (!player.getWorld().getName().equals(hardcoreWorld)) {
                log(Level.INFO, "Player " + player.getName() + " died in world " + player.getWorld().getName() +
                        " which is not the hardcore world (" + hardcoreWorld + "). Ignoring.");
                return;
            }
        }

        // Check if player has bypass permission
        if (player.hasPermission("hardcoreban.bypass")) {
            log(Level.INFO, player.getName() + " died but has the bypass permission.");
            return;
        }

        // Calculate ban duration in milliseconds based on the new config
        String unit = config.getString("ban-duration.unit", "hours").toLowerCase();
        int amount = config.getInt("ban-duration.amount", 24);

        long banDuration;
        if ("minutes".equals(unit)) {
            banDuration = amount * 60 * 1000; // Convert minutes to ms
        } else {
            // Default to hours
            banDuration = amount * 60 * 60 * 1000; // Convert hours to ms
        }

        long expiry = System.currentTimeMillis() + banDuration;
        log(Level.INFO, "Attempting to ban player " + player.getName() + " until " + new java.util.Date(expiry));

        // Ban the player
        boolean banSuccess = banPlayer(player.getUniqueId(), expiry);

        if (banSuccess) {
            // Format the ban time for messages
            String formattedBanTime = formatBanTime(banDuration);

            // Notify the player about the ban
            String deathMessage = config.getString("messages.death-ban", "<red>You died in hardcore mode! You are banned for {time}.");
            deathMessage = deathMessage.replace("{time}", formattedBanTime);
            player.sendMessage(miniMessage.deserialize(deathMessage));

            // Set gamemode to spectator if configured to do so
            boolean setSpectatorOnDeath = config.getBoolean("set-spectator-on-death", true);
            if (setSpectatorOnDeath) {
                // Set to spectator immediately to allow them to see their death location
                player.setGameMode(GameMode.SPECTATOR);

                // Create the kick message
                final String kickMessage = config.getString("messages.kick-message",
                                "<red>You died in hardcore mode! You are banned for {time}.")
                        .replace("{time}", formattedBanTime);

                // Schedule a task to kick the player after a short delay
                int kickDelayTicks = config.getInt("kick-delay-ticks", 60);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            // Double check they're still banned before kicking
                            if (isBanned(player.getUniqueId())) {
                                player.kick(miniMessage.deserialize(kickMessage));
                            } else {
                                log(Level.WARNING, "Player " + player.getName() +
                                        " was supposed to be banned but isn't. Not kicking.");
                                resetPlayerGameMode(player);
                            }
                        }
                    }
                }.runTaskLater(this, kickDelayTicks);
            } else {
                // Kick immediately if not using spectator mode
                String kickMessage = config.getString("messages.kick-message",
                        "<red>You died in hardcore mode! You are banned for {time}.");
                kickMessage = kickMessage.replace("{time}", formattedBanTime);
                player.kick(miniMessage.deserialize(kickMessage));
            }
        } else {
            log(Level.SEVERE, "Failed to ban player " + player.getName() + " after death!");
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Check if we're in the right world
        if (!config.getBoolean("affect-all-worlds", false)) {
            String hardcoreWorld = config.getString("hardcore-world", "world");
            if (!player.getWorld().getName().equals(hardcoreWorld)) {
                return;
            }
        }

        // Check if the player had died and is in spectator mode
        if (player.getGameMode() == GameMode.SPECTATOR && isBanned(player.getUniqueId())) {
            // This should never happen as the player should have been kicked,
            // but just in case they're still here
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        long timeLeft = getTimeLeft(player.getUniqueId());
                        String kickMessage = config.getString("messages.join-banned", "<red>You are still banned from hardcore mode for {time}.");
                        kickMessage = kickMessage.replace("{time}", formatTime(timeLeft));
                        player.kick(miniMessage.deserialize(kickMessage));
                    }
                }
            }.runTaskLater(this, 5L); // Short delay
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        log(Level.INFO, "Player " + player.getName() + " joined. Checking ban status...");

        // Check if player is banned
        boolean banned = isBanned(uuid);
        log(Level.INFO, "Ban check for " + player.getName() + ": " + (banned ? "BANNED" : "NOT BANNED"));

        if (banned) {
            long timeLeft = getTimeLeft(uuid);
            log(Level.INFO, "Ban time left for " + player.getName() + ": " + timeLeft + "ms");

            // If still banned, kick them
            if (timeLeft > 0) {
                // Create the kick message outside the inner class so it's effectively final
                final String kickMessage = config.getString("messages.join-banned", "<red>You are still banned from hardcore mode for {time}.")
                        .replace("{time}", formatTime(timeLeft));

                // Give them a moment to see the message before kicking
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            player.kick(miniMessage.deserialize(kickMessage));
                        }
                    }
                }.runTaskLater(this, 5L);
            } else {
                // Ban expired, remove it and reset gamemode
                log(Level.INFO, "Ban for " + player.getName() + " has expired. Removing ban and resetting gamemode.");
                removeBan(uuid);
                resetPlayerGameMode(player);
            }
        } else {
            // Not banned, but check if they're in spectator mode and reset them if needed
            // This handles the case where they might have been in spectator mode when banned
            if (player.getGameMode() == GameMode.SPECTATOR) {
                // Check if we're in the right world
                if (config.getBoolean("affect-all-worlds", false) ||
                        player.getWorld().getName().equals(config.getString("hardcore-world", "world"))) {

                    log(Level.INFO, "Player " + player.getName() + " is in spectator mode but not banned. Resetting gamemode.");
                    resetPlayerGameMode(player);
                }
            }
        }
    }

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

    public boolean isBanned(UUID uuid) {
        try {
            return databaseManager.isBanned(uuid);
        } catch (Exception e) {
            log(Level.SEVERE, "Error checking if player " + uuid + " is banned: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public long getTimeLeft(UUID uuid) {
        try {
            return databaseManager.getTimeLeft(uuid);
        } catch (Exception e) {
            log(Level.SEVERE, "Error getting time left for player " + uuid + ": " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

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

    public Map<UUID, Long> getBannedPlayers() {
        try {
            return databaseManager.getAllBans();
        } catch (Exception e) {
            log(Level.SEVERE, "Error getting banned players: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private void resetPlayerGameMode(Player player) {
        // Get the configured gamemode to reset to
        String gameModeStr = config.getString("reset-gamemode", "SURVIVAL").toUpperCase();
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
        String resetMessage = config.getString("messages.gamemode-reset",
                "<green>Your hardcore ban has expired. Your gamemode has been set to survival.");
        player.sendMessage(miniMessage.deserialize(resetMessage));
    }

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

    // New method to format ban time based on configuration
    public String formatBanTime(long durationMillis) {
        String unit = config.getString("ban-duration.unit", "hours").toLowerCase();

        if ("minutes".equals(unit)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis);
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else {
            // Default to hours
            long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
            return hours + " hour" + (hours == 1 ? "" : "s");
        }
    }

    // Updated format time method for better display
    private String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder time = new StringBuilder();

        if (hours > 0) {
            time.append(hours).append(" hour").append(hours == 1 ? "" : "s");
            if (minutes > 0) {
                time.append(", ");
            }
        }

        if (minutes > 0 || hours == 0) {
            time.append(minutes).append(" minute").append(minutes == 1 ? "" : "s");
        }

        return time.toString();
    }

    public void log(Level level, String message) {
        Level configLevel = Level.parse(config.getString("log-level", "INFO").toUpperCase());
        if (level.intValue() >= configLevel.intValue()) {
            getLogger().log(level, message);
        }
    }

    public boolean checkDatabaseConnection() {
        return databaseManager.connect();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

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