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

public class DatabaseManager {
    private final HardcoreBanVelocityPlugin plugin;
    private final Logger logger;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public DatabaseManager(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load database configuration from config
        this.host = plugin.getConfigString("database.host", "localhost");
        this.port = plugin.getConfigInt("database.port", 3306);
        this.database = plugin.getConfigString("database.database", "minecraft");
        this.username = plugin.getConfigString("database.username", "root");
        this.password = plugin.getConfigString("database.password", "");
    }

    public boolean connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return true;
            }

            // Use the newer driver class
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Add connection timeout and some other useful options
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false" +
                    "&connectTimeout=5000" +
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