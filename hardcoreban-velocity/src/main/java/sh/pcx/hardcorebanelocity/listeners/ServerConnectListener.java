package sh.pcx.hardcorebanelocity.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;

import sh.pcx.hardcorebanelocity.HardcoreBanVelocityPlugin;
import sh.pcx.hardcorebanelocity.util.ConfigManager;
import sh.pcx.hardcorebanelocity.util.TimeFormatter;

/**
 * Listener for intercepting server connection attempts and enforcing bans.
 */
public class ServerConnectListener {

    private final HardcoreBanVelocityPlugin plugin;
    private final MiniMessage miniMessage;
    private final Logger logger;
    private final ConfigManager configManager;

    /**
     * Creates a new ServerConnectListener.
     *
     * @param plugin The main plugin instance
     */
    public ServerConnectListener(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Handles server pre-connect events. Prevents banned players from connecting to the hardcore server.
     * Uses FIRST order to run early for ban enforcement.
     *
     * @param event The server pre-connect event
     */
    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        RegisteredServer targetServer = event.getOriginalServer();

        // Check if this is the hardcore server
        String hardcoreServerName = configManager.getString("hardcore-server", "world");
        if (targetServer.getServerInfo().getName().equalsIgnoreCase(hardcoreServerName)) {
            // Force a DB check for the player's ban status
            if (plugin.isBanned(uuid)) {
                long timeLeft = plugin.getTimeLeft(uuid);

                // If ban has expired, allow connection
                if (timeLeft <= 0) {
                    logger.info("Ban for player {} has expired, allowing connection to hardcore server",
                            player.getUsername());
                    return;
                }

                // Ban is active, deny the connection and show messages
                showBanMessages(player, timeLeft);

                // Cancel the connection attempt
                event.setResult(ServerPreConnectEvent.ServerResult.denied());

                logger.debug("Player {} attempted to connect to hardcore server while banned for {}",
                        player.getUsername(), TimeFormatter.formatTime(timeLeft));
            }
        }
    }

    /**
     * Shows ban notification messages to a player.
     * Displays both a title and a chat message.
     *
     * @param player The player to show messages to
     * @param timeLeft The time left on the ban in milliseconds
     */
    private void showBanMessages(Player player, long timeLeft) {
        String formattedTime = TimeFormatter.formatDisplayTime(timeLeft);

        // Get the configured messages
        String titleMessageStr = configManager.getString("messages.title-banned",
                "<red>Hardcore Mode Banned");
        String subtitleMessageStr = configManager.getString("messages.subtitle-banned",
                "<yellow>Ban expires in {time}");
        String chatMessageStr = configManager.getString("messages.chat-banned",
                "<red>You cannot connect to the hardcore server for {time}.");

        // Replace placeholders
        subtitleMessageStr = subtitleMessageStr.replace("{time}", formattedTime);
        chatMessageStr = chatMessageStr.replace("{time}", formattedTime);

        // Create title components
        Component titleComponent = miniMessage.deserialize(titleMessageStr);
        Component subtitleComponent = miniMessage.deserialize(subtitleMessageStr);
        Component chatComponent = miniMessage.deserialize(chatMessageStr);

        // Create and show title
        Title title = Title.title(
                titleComponent,
                subtitleComponent,
                Title.Times.of(
                        Duration.ofMillis(500),   // Fade in
                        Duration.ofMillis(3000),  // Stay
                        Duration.ofMillis(500)    // Fade out
                )
        );

        // Show messages to the player
        player.showTitle(title);
        player.sendMessage(chatComponent);
    }
}