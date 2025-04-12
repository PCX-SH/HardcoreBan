package sh.pcx.hardcorebanelocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Utility class for sending messages to Paper servers
 */
public class MessageSender {

    private final HardcoreBanVelocityPlugin plugin;
    private final Logger logger;
    private final ChannelIdentifier channelId;

    public MessageSender(HardcoreBanVelocityPlugin plugin, Logger logger, ChannelIdentifier channelId) {
        this.plugin = plugin;
        this.logger = logger;
        this.channelId = channelId;
    }

    /**
     * Sends an unban message to the hardcore server
     *
     * @param uuid The UUID of the player to unban
     * @return true if the message was sent, false otherwise
     */
    public boolean sendUnban(UUID uuid) {
        // Try to find the hardcore server first
        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(plugin.getHardcoreServerName());
        if (optionalServer.isEmpty()) {
            logger.warn("Could not find hardcore server '{}' to send unban message", plugin.getHardcoreServerName());
            return false;
        }

        // Create the message
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("VELOCITY_UNBAN");
        out.writeUTF(uuid.toString());
        byte[] messageData = out.toByteArray();

        // First try: Send through an online player on the hardcore server
        for (Player player : plugin.getServer().getAllPlayers()) {
            Optional<ServerConnection> currentServer = player.getCurrentServer();
            if (currentServer.isPresent() &&
                    currentServer.get().getServerInfo().getName().equals(plugin.getHardcoreServerName())) {

                try {
                    currentServer.get().sendPluginMessage(channelId, messageData);
                    logger.info("Sent unban message to hardcore server for player {} via {}",
                            uuid, player.getUsername());
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to send plugin message through player {}: {}",
                            player.getUsername(), e.getMessage());
                }
            }
        }

        // Second try: Connect to the server and send the message
        logger.info("No players on hardcore server, attempting to connect to send unban message");

        // Try to find any player to use for connection
        if (!plugin.getServer().getAllPlayers().isEmpty()) {
            Player anyPlayer = plugin.getServer().getAllPlayers().iterator().next();

            // Try sending through current server first
            if (anyPlayer.getCurrentServer().isPresent()) {
                try {
                    anyPlayer.getCurrentServer().get().sendPluginMessage(channelId, messageData);
                    logger.info("Sent unban message for {} via player {} on their current server",
                            uuid, anyPlayer.getUsername());
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to send plugin message: {}", e.getMessage());
                }
            }
        }

        logger.warn("Could not send unban message: no suitable players online");
        return false;
    }

    /**
     * Sends a clear all bans message to the hardcore server
     *
     * @return true if the message was sent, false otherwise
     */
    public boolean sendClearAllBans() {
        // Try to find the hardcore server first
        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(plugin.getHardcoreServerName());
        if (optionalServer.isEmpty()) {
            logger.warn("Could not find hardcore server '{}' to send clear all bans message",
                    plugin.getHardcoreServerName());
            return false;
        }

        // Create message
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("VELOCITY_CLEAR_ALL");
        byte[] messageData = out.toByteArray();

        // First try: Send through an online player on the hardcore server
        for (Player player : plugin.getServer().getAllPlayers()) {
            Optional<ServerConnection> currentServer = player.getCurrentServer();
            if (currentServer.isPresent() &&
                    currentServer.get().getServerInfo().getName().equals(plugin.getHardcoreServerName())) {

                try {
                    currentServer.get().sendPluginMessage(channelId, messageData);
                    logger.info("Sent clear all bans message to hardcore server via {}", player.getUsername());
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to send plugin message through player {}: {}",
                            player.getUsername(), e.getMessage());
                }
            }
        }

        // Try to find any player to use for message sending
        if (!plugin.getServer().getAllPlayers().isEmpty()) {
            Player anyPlayer = plugin.getServer().getAllPlayers().iterator().next();

            // Try sending through current server
            if (anyPlayer.getCurrentServer().isPresent()) {
                try {
                    anyPlayer.getCurrentServer().get().sendPluginMessage(channelId, messageData);
                    logger.info("Sent clear all bans message via player {} on their current server",
                            anyPlayer.getUsername());
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to send plugin message: {}", e.getMessage());
                }
            }
        }

        logger.warn("Could not send clear all bans message: no suitable players online");
        return false;
    }
}