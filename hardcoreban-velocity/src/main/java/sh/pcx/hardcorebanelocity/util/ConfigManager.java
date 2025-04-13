package sh.pcx.hardcorebanelocity.util;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages configuration loading and access for the Velocity plugin.
 */
public class ConfigManager {
    private final Logger logger;
    private final Path dataDirectory;
    private Map<String, Object> config;
    private File configFile;

    /**
     * Creates a new ConfigManager instance.
     *
     * @param logger The plugin logger
     * @param dataDirectory The plugin data directory
     */
    public ConfigManager(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.yml").toFile();
        loadConfig();
    }

    /**
     * Loads or creates the configuration file.
     */
    public void loadConfig() {
        try {
            // Create the data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created plugin data directory");
            }

            // Create default config if it doesn't exist
            if (!configFile.exists()) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                        logger.info("Created default configuration file");
                    } else {
                        // Create empty config with defaults
                        Map<String, Object> defaultConfig = createDefaultConfig();
                        Files.writeString(configFile.toPath(), new Yaml().dump(defaultConfig));
                        logger.info("Created default configuration with built-in values");
                    }
                }
            }

            // Load the config
            try (FileReader reader = new FileReader(configFile)) {
                config = new Yaml().load(reader);

                if (config == null) {
                    logger.warn("Empty configuration file, using defaults");
                    config = createDefaultConfig();
                }

                logger.info("Configuration loaded successfully");
            }
        } catch (IOException e) {
            logger.error("Failed to load config: {}", e.getMessage());

            // Create default config in memory
            config = createDefaultConfig();
            logger.info("Using default configuration values");
        }
    }

    /**
     * Creates a default configuration map with sensible defaults.
     *
     * @return A map containing default configuration values
     */
    private Map<String, Object> createDefaultConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("hardcore-server", "world");
        defaultConfig.put("check-interval", 10);

        // Database defaults
        Map<String, Object> database = new HashMap<>();
        database.put("host", "localhost");
        database.put("port", 3306);
        database.put("database", "minecraft");
        database.put("username", "root");
        database.put("password", "password");
        defaultConfig.put("database", database);

        // Message defaults
        Map<String, String> messages = new HashMap<>();
        messages.put("title-banned", "<red>Hardcore Mode Banned");
        messages.put("subtitle-banned", "<yellow>Ban expires in {time}");
        messages.put("chat-banned", "<red>You cannot connect to the hardcore server for {time}.");
        defaultConfig.put("messages", messages);

        return defaultConfig;
    }

    /**
     * Saves the current configuration to disk.
     */
    public void saveConfig() {
        try {
            String yamlString = new Yaml().dump(config);
            Files.writeString(configFile.toPath(), yamlString);
            logger.info("Configuration saved successfully");
        } catch (IOException e) {
            logger.error("Failed to save config: {}", e.getMessage());
        }
    }

    /**
     * Gets a string from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The string value
     */
    public String getString(String path, String defaultValue) {
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
            logger.warn("Error getting config value for path: {}", path);
            return defaultValue;
        }
    }

    /**
     * Gets an integer from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The integer value
     */
    public int getInt(String path, int defaultValue) {
        String strValue = getString(path, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean from the configuration.
     *
     * @param path The path to the value
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The boolean value
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        String strValue = getString(path, String.valueOf(defaultValue));
        if (strValue.equalsIgnoreCase("true")) {
            return true;
        } else if (strValue.equalsIgnoreCase("false")) {
            return false;
        } else {
            return defaultValue;
        }
    }

    /**
     * Gets a message from the configuration with time placeholder replaced.
     *
     * @param key The message key (without the messages. prefix)
     * @param timeMillis The time in milliseconds to insert in the placeholder
     * @return The formatted message
     */
    public String getFormattedTimeMessage(String key, long timeMillis) {
        String message = getString("messages." + key, "");
        if (message.isEmpty()) {
            return "";
        }

        String formattedTime = TimeFormatter.formatTime(timeMillis);
        return message.replace("{time}", formattedTime);
    }
}