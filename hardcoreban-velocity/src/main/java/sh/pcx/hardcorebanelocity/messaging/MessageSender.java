package sh.pcx.hardcorebanelocity.messaging;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

import sh.pcx.hardcorebanelocity.HardcoreBanVelocityPlugin;

/**
 * Utility class for sending messages to Paper servers.
 */
public class MessageSender {

    private final HardcoreBanVelocityPlugin plugin;
    private final Logger logger;
    private final ChannelIdentifier channelId;

    /**
     * Creates a new MessageSender.
     *
     * @param plugin The main plugin instance
     * @param logger The plugin logger
     * @param channelId The plugin messaging channel identifier
     */
    public MessageSender(HardcoreBanVelocityPlugin plugin, Logger logger, ChannelIdentifier channelId) {
        this.plugin = plugin;
        this.logger = logger;
        this.channelId = channelId;
    }

    /**
     * Sends an unban message to the hardcore server.
     *
     * @param uuid The UUID of the player to unban
     * @return true if the message was sent, false otherwise
     */
    public boolean sendUnban(UUID uuid) {
        // Try to find the hardcore server first
        String hardcoreServerName = plugin.getConfigManager().getString("hardcore-server", "world");
        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(hardcoreServerName);

        if (optionalServer.isEmpty()) {
            logger.warn("Could not find hardcore server '{}' to send unban message", hardcoreServerName);
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
                    currentServer.get().getServerInfo().getName().equals(hardcoreServerName)) {

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

        // Second try: Try to send through any player's current server
        if (!plugin.getServer().getAllPlayers().isEmpty()) {
            Player anyPlayer = plugin.getServer().getAllPlayers().iterator().next();

            // Try sending through current server
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
     * Sends a clear all bans message to the hardcore server.
     *
     * @return true if the message was sent, false otherwise
     */
    public boolean sendClearAllBans() {
        // Try to find the hardcore server first
        String hardcoreServerName = plugin.getConfigManager().getString("hardcore-server", "world");
        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(hardcoreServerName);

        if (optionalServer.isEmpty()) {
            logger.warn("Could not find hardcore server '{}' to send clear all bans message",
                    hardcoreServerName);
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
                    currentServer.get().getServerInfo().getName().equals(hardcoreServerName)) {

                try {
                    currentServer.get().sendPluginMessage(channelId, messageData);
                    logger.info("Sent clear all bans message to hardcore server via {}",
                            player.getUsername());
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to send plugin message through player {}: {}",
                            player.getUsername(), e.getMessage());
                }
            }
        }

        // Second try: Try to send through any player's current server
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

    /**
     * Sends a ban check request to the hardcore server.
     *
     * @param uuid The UUID of the player to check
     * @return true if the message was sent, false otherwise
     */
    public boolean sendBanCheck(UUID uuid) {
        // Try to find the hardcore server first
        String hardcoreServerName = plugin.getConfigManager().getString("hardcore-server", "world");
        Optional<RegisteredServer> optionalServer = plugin.getServer().getServer(hardcoreServerName);

        if (optionalServer.isEmpty()) {
            logger.warn("Could not find hardcore server '{}' to send ban check message",
                    hardcoreServerName);
            return false;
        }

        // Create message
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CHECK_BAN");
        out.writeUTF(uuid.toString());
        byte[] messageData = out.toByteArray();

        // Try to find a player to send the message through
        if (!plugin.getServer().getAllPlayers().isEmpty()) {
            Player anyPlayer = plugin.getServer().getAllPlayers().iterator().next();

            // Try sending through current server
            if (anyPlayer.getCurrentServer().isPresent()) {
                try {
                    anyPlayer.getCurrentServer().get().sendPluginMessage(channelId, messageData);
                    logger.debug("Sent ban check message for {}", uuid);
                    return true;
                } catch (Exception e) {
                    logger.warn("Failed to send ban check message: {}", e.getMessage());
                }
            }
        }

        logger.warn("Could not send ban check message: no players online");
        return false;
    }
}