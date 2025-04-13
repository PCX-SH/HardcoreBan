package sh.pcx.hardcorebanelocity.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.util.UUID;

import org.slf4j.Logger;

import sh.pcx.hardcorebanelocity.HardcoreBanVelocityPlugin;

/**
 * Listens for plugin messages from Paper servers.
 * Processes ban-related messages.
 */
public class PluginMessageListener {

    private final HardcoreBanVelocityPlugin plugin;
    private final ChannelIdentifier channelId;
    private final Logger logger;

    /**
     * Creates a new PluginMessageListener.
     *
     * @param plugin The main plugin instance
     */
    public PluginMessageListener(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.channelId = MinecraftChannelIdentifier.from("hardcoreban:channel");
        this.logger = plugin.getLogger();
    }

    /**
     * Handles plugin messages received from Paper servers.
     *
     * @param event The plugin message event
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Check if this is our channel
        if (!event.getIdentifier().equals(channelId)) {
            return;
        }

        // Mark the message as handled
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Process the message
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String messageType = in.readUTF();

        logger.debug("Received plugin message: {}", messageType);

        switch (messageType) {
            case "BAN":
                handleBanMessage(in);
                break;
            case "UNBAN":
                handleUnbanMessage(in);
                break;
            case "CLEAR_ALL":
                handleClearAllMessage();
                break;
            default:
                logger.warn("Received unknown plugin message type: {}", messageType);
                break;
        }
    }

    /**
     * Handles a BAN message from a Paper server.
     *
     * @param in The data input stream
     */
    private void handleBanMessage(ByteArrayDataInput in) {
        try {
            String uuidStr = in.readUTF();
            UUID uuid = UUID.fromString(uuidStr);
            long expiry = in.readLong();

            // Refresh ban data from database to ensure it's up-to-date
            plugin.refreshBans();

            logger.info("Received ban notification for player {}, expiry: {}",
                    plugin.getPlayerName(uuid), new java.util.Date(expiry));
        } catch (Exception e) {
            logger.error("Error processing BAN message: {}", e.getMessage());
        }
    }

    /**
     * Handles an UNBAN message from a Paper server.
     *
     * @param in The data input stream
     */
    private void handleUnbanMessage(ByteArrayDataInput in) {
        try {
            String uuidStr = in.readUTF();
            UUID uuid = UUID.fromString(uuidStr);

            // Refresh ban data from database to ensure it's up-to-date
            plugin.refreshBans();

            logger.info("Received unban notification for player {}", plugin.getPlayerName(uuid));
        } catch (Exception e) {
            logger.error("Error processing UNBAN message: {}", e.getMessage());
        }
    }

    /**
     * Handles a CLEAR_ALL message from a Paper server.
     */
    private void handleClearAllMessage() {
        try {
            // Refresh ban data from database to ensure it's up-to-date
            plugin.refreshBans();

            logger.info("Received clear all bans notification");
        } catch (Exception e) {
            logger.error("Error processing CLEAR_ALL message: {}", e.getMessage());
        }
    }
}