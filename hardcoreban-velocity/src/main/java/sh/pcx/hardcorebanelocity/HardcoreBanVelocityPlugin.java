package sh.pcx.hardcorebanelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import sh.pcx.hardcorebanelocity.commands.HardcoreBanCommand;
import sh.pcx.hardcorebanelocity.database.DatabaseManager;
import sh.pcx.hardcorebanelocity.listeners.PluginMessageListener;
import sh.pcx.hardcorebanelocity.listeners.ServerConnectListener;
import sh.pcx.hardcorebanelocity.messaging.MessageSender;
import sh.pcx.hardcorebanelocity.util.ConfigManager;
import sh.pcx.hardcorebanelocity.util.TimeFormatter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Main class for the HardcoreBan Velocity plugin.
 * Coordinates all plugin functionality.
 */
@Plugin(
        id = "hardcoreban-velocity",
        name = "HardcoreBan-Velocity",
        version = "1.1.0",
        description = "Prevents banned players from joining the hardcore server",
        authors = {"Reset64"}
)
public class HardcoreBanVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MessageSender messageSender;
    private ChannelIdentifier channelIdentifier;

    // Cache of player names for UUID lookup
    private Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

    /**
     * Creates a new instance of the plugin.
     * Dependency injection is handled by Velocity.
     *
     * @param server The Velocity proxy server
     * @param logger The logger
     * @param dataDirectory The plugin data directory
     */
    @Inject
    public HardcoreBanVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Initializes the plugin when the proxy starts.
     *
     * @param event The proxy initialization event
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            // Initialize configuration
            configManager = new ConfigManager(logger, dataDirectory);

            // Initialize database manager
            databaseManager = new DatabaseManager(this);
            boolean dbConnected = false;

            try {
                dbConnected = databaseManager.connect();
            } catch (Exception e) {
                logger.error("Error connecting to database", e);
            }

            if (!dbConnected) {
                logger.error("Could not connect to database. Plugin functionality will be limited.");
            }

            // Register plugin messaging channel
            setupPluginMessaging();

            // Register event listeners
            registerEventListeners();

            // Register commands
            registerCommands();

            // Initialize message sender for Velocity->Paper communication
            messageSender = new MessageSender(this, logger, channelIdentifier);

            // Start tasks
            startTasks();

            // Preload bans if database connection was successful
            if (dbConnected) {
                try {
                    refreshBans();
                } catch (Exception e) {
                    logger.error("Failed to refresh bans", e);
                }
            }

            logger.info("HardcoreBan Velocity plugin initialized successfully!");
        } catch (Exception e) {
            logger.error("Error initializing plugin", e);
        }
    }

    /**
     * Cleans up when the proxy shuts down.
     *
     * @param event The proxy shutdown event
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        // Disconnect from database
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        logger.info("HardcoreBan Velocity plugin shutting down");
    }

    /**
     * Sets up plugin messaging channels.
     */
    private void setupPluginMessaging() {
        channelIdentifier = MinecraftChannelIdentifier.from("hardcoreban:channel");
        server.getChannelRegistrar().register(channelIdentifier);
    }

    /**
     * Registers event listeners.
     */
    private void registerEventListeners() {
        server.getEventManager().register(this, new PluginMessageListener(this));
        server.getEventManager().register(this, new ServerConnectListener(this));
    }

    /**
     * Registers commands.
     */
    private void registerCommands() {
        CommandManager commandManager = server.getCommandManager();

        CommandMeta commandMeta = commandManager.metaBuilder("vhardcoreban")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new HardcoreBanCommand(this));
    }

    /**
     * Starts scheduled tasks.
     */
    private void startTasks() {
        int checkInterval = configManager.getInt("check-interval", 10); // Default to 10 seconds

        // Refresh bans from database periodically
        server.getScheduler().buildTask(this, this::refreshBans)
                .repeat(checkInterval, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Refreshes ban data from the database.
     */
    public void refreshBans() {
        try {
            databaseManager.getAllBans();
            logger.debug("Refreshed bans from database");
        } catch (Exception e) {
            logger.error("Error refreshing bans: {}", e.getMessage());
        }
    }

    /**
     * Checks if a player is banned.
     *
     * @param uuid The UUID of the player
     * @return true if the player is banned, false otherwise
     */
    public boolean isBanned(UUID uuid) {
        return databaseManager.isBanned(uuid);
    }

    /**
     * Gets the time left on a player's ban.
     *
     * @param uuid The UUID of the player
     * @return The time left in milliseconds, or 0 if not banned
     */
    public long getTimeLeft(UUID uuid) {
        return databaseManager.getTimeLeft(uuid);
    }

    /**
     * Gets all banned players.
     *
     * @return A map of UUID to expiry time
     */
    public Map<UUID, Long> getBannedPlayers() {
        return databaseManager.getAllBans();
    }

    /**
     * Gets a player's name from their UUID, either from cache or online players.
     *
     * @param uuid The UUID of the player
     * @return The player's name, or a string containing their UUID if not found
     */
    public String getPlayerName(UUID uuid) {
        // Check if we have a cached name
        if (playerNameCache.containsKey(uuid)) {
            return playerNameCache.get(uuid);
        }

        // Try to get from an online player
        return server.getPlayer(uuid)
                .map(Player::getUsername)
                .orElse("Unknown (" + uuid.toString() + ")");
    }

    /**
     * Sets a player's name in the cache.
     *
     * @param uuid The UUID of the player
     * @param name The name of the player
     */
    public void setPlayerName(UUID uuid, String name) {
        playerNameCache.put(uuid, name);
    }

    /**
     * Gets the Velocity server instance.
     *
     * @return The server instance
     */
    public ProxyServer getServer() {
        return server;
    }

    /**
     * Gets the plugin logger.
     *
     * @return The logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the configuration manager.
     *
     * @return The configuration manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the database manager.
     *
     * @return The database manager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Gets the message sender.
     *
     * @return The message sender
     */
    public MessageSender getMessageSender() {
        return messageSender;
    }

    /**
     * Gets the MiniMessage instance.
     *
     * @return The MiniMessage instance
     */
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}