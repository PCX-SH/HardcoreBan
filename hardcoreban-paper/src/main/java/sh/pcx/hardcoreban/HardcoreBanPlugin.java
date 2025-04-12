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
    private File banFile;
    private File sharedBanFile;
    private DatabaseManager databaseManager;
    private FileConfiguration banConfig;
    private Map<UUID, Long> bannedPlayers = new HashMap<>();
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

    private void setupBanStorage() {
        banFile = new File(getDataFolder(), "bans.yml");
        if (!banFile.exists()) {
            try {
                banFile.createNewFile();
            } catch (IOException e) {
                log(Level.SEVERE, "Could not create bans.yml file: " + e.getMessage());
            }
        }

        // Set up shared bans file in the plugins folder
        sharedBanFile = new File(getDataFolder(), "hardcoreban_shared.yml");
        if (!sharedBanFile.exists()) {
            try {
                sharedBanFile.createNewFile();
                log(Level.INFO, "Created shared ban file at " + sharedBanFile.getAbsolutePath());
            } catch (IOException e) {
                log(Level.SEVERE, "Could not create shared ban file: " + e.getMessage());
            }
        }

        // Load existing bans
        banConfig = YamlConfiguration.loadConfiguration(banFile);

        if (banConfig.contains("bans")) {
            for (String uuidStr : banConfig.getConfigurationSection("bans").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long expiry = banConfig.getLong("bans." + uuidStr);
                    if (System.currentTimeMillis() < expiry) {
                        bannedPlayers.put(uuid, expiry);
                    }
                } catch (IllegalArgumentException e) {
                    log(Level.WARNING, "Invalid UUID in ban storage: " + uuidStr);
                }
            }
        }

        log(Level.INFO, "Loaded " + bannedPlayers.size() + " active bans from storage.");

        // Immediately update shared storage
        updateSharedStorage();
    }

    private void updateSharedStorage() {
        try {
            YamlConfiguration sharedConfig = new YamlConfiguration();

            // Save all bans to the shared file
            for (Map.Entry<UUID, Long> entry : bannedPlayers.entrySet()) {
                String uuidStr = entry.getKey().toString();
                long expiry = entry.getValue();

                // Store name if available
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    sharedConfig.set("names." + uuidStr, player.getName());
                } else {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    if (name != null) {
                        sharedConfig.set("names." + uuidStr, name);
                    }
                }

                sharedConfig.set("bans." + uuidStr, expiry);
            }

            // Add timestamp
            sharedConfig.set("last_updated", System.currentTimeMillis());

            // Save to file
            sharedConfig.save(sharedBanFile);
            log(Level.FINE, "Updated shared ban storage with " + bannedPlayers.size() + " bans");
        } catch (IOException e) {
            log(Level.SEVERE, "Could not update shared ban file: " + e.getMessage());
        }
    }

    public void saveBanData() {
        banConfig.set("bans", null); // Clear existing bans section
        for (Map.Entry<UUID, Long> entry : bannedPlayers.entrySet()) {
            banConfig.set("bans." + entry.getKey().toString(), entry.getValue());
        }

        try {
            banConfig.save(banFile);
            log(Level.INFO, "Saved " + bannedPlayers.size() + " bans to storage.");

            // Also update the shared file
            updateSharedStorage();
        } catch (IOException e) {
            log(Level.SEVERE, "Could not save bans to file: " + e.getMessage());
        }
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

        // Calculate ban duration in milliseconds
        long banDuration = config.getLong("ban-duration", 24) * 60 * 60 * 1000; // Convert hours to ms
        long expiry = System.currentTimeMillis() + banDuration;
        log(Level.INFO, "Attempting to ban player " + player.getName() + " until " + new java.util.Date(expiry));

        // Direct database operation test
        try {
            boolean directDbSuccess = databaseManager.addBan(
                    player.getUniqueId(),
                    player.getName(),
                    expiry,
                    "Death",
                    System.currentTimeMillis(),
                    "Death in hardcore mode"
            );
            log(Level.INFO, "Direct database ban operation result: " + (directDbSuccess ? "SUCCESS" : "FAILURE"));
        } catch (Exception e) {
            log(Level.SEVERE, "Error during direct database ban: " + e.getMessage());
            e.printStackTrace();
        }

        // Verify the ban was added
        boolean isBannedNow = databaseManager.isBanned(player.getUniqueId());
        log(Level.INFO, "Ban verification check for " + player.getName() + ": " + (isBannedNow ? "BANNED" : "NOT BANNED"));

        // Only proceed with the player messaging and kicking if the ban was successful
        if (isBannedNow) {
            // Notify the player about the ban
            String deathMessage = config.getString("messages.death-ban", "<red>You died in hardcore mode! You are banned for {hours} hours.");
            deathMessage = deathMessage.replace("{hours}", String.valueOf(TimeUnit.MILLISECONDS.toHours(banDuration)));
            player.sendMessage(miniMessage.deserialize(deathMessage));
            log(Level.INFO, "Sent ban message to " + player.getName());

            // Set gamemode to spectator if configured to do so
            boolean setSpectatorOnDeath = config.getBoolean("set-spectator-on-death", true);
            if (setSpectatorOnDeath) {
                // Set to spectator immediately to allow them to see their death location
                player.setGameMode(GameMode.SPECTATOR);
                log(Level.INFO, "Set " + player.getName() + " to SPECTATOR mode");

                // Create the kick message
                final String kickMessage = config.getString("messages.kick-message",
                                "<red>You died in hardcore mode! You are banned for {hours} hours.")
                        .replace("{hours}", String.valueOf(TimeUnit.MILLISECONDS.toHours(banDuration)));

                // Schedule a task to kick the player after a short delay
                int kickDelayTicks = config.getInt("kick-delay-ticks", 60); // 3 seconds by default
                log(Level.INFO, "Scheduled kick for " + player.getName() + " in " + kickDelayTicks + " ticks");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            // Double check they're still banned before kicking
                            boolean stillBanned = databaseManager.isBanned(player.getUniqueId());
                            log(Level.INFO, "Kick task executing for " + player.getName() +
                                    ". Still banned: " + stillBanned);

                            if (stillBanned) {
                                log(Level.INFO, "Kicking " + player.getName() + " due to hardcore ban");
                                player.kick(miniMessage.deserialize(kickMessage));
                            } else {
                                log(Level.WARNING, "Player " + player.getName() +
                                        " was supposed to be banned but isn't. Not kicking.");
                                resetPlayerGameMode(player);
                            }
                        } else {
                            log(Level.INFO, "Player " + player.getName() + " is offline, not kicking");
                        }
                    }
                }.runTaskLater(this, kickDelayTicks);
            } else {
                // Kick immediately if not using spectator mode
                String kickMessage = config.getString("messages.kick-message",
                        "<red>You died in hardcore mode! You are banned for {hours} hours.");
                kickMessage = kickMessage.replace("{hours}", String.valueOf(TimeUnit.MILLISECONDS.toHours(banDuration)));
                log(Level.INFO, "Immediately kicking " + player.getName() + " due to hardcore ban");
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

    private void resetPlayerGameMode(Player player) {
        GameMode gameMode = GameMode.valueOf(config.getString("reset-gamemode", "SURVIVAL").toUpperCase());
        player.setGameMode(gameMode);

        String resetMessage = config.getString("messages.gamemode-reset", "<green>Your hardcore ban has expired. Your gamemode has been set to survival.");
        player.sendMessage(miniMessage.deserialize(resetMessage));

        log(Level.INFO, "Reset gamemode for player " + player.getName() + " to " + gameMode);
    }

    private void sendBanToVelocity(UUID uuid, long expiry) {
        // Try to notify Velocity, but don't worry if it fails
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            log(Level.FINE, "Cannot notify Velocity of ban: no players online");
            // This is fine - Velocity will check the shared file
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
            // This is fine - Velocity will check the shared file
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
            // This is fine - Velocity will check the shared file
            return;
        }

        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CLEAR_ALL");

        player.sendPluginMessage(this, "hardcoreban:channel", out.toByteArray());
        log(Level.FINE, "Notified Velocity of ban clearance");
    }

    private String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        if (hours > 0) {
            return hours + " hours, " + minutes + " minutes";
        } else {
            return minutes + " minutes";
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

    public void log(Level level, String message) {
        Level configLevel = Level.parse(config.getString("log-level", "INFO").toUpperCase());
        if (level.intValue() >= configLevel.intValue()) {
            getLogger().log(level, message);
        }
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

    public boolean checkDatabaseConnection() {
        return databaseManager.connect();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}