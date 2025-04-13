package sh.pcx.hardcorebanelocity.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import sh.pcx.hardcorebanelocity.HardcoreBanVelocityPlugin;
import sh.pcx.hardcorebanelocity.util.ConfigManager;

/**
 * Manages database operations for the HardcoreBan Velocity plugin.
 * Handles connections and ban data retrieval.
 */
public class DatabaseManager {
    private final HardcoreBanVelocityPlugin plugin;
    private final Logger logger;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;
    private long lastConnectionAttempt = 0;
    private static final long CONNECTION_RETRY_DELAY = 10000; // 10 seconds in milliseconds

    /**
     * Creates a new DatabaseManager instance.
     *
     * @param plugin The main plugin instance
     */
    public DatabaseManager(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Get the ConfigManager from the plugin
        ConfigManager configManager = plugin.getConfigManager();

        // Load database configuration from config
        this.host = configManager.getString("database.host", "localhost");
        this.port = configManager.getInt("database.port", 3306);
        this.database = configManager.getString("database.database", "minecraft");
        this.username = configManager.getString("database.username", "root");
        this.password = configManager.getString("database.password", "");
    }

    /**
     * Connects to the database.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean connect() {
        // Prevent connection attempts from happening too frequently
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastConnectionAttempt < CONNECTION_RETRY_DELAY) {
            if (connection != null) {
                try {
                    // Just do a quick check if we recently tried connecting
                    if (!connection.isClosed()) {
                        return true;
                    }
                } catch (SQLException e) {
                    // Connection is invalid, continue with reconnect
                    logger.debug("Connection invalid, will attempt reconnect");
                }
            }
        }

        lastConnectionAttempt = currentTime;

        try {
            // Check if current connection is still valid
            if (connection != null) {
                try {
                    // Test if connection is valid with a 5-second timeout
                    if (!connection.isClosed() && connection.isValid(5)) {
                        return true;
                    }
                    // Connection exists but is invalid, close it
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // Ignore errors when closing invalid connection
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking connection validity: {}", e.getMessage());
                    // Try to close the connection anyway
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // Ignore errors when closing invalid connection
                    }
                }
            }

            // Use the newer driver class
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Add connection timeout, auto-reconnect, and test-on-borrow options
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false" +
                    "&connectTimeout=5000" +
                    "&autoReconnect=true" +
                    "&testOnBorrow=true" +
                    "&validationQuery=SELECT 1" +
                    "&serverTimezone=UTC";

            connection = DriverManager.getConnection(url, username, password);

            // Create the table if it doesn't exist
            createTableIfNotExists();

            logger.info("Connected to database successfully.");
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            connection = null; // Make sure connection is null on failure
            return false;
        }
    }

    /**
     * Disconnects from the database.
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Disconnected from database.");
            }
        } catch (SQLException e) {
            logger.warn("Error disconnecting from database: {}", e.getMessage());
        }
    }

    /**
     * Keeps the database connection alive by executing a simple query.
     * This should be called periodically to prevent timeout issues.
     */
    public void keepConnectionAlive() {
        try {
            boolean connected = connect(); // This will validate and reconnect if needed
            if (connected) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SELECT 1");
                    logger.debug("Database heartbeat successful");
                }
            }
        } catch (SQLException e) {
            logger.warn("Database heartbeat failed: {}", e.getMessage());
        }
    }

    /**
     * Creates the required database table if it doesn't exist.
     */
    private void createTableIfNotExists() {
        try {
            if (connection == null || connection.isClosed()) {
                logger.error("Cannot create table: database connection is closed or null");
                return;
            }

            try (Statement stmt = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS hardcoreban_bans (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "player_name VARCHAR(36), " +
                        "expiry BIGINT, " +
                        "banned_by VARCHAR(36), " +
                        "banned_at BIGINT, " +
                        "reason VARCHAR(255)" +
                        ");";
                stmt.execute(sql);
                logger.info("Verified that database table exists");
            }
        } catch (SQLException e) {
            logger.error("Failed to create database table: {}", e.getMessage());
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
            connect();

            String sql = "SELECT expiry FROM hardcoreban_bans WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long expiry = rs.getLong("expiry");
                        return expiry > System.currentTimeMillis();
                    }
                    return false;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to check if player is banned: {}", e.getMessage());
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
            connect();

            String sql = "SELECT expiry FROM hardcoreban_bans WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long expiry = rs.getLong("expiry");
                        long now = System.currentTimeMillis();
                        return Math.max(0, expiry - now);
                    }
                    return 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get ban time left: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Gets all currently active bans.
     *
     * @return A map of UUID to expiry time
     */
    public Map<UUID, Long> getAllBans() {
        Map<UUID, Long> bans = new HashMap<>();

        try {
            boolean connected = connect();
            if (!connected) {
                logger.warn("Failed to connect to database when getting bans");
                return bans; // Return empty map if connection fails
            }

            if (connection == null) {
                logger.warn("Database connection is null");
                return bans; // Return empty map if connection is null
            }

            String sql = "SELECT uuid, player_name, expiry FROM hardcoreban_bans";

            try (Statement stmt = connection.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String playerName = rs.getString("player_name");
                        long expiry = rs.getLong("expiry");

                        // Only include non-expired bans
                        if (expiry > System.currentTimeMillis()) {
                            bans.put(uuid, expiry);

                            // Store player name in the plugin's name cache
                            if (playerName != null && !playerName.isEmpty()) {
                                plugin.setPlayerName(uuid, playerName);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get all bans: {}", e.getMessage());
        }

        return bans;
    }
}