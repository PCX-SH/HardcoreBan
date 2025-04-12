package sh.pcx.hardcorebanelocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HardcoreBanCommand implements SimpleCommand {

    private final HardcoreBanVelocityPlugin plugin;
    private final MiniMessage miniMessage;

    public HardcoreBanCommand(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(miniMessage.deserialize("<yellow>HardcoreBan Velocity commands:"));
            source.sendMessage(miniMessage.deserialize("<yellow>/vhardcoreban check <player> - Check if a player is banned"));
            source.sendMessage(miniMessage.deserialize("<yellow>/vhardcoreban list - List all banned players"));
            source.sendMessage(miniMessage.deserialize("<yellow>/vhardcoreban refresh - Refresh ban data from shared file"));
            source.sendMessage(miniMessage.deserialize("<yellow>Note: Bans are managed on the Paper server."));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "check":
                if (!source.hasPermission("hardcoreban.check")) {
                    source.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
                    return;
                }

                if (args.length < 2) {
                    source.sendMessage(miniMessage.deserialize("<red>Usage: /vhardcoreban check <player>"));
                    return;
                }

                String playerName = args[1];

                // Force a refresh
                plugin.checkSharedFile();

                // Try to find by name
                UUID uuid = null;
                for (Map.Entry<UUID, Long> entry : plugin.getBannedPlayers().entrySet()) {
                    UUID id = entry.getKey();
                    String name = plugin.getPlayerName(id);
                    if (name.equalsIgnoreCase(playerName)) {
                        uuid = id;
                        break;
                    }
                }

                if (uuid == null) {
                    // Try online players
                    uuid = plugin.getServer().getPlayer(playerName)
                            .map(p -> p.getUniqueId())
                            .orElse(null);
                }

                if (uuid == null) {
                    source.sendMessage(miniMessage.deserialize("<red>Player not found."));
                    return;
                }

                if (plugin.isBanned(uuid)) {
                    long timeLeft = plugin.getTimeLeft(uuid);
                    source.sendMessage(miniMessage.deserialize("<yellow>" + playerName +
                            " is banned for " + plugin.formatTime(timeLeft) + "."));
                } else {
                    source.sendMessage(miniMessage.deserialize("<yellow>" + playerName + " is not banned."));
                }
                break;

            case "list":
                if (!source.hasPermission("hardcoreban.list")) {
                    source.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
                    return;
                }

                // Force a refresh
                plugin.checkSharedFile();

                Map<UUID, Long> bannedPlayers = plugin.getBannedPlayers();

                if (bannedPlayers.isEmpty()) {
                    source.sendMessage(miniMessage.deserialize("<yellow>There are no banned players."));
                    return;
                }

                source.sendMessage(miniMessage.deserialize("<yellow>Banned players (" + bannedPlayers.size() + "):"));

                for (Map.Entry<UUID, Long> entry : bannedPlayers.entrySet()) {
                    UUID playerUuid = entry.getKey();
                    long expiry = entry.getValue();
                    long timeLeft = expiry - System.currentTimeMillis();

                    String bannedPlayerName = plugin.getPlayerName(playerUuid);

                    source.sendMessage(miniMessage.deserialize("<yellow> - " + bannedPlayerName +
                            " - " + plugin.formatTime(timeLeft) + " remaining"));
                }
                break;

            case "refresh":
                if (!source.hasPermission("hardcoreban.admin")) {
                    source.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
                    return;
                }

                // Force a refresh
                plugin.checkSharedFile();

                source.sendMessage(miniMessage.deserialize("<green>Ban data refreshed from shared file."));
                break;

            default:
                source.sendMessage(miniMessage.deserialize("<red>Unknown command. Use /vhardcoreban for help."));
                break;
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 0 || args.length == 1) {
            if (source.hasPermission("hardcoreban.check")) suggestions.add("check");
            if (source.hasPermission("hardcoreban.list")) suggestions.add("list");
            if (source.hasPermission("hardcoreban.admin")) suggestions.add("refresh");

            return filterByStart(suggestions, args.length == 0 ? "" : args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check") && source.hasPermission("hardcoreban.check")) {
            plugin.getServer().getAllPlayers().forEach(p -> suggestions.add(p.getUsername()));
            return filterByStart(suggestions, args[1]);
        }

        return suggestions;
    }

    private List<String> filterByStart(List<String> list, String prefix) {
        List<String> filtered = new ArrayList<>();
        for (String item : list) {
            if (item.toLowerCase().startsWith(prefix.toLowerCase())) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}