package sh.pcx.hardcorebanelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.UUID;

public class ServerConnectListener {

    private final HardcoreBanVelocityPlugin plugin;
    private final MiniMessage miniMessage;

    public ServerConnectListener(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        RegisteredServer targetServer = event.getOriginalServer();

        // Check if this is the hardcore server
        if (targetServer.getServerInfo().getName().equalsIgnoreCase(plugin.getHardcoreServerName())) {
            // Force a fresh check of the shared ban file
            plugin.checkSharedFile();

            // Check if the player is banned
            if (plugin.isBanned(uuid)) {
                long timeLeft = plugin.getTimeLeft(uuid);

                // If ban has expired, allow connection
                if (timeLeft <= 0) {
                    plugin.getLogger().info("Ban for player {} has expired, allowing connection to hardcore server",
                            player.getUsername());
                    return;
                }

                // Ban is active, deny the connection and show a title message
                String formattedTime = plugin.formatTime(timeLeft);

                // Get the configured message
                String titleMessageStr = plugin.getConfigString("messages.title-banned",
                        "<red>Hardcore Mode Banned");
                String subtitleMessageStr = plugin.getConfigString("messages.subtitle-banned",
                        "<yellow>Ban expires in {time}");

                // Replace placeholders
                subtitleMessageStr = subtitleMessageStr.replace("{time}", formattedTime);

                // Create title components
                Component titleComponent = miniMessage.deserialize(titleMessageStr);
                Component subtitleComponent = miniMessage.deserialize(subtitleMessageStr);

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

                player.showTitle(title);

                // Also send a chat message for good measure
                String chatMessageStr = plugin.getConfigString("messages.chat-banned",
                        "<red>You cannot connect to the hardcore server for {time}.");
                chatMessageStr = chatMessageStr.replace("{time}", formattedTime);
                player.sendMessage(miniMessage.deserialize(chatMessageStr));

                // Cancel the connection attempt
                event.setResult(ServerPreConnectEvent.ServerResult.denied());

                plugin.getLogger().debug("Player {} attempted to connect to hardcore server while banned for {}",
                        player.getUsername(), formattedTime);
            }
        }
    }
}