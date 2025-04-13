package sh.pcx.hardcoreban.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import sh.pcx.hardcoreban.HardcoreBanPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Manages the plugin configuration, handling loading, saving, and accessing configuration values.
 */
public class ConfigManager {
    private final HardcoreBanPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /**
     * Creates a new ConfigManager instance.
     *
     * @param plugin The main plugin instance
     */
    public ConfigManager(HardcoreBanPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads or creates the configuration file.
     */
    public void loadConfig() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();

        // Load the config
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Store reference to the file
        configFile = new File(plugin.getDataFolder(), "config.yml");

        plugin.log(Level.INFO, "Configuration loaded.");
    }

    /**
     * Saves the current configuration to disk.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
            plugin.log(Level.INFO, "Configuration saved.");
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Could not save config to " + configFile + ": " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.log(Level.INFO, "Configuration reloaded.");
    }

    /**
     * Gets the raw FileConfiguration object.
     *
     * @return The FileConfiguration object
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Gets a string from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The string value
     */
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    /**
     * Gets an integer from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The integer value
     */
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * Gets a boolean from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The boolean value
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    /**
     * Gets a long from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The long value
     */
    public long getLong(String path, long defaultValue) {
        return config.getLong(path, defaultValue);
    }

    /**
     * Calculates the ban duration in milliseconds based on configuration settings.
     *
     * @return Ban duration in milliseconds
     */
    public long getBanDurationMillis() {
        String unit = config.getString("ban-duration.unit", "hours").toLowerCase();
        int amount = config.getInt("ban-duration.amount", 24);

        if ("minutes".equals(unit)) {
            return amount * 60 * 1000; // Convert minutes to ms
        } else {
            // Default to hours
            return amount * 60 * 60 * 1000; // Convert hours to ms
        }
    }

    /**
     * Gets the formatted ban time message with placeholder replaced.
     *
     * @param key The message key in configuration
     * @param timeMillis The time in milliseconds
     * @return The formatted message
     */
    public String getFormattedTimeMessage(String key, long timeMillis) {
        String unit = config.getString("ban-duration.unit", "hours").toLowerCase();
        String message = config.getString(key, "");

        if (message.isEmpty()) {
            return "";
        }

        String formattedTime = TimeFormatter.formatBanTime(timeMillis, unit);
        return message.replace("{time}", formattedTime);
    }
}