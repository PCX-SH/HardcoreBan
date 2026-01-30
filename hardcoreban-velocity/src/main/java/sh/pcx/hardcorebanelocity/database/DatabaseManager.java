package sh.pcx.hardcorebanelocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
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
 * Handles connections via HikariCP connection pool and ban data retrieval.
 */
public class DatabaseManager {
    private final HardcoreBanVelocityPlugin plugin;
    private final Logger logger;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private HikariDataSource dataSource;

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
     * Connects to the database using HikariCP connection pool.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean connect() {
        if (dataSource != null && !dataSource.isClosed()) {
            return true;
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName("HardcoreBan-Velocity-Pool");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(1800000);
            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);

            // Create the table if it doesn't exist
            createTableIfNotExists();

            logger.info("Connected to database successfully using HikariCP.");
            return true;
        } catch (Exception e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            dataSource = null;
            return false;
        }
    }

    /**
     * Disconnects from the database by closing the HikariCP pool.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Disconnected from database.");
        }
    }

    /**
     * Creates the required database table if it doesn't exist.
     */
    private void createTableIfNotExists() {
        if (dataSource == null || dataSource.isClosed()) {
            logger.error("Cannot create table: database connection pool is closed or null");
            return;
        }

        String sql = "CREATE TABLE IF NOT EXISTS hardcoreban_bans (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(36), " +
                "expiry BIGINT, " +
                "banned_by VARCHAR(36), " +
                "banned_at BIGINT, " +
                "reason VARCHAR(255)" +
                ");";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Verified that database table exists");
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
        if (dataSource == null || dataSource.isClosed()) {
            logger.warn("Database connection pool is not available");
            return false;
        }

        String sql = "SELECT expiry FROM hardcoreban_bans WHERE uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long expiry = rs.getLong("expiry");
                    return expiry > System.currentTimeMillis();
                }
                return false;
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
        if (dataSource == null || dataSource.isClosed()) {
            logger.warn("Database connection pool is not available");
            return 0;
        }

        String sql = "SELECT expiry FROM hardcoreban_bans WHERE uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long expiry = rs.getLong("expiry");
                    long now = System.currentTimeMillis();
                    return Math.max(0, expiry - now);
                }
                return 0;
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

        if (dataSource == null || dataSource.isClosed()) {
            logger.warn("Database connection pool is not available");
            return bans;
        }

        String sql = "SELECT uuid, player_name, expiry FROM hardcoreban_bans";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
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
        } catch (SQLException e) {
            logger.error("Failed to get all bans: {}", e.getMessage());
        }

        return bans;
    }
}
