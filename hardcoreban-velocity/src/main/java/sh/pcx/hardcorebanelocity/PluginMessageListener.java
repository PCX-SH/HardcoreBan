package sh.pcx.hardcorebanelocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.util.UUID;

public class PluginMessageListener {

    private final HardcoreBanVelocityPlugin plugin;
    private final ChannelIdentifier channelId = MinecraftChannelIdentifier.from("hardcoreban:channel");

    public PluginMessageListener(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
    }

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

        switch (messageType) {
            // We don't need to handle these anymore - the shared file handles synchronization
            case "BAN":
            case "UNBAN":
            case "CLEAR_ALL":
                // Force a refresh of the shared file
                plugin.checkSharedFile();
                break;

            default:
                plugin.getLogger().warn("Received unknown plugin message type: {}", messageType);
                break;
        }
    }
}