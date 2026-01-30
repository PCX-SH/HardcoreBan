package sh.pcx.hardcoreban.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.command.CommandSender;
import sh.pcx.hardcoreban.HardcoreBanBootstrap;
import sh.pcx.hardcoreban.model.Ban;
import sh.pcx.hardcoreban.util.ConfigManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages database operations for the HardcoreBan plugin.
 * Handles connections via HikariCP connection pool, ban storage and retrieval.
 */
public class DatabaseManager {
    private final HardcoreBanBootstrap plugin;
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
    public DatabaseManager(HardcoreBanBootstrap plugin) {
        this.plugin = plugin;

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
            config.setPoolName("HardcoreBan-Pool");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(1800000);
            config.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(config);

            // Create the table if it doesn't exist
            createTablesIfNotExist();

            plugin.log(Level.INFO, "Connected to database successfully using HikariCP.");
            return true;
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to connect to database: " + e.getMessage());
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
            plugin.log(Level.INFO, "Disconnected from database.");
        }
    }

    /**
     * Creates the required database tables if they don't exist.
     */
    private void createTablesIfNotExist() {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.SEVERE, "Cannot create table: database connection pool is closed or null");
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
            plugin.log(Level.INFO, "Verified that database table exists");
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create database table: " + e.getMessage());
        }
    }

    /**
     * Adds a ban for a player who died in hardcore mode.
     * Uses default values for banned by, banned at, and reason.
     *
     * @param uuid The UUID of the player
     * @param playerName The name of the player
     * @param expiry The time when the ban expires
     * @return true if the ban was added successfully, false otherwise
     */
    public boolean addBan(UUID uuid, String playerName, long expiry) {
        return addBan(uuid, playerName, expiry, "Console", System.currentTimeMillis(), "Death in hardcore mode");
    }

    /**
     * Adds a ban with custom details.
     *
     * @param uuid The UUID of the player
     * @param playerName The name of the player
     * @param expiry The time when the ban expires
     * @param bannedBy Who banned the player
     * @param bannedAt When the ban was created
     * @param reason The reason for the ban
     * @return true if the ban was added successfully, false otherwise
     */
    public boolean addBan(UUID uuid, String playerName, long expiry, String bannedBy, long bannedAt, String reason) {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return false;
        }

        String sql = "INSERT INTO hardcoreban_bans (uuid, player_name, expiry, banned_by, banned_at, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "player_name = ?, expiry = ?, banned_by = ?, banned_at = ?, reason = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerName);
            stmt.setLong(3, expiry);
            stmt.setString(4, bannedBy);
            stmt.setLong(5, bannedAt);
            stmt.setString(6, reason);

            // For ON DUPLICATE KEY UPDATE
            stmt.setString(7, playerName);
            stmt.setLong(8, expiry);
            stmt.setString(9, bannedBy);
            stmt.setLong(10, bannedAt);
            stmt.setString(11, reason);

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to add ban: " + e.getMessage());
            return false;
        }
    }

    /**
     * Adds a ban using a Ban model object.
     *
     * @param ban The Ban object containing all ban details
     * @return true if the ban was added successfully, false otherwise
     */
    public boolean addBan(Ban ban) {
        return addBan(
                ban.getUuid(),
                ban.getPlayerName(),
                ban.getExpiry(),
                ban.getBannedBy(),
                ban.getBannedAt(),
                ban.getReason()
        );
    }

    /**
     * Removes a ban for a player.
     *
     * @param uuid The UUID of the player
     * @return true if a ban was removed, false if the player wasn't banned or an error occurred
     */
    public boolean removeBan(UUID uuid) {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return false;
        }

        String sql = "DELETE FROM hardcoreban_bans WHERE uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to remove ban: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clears all bans from the database.
     */
    public void clearAllBans() {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return;
        }

        String sql = "DELETE FROM hardcoreban_bans";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int rowsAffected = stmt.executeUpdate(sql);
            plugin.log(Level.INFO, "Cleared " + rowsAffected + " bans from the database");
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to clear all bans: " + e.getMessage());
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
            plugin.log(Level.WARNING, "Database connection pool is not available");
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
            plugin.log(Level.SEVERE, "Failed to check if player is banned: " + e.getMessage());
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
            plugin.log(Level.WARNING, "Database connection pool is not available");
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
            plugin.log(Level.SEVERE, "Failed to get ban time left: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gets a Ban object for a player if they are banned.
     *
     * @param uuid The UUID of the player
     * @return A Ban object, or null if the player isn't banned
     */
    public Ban getBan(UUID uuid) {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return null;
        }

        String sql = "SELECT player_name, expiry, banned_by, banned_at, reason FROM hardcoreban_bans WHERE uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String playerName = rs.getString("player_name");
                    long expiry = rs.getLong("expiry");
                    String bannedBy = rs.getString("banned_by");
                    long bannedAt = rs.getLong("banned_at");
                    String reason = rs.getString("reason");

                    // Only return if ban is still active
                    if (expiry > System.currentTimeMillis()) {
                        return new Ban(uuid, playerName, expiry, bannedBy, bannedAt, reason);
                    }
                }
                return null;
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to get ban for player: " + e.getMessage());
            return null;
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
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return bans;
        }

        String sql = "SELECT uuid, expiry FROM hardcoreban_bans";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                long expiry = rs.getLong("expiry");

                // Only include non-expired bans
                if (expiry > System.currentTimeMillis()) {
                    bans.put(uuid, expiry);
                }
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to get all bans: " + e.getMessage());
        }

        return bans;
    }

    /**
     * Gets all active bans with full details.
     *
     * @return A map of UUID to Ban objects
     */
    public Map<UUID, Ban> getAllBanDetails() {
        Map<UUID, Ban> bans = new HashMap<>();

        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return bans;
        }

        String sql = "SELECT uuid, player_name, expiry, banned_by, banned_at, reason FROM hardcoreban_bans";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            long now = System.currentTimeMillis();

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String playerName = rs.getString("player_name");
                long expiry = rs.getLong("expiry");
                String bannedBy = rs.getString("banned_by");
                long bannedAt = rs.getLong("banned_at");
                String reason = rs.getString("reason");

                // Only include non-expired bans
                if (expiry > now) {
                    Ban ban = new Ban(uuid, playerName, expiry, bannedBy, bannedAt, reason);
                    bans.put(uuid, ban);
                }
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to get all ban details: " + e.getMessage());
        }

        return bans;
    }

    /**
     * Removes expired bans from the database.
     */
    public void cleanupExpiredBans() {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.log(Level.WARNING, "Database connection pool is not available");
            return;
        }

        String sql = "DELETE FROM hardcoreban_bans WHERE expiry <= ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.log(Level.INFO, "Cleaned up " + rowsAffected + " expired bans.");
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to clean up expired bans: " + e.getMessage());
        }
    }

    /**
     * Executes a raw SQL query. Should only be used for admin commands.
     *
     * @param sql The SQL query to execute
     * @param sender The command sender who will receive the results
     * @throws SQLException If an SQL error occurs
     */
    public void executeRawSql(String sql, CommandSender sender) throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not available");
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            boolean isQuery = sql.trim().toLowerCase().startsWith("select");

            if (isQuery) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    if (sender != null) {
                        // Get metadata
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();

                        // Print headers
                        StringBuilder header = new StringBuilder("§a");
                        for (int i = 1; i <= columnCount; i++) {
                            header.append(meta.getColumnName(i)).append(" | ");
                        }
                        sender.sendMessage(header.toString());

                        // Print data
                        int count = 0;
                        while (rs.next() && count < 50) {
                            StringBuilder row = new StringBuilder("§7");
                            for (int i = 1; i <= columnCount; i++) {
                                row.append(rs.getString(i)).append(" | ");
                            }
                            sender.sendMessage(row.toString());
                            count++;
                        }

                        sender.sendMessage("§7Total rows: §a" + count + (count >= 50 ? " (showing first 50)" : ""));
                    }
                }
            } else {
                int rowsAffected = stmt.executeUpdate(sql);
                if (sender != null) {
                    sender.sendMessage("§aQuery executed. Rows affected: " + rowsAffected);
                }
            }
        }
    }
}
