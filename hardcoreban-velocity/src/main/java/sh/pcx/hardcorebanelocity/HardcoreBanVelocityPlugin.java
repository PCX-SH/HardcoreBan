package sh.pcx.hardcorebanelocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import sh.pcx.hardcorebanelocity.database.DatabaseManager;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "hardcoreban-velocity",
        name = "HardcoreBan-Velocity",
        version = "1.0.0",
        description = "Prevents banned players from joining the hardcore server",
        authors = {"Your Name"}
)
public class HardcoreBanVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, Object> config;
    private Map<UUID, Long> cachedBans = new ConcurrentHashMap<>();
    private Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private ChannelIdentifier channelIdentifier;
    private MessageSender messageSender;
    private DatabaseManager databaseManager;
    private Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();
    private String hardcoreServerName;
    private File sharedBanFile;
    private long lastSharedFileCheck = 0;

    @Inject
    public HardcoreBanVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            // Create the data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                try {
                    Files.createDirectories(dataDirectory);
                } catch (Exception e) {
                    logger.error("Failed to create data directory", e);
                }
            }

            // Load configuration
            loadConfig();

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
            channelIdentifier = MinecraftChannelIdentifier.from("hardcoreban:channel");
            server.getChannelRegistrar().register(channelIdentifier);

            // Register plugin message listener
            server.getEventManager().register(this, new PluginMessageListener(this));

            // Register server connect listener
            server.getEventManager().register(this, new ServerConnectListener(this));

            // Register command
            registerCommands();

            // Only try to preload bans if database connection was successful
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

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        // Disconnect from database
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        logger.info("HardcoreBan Velocity plugin shutting down");
    }

    private void loadConfig() {
        try {
            Path configPath = dataDirectory.resolve("config.yml");

            // Create default config if it doesn't exist
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        // Create empty config with defaults
                        Map<String, Object> defaultConfig = new HashMap<>();
                        defaultConfig.put("hardcore-server", "world");
                        defaultConfig.put("check-interval", 10);

                        Map<String, String> messages = new HashMap<>();
                        messages.put("title-banned", "<red>Hardcore Mode Banned");
                        messages.put("subtitle-banned", "<yellow>Ban expires in {time}");
                        messages.put("chat-banned", "<red>You cannot connect to the hardcore server for {time}.");

                        defaultConfig.put("messages", messages);

                        Files.writeString(configPath, new Yaml().dump(defaultConfig));
                    }
                    logger.info("Created default configuration file");
                } catch (Exception e) {
                    logger.error("Failed to create default config", e);
                }
            }

            // Load the config
            try (FileReader reader = new FileReader(configPath.toFile())) {
                config = new Yaml().load(reader);

                // Get the hardcore server name
                hardcoreServerName = getConfigString("hardcore-server", "world");
                logger.info("Hardcore server name configured as: {}", hardcoreServerName);
            }
        } catch (Exception e) {
            logger.error("Failed to load config", e);

            // Create default config in memory
            config = new HashMap<>();
            config.put("hardcore-server", "world");
            config.put("check-interval", 10);

            Map<String, String> messages = new HashMap<>();
            messages.put("title-banned", "<red>Hardcore Mode Banned");
            messages.put("subtitle-banned", "<yellow>Ban expires in {time}");
            messages.put("chat-banned", "<red>You cannot connect to the hardcore server for {time}.");

            config.put("messages", messages);
        }
    }

    private void setupSharedBanFile() {
        // First try to locate it in the server root directory
        Path serverDir = dataDirectory.getParent().getParent();
        Path paperPluginsDir = serverDir.resolve("plugins/HardcoreBan");

        sharedBanFile = paperPluginsDir.resolve("hardcoreban_shared.yml").toFile();

        if (!sharedBanFile.exists()) {
            // Try alternate locations as fallback
            logger.warn("Shared ban file not found at expected location: {}", sharedBanFile.getAbsolutePath());

            // Try in our own plugin directory as a fallback
            sharedBanFile = dataDirectory.resolve("hardcoreban_shared.yml").toFile();

            if (!sharedBanFile.exists()) {
                logger.warn("Shared ban file not found - connection to Paper server not established");
                logger.warn("Bans won't be enforced until connection is established");
            }
        }

        logger.info("Using shared ban file at: {}", sharedBanFile.getAbsolutePath());

        // Do an initial check of the shared file
        checkSharedFile();
    }

    private void registerCommands() {
        CommandManager commandManager = server.getCommandManager();

        CommandMeta commandMeta = commandManager.metaBuilder("vhardcoreban")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new HardcoreBanCommand(this));
    }

    private void startCheckTask() {
        int checkInterval = getConfigInt("check-interval", 10); // Default to 10 seconds
        server.getScheduler().buildTask(this, this::checkSharedFile)
                .repeat(checkInterval, TimeUnit.SECONDS)
                .schedule();
    }

    public void checkSharedFile() {
        if (sharedBanFile == null || !sharedBanFile.exists()) {
            return;
        }

        // Only check if the file has been modified since we last checked
        if (sharedBanFile.lastModified() <= lastSharedFileCheck) {
            return;
        }

        try (FileReader reader = new FileReader(sharedBanFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);

            // Update our cache
            Map<UUID, Long> newBans = new ConcurrentHashMap<>();
            Map<UUID, String> newNames = new ConcurrentHashMap<>();

            if (data != null) {
                // Read ban data
                if (data.containsKey("bans")) {
                    Map<String, Object> bans = (Map<String, Object>) data.get("bans");
                    for (Map.Entry<String, Object> entry : bans.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            long expiry = ((Number) entry.getValue()).longValue();

                            // Only add if not expired
                            if (expiry > System.currentTimeMillis()) {
                                newBans.put(uuid, expiry);
                            }
                        } catch (Exception e) {
                            logger.warn("Invalid ban entry in shared file: {}", entry.getKey());
                        }
                    }
                }

                // Read name data
                if (data.containsKey("names")) {
                    Map<String, Object> names = (Map<String, Object>) data.get("names");
                    for (Map.Entry<String, Object> entry : names.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            String name = String.valueOf(entry.getValue());
                            newNames.put(uuid, name);
                        } catch (Exception e) {
                            logger.warn("Invalid name entry in shared file: {}", entry.getKey());
                        }
                    }
                }
            }

            // Check if there were changes
            if (!newBans.equals(cachedBans)) {
                int added = 0, removed = 0;

                // Find new bans
                for (UUID uuid : newBans.keySet()) {
                    if (!cachedBans.containsKey(uuid)) {
                        added++;
                    }
                }

                // Find removed bans
                for (UUID uuid : cachedBans.keySet()) {
                    if (!newBans.containsKey(uuid)) {
                        removed++;
                    }
                }

                logger.info("Updated ban cache from shared file: {} added, {} removed", added, removed);

                // Update our cache
                cachedBans = newBans;
                playerNames = newNames;
            }

            // Update last check time
            lastSharedFileCheck = sharedBanFile.lastModified();

        } catch (Exception e) {
            logger.error("Error reading shared ban file", e);
        }
    }

    public boolean isBanned(UUID uuid) {
        return databaseManager.isBanned(uuid);
    }

    public long getTimeLeft(UUID uuid) {
        return databaseManager.getTimeLeft(uuid);
    }

    public Map<UUID, Long> getBannedPlayers() {
        return databaseManager.getAllBans();
    }

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

    public void setPlayerName(UUID uuid, String name) {
        playerNameCache.put(uuid, name);
    }

    public void refreshBans() {
        try {
            // This will also update the player name cache
            databaseManager.getAllBans();
        } catch (Exception e) {
            logger.error("Error refreshing bans: {}", e.getMessage());
            // Continue plugin initialization even if ban refresh fails
        }
    }

    // Helper method to get strings from config
    public String getConfigString(String path, String defaultValue) {
        if (config == null) {
            return defaultValue;
        }

        try {
            String[] parts = path.split("\\.");
            Object current = config;

            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                    if (current == null) {
                        return defaultValue;
                    }
                } else {
                    return defaultValue;
                }
            }

            return String.valueOf(current);
        } catch (Exception e) {
            logger.warn("Error getting config value for path: {}", path, e);
            return defaultValue;
        }
    }

    // Helper method to get integers from config
    public int getConfigInt(String path, int defaultValue) {
        String strValue = getConfigString(path, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    public ProxyServer getServer() {
        return server;
    }

    public String getHardcoreServerName() {
        return hardcoreServerName;
    }

    public Logger getLogger() {
        return logger;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}