package sh.pcx.hardcoreban;

import java.util.UUID;
import java.util.logging.Level;

import com.google.common.io.ByteArrayDataOutput;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.entity.Player;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

public class VelocityMessageListener implements PluginMessageListener {

    private final HardcoreBanPlugin plugin;

    public VelocityMessageListener(HardcoreBanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("hardcoreban:channel")) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String messageType = in.readUTF();

        plugin.log(Level.FINE, "Received plugin message: " + messageType);

        switch (messageType) {
            case "CHECK_BAN":
                try {
                    String uuidStr = in.readUTF();
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean isBanned = plugin.isBanned(uuid);
                    long timeLeft = plugin.getTimeLeft(uuid);

                    // Respond back to Velocity
                    if (player.isOnline()) {
                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("BAN_STATUS");
                        out.writeUTF(uuid.toString());
                        out.writeBoolean(isBanned);
                        out.writeLong(timeLeft);

                        player.sendPluginMessage(plugin, "hardcoreban:channel", out.toByteArray());
                        plugin.log(Level.FINE, "Sent ban status for " + uuidStr + ": banned=" + isBanned +
                                ", timeLeft=" + timeLeft);
                    }
                } catch (Exception e) {
                    plugin.log(Level.WARNING, "Error processing CHECK_BAN message: " + e.getMessage());
                }
                break;

            default:
                plugin.log(Level.WARNING, "Received unknown plugin message type: " + messageType);
                break;
        }
    }
}