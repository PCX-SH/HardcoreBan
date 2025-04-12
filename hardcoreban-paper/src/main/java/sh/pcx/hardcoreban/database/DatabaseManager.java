package sh.pcx.hardcoreban.database;

import org.bukkit.command.CommandSender;
import sh.pcx.hardcoreban.HardcoreBanPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final HardcoreBanPlugin plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public DatabaseManager(HardcoreBanPlugin plugin) {
        this.plugin = plugin;

        // Load database configuration from config
        this.host = plugin.getConfig().getString("database.host", "localhost");
        this.port = plugin.getConfig().getInt("database.port", 3306);
        this.database = plugin.getConfig().getString("database.database", "minecraft");
        this.username = plugin.getConfig().getString("database.username", "root");
        this.password = plugin.getConfig().getString("database.password", "");
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
            createTablesIfNotExist();

            plugin.log(Level.INFO, "Connected to database successfully.");
            return true;
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to connect to database: " + e.getMessage());
            connection = null; // Make sure connection is null on failure
            return false;
        } catch (ClassNotFoundException e) {
            plugin.log(Level.SEVERE, "MySQL driver not found: " + e.getMessage());
            connection = null;
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.log(Level.INFO, "Disconnected from database.");
            }
        } catch (SQLException e) {
            plugin.log(Level.WARNING, "Error disconnecting from database: " + e.getMessage());
        }
    }

    private void createTablesIfNotExist() {
        try {
            if (connection == null || connection.isClosed()) {
                plugin.log(Level.SEVERE, "Cannot create table: database connection is closed or null");
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
                plugin.log(Level.INFO, "Verified that database table exists");
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create database table: " + e.getMessage());
        }
    }

    public boolean addBan(UUID uuid, String playerName, long expiry) {
        return addBan(uuid, playerName, expiry, "Console", System.currentTimeMillis(), "Death in hardcore mode");
    }

    public boolean addBan(UUID uuid, String playerName, long expiry, String bannedBy, long bannedAt, String reason) {
        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when adding ban");
                return false;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return false;
            }

            String sql = "INSERT INTO hardcoreban_bans (uuid, player_name, expiry, banned_by, banned_at, reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "player_name = ?, expiry = ?, banned_by = ?, banned_at = ?, reason = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to add ban: " + e.getMessage());
            return false;
        }
    }

    public boolean removeBan(UUID uuid) {
        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when removing ban");
                return false;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return false;
            }

            String sql = "DELETE FROM hardcoreban_bans WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to remove ban: " + e.getMessage());
            return false;
        }
    }

    public void clearAllBans() {
        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when clearing bans");
                return;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return;
            }

            String sql = "DELETE FROM hardcoreban_bans";

            try (Statement stmt = connection.createStatement()) {
                int rowsAffected = stmt.executeUpdate(sql);
                plugin.log(Level.INFO, "Cleared " + rowsAffected + " bans from the database");
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to clear all bans: " + e.getMessage());
        }
    }

    public boolean isBanned(UUID uuid) {
        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when checking ban status");
                return false;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return false;
            }

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
            plugin.log(Level.SEVERE, "Failed to check if player is banned: " + e.getMessage());
            return false;
        }
    }

    public long getTimeLeft(UUID uuid) {
        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when getting ban time left");
                return 0;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return 0;
            }

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
            plugin.log(Level.SEVERE, "Failed to get ban time left: " + e.getMessage());
            return 0;
        }
    }

    public Map<UUID, Long> getAllBans() {
        Map<UUID, Long> bans = new HashMap<>();

        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when getting all bans");
                return bans;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return bans;
            }

            String sql = "SELECT uuid, expiry FROM hardcoreban_bans";

            try (Statement stmt = connection.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        long expiry = rs.getLong("expiry");

                        // Only include non-expired bans
                        if (expiry > System.currentTimeMillis()) {
                            bans.put(uuid, expiry);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to get all bans: " + e.getMessage());
        }

        return bans;
    }

    public void cleanupExpiredBans() {
        try {
            boolean connected = connect();
            if (!connected) {
                plugin.log(Level.WARNING, "Failed to connect to database when cleaning up expired bans");
                return;
            }

            if (connection == null) {
                plugin.log(Level.WARNING, "Database connection is null");
                return;
            }

            String sql = "DELETE FROM hardcoreban_bans WHERE expiry <= ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, System.currentTimeMillis());

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    plugin.log(Level.INFO, "Cleaned up " + rowsAffected + " expired bans.");
                }
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to clean up expired bans: " + e.getMessage());
        }
    }

    public void executeRawSql(String sql, CommandSender sender) throws SQLException {
        if (!connect()) {
            throw new SQLException("Could not connect to database");
        }

        try (Statement stmt = connection.createStatement()) {
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