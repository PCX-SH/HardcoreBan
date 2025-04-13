package sh.pcx.hardcorebanelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import sh.pcx.hardcorebanelocity.HardcoreBanVelocityPlugin;
import sh.pcx.hardcorebanelocity.util.TimeFormatter;

/**
 * Handles commands for the Velocity plugin.
 */
public class HardcoreBanCommand implements SimpleCommand {

    private final HardcoreBanVelocityPlugin plugin;
    private final MiniMessage miniMessage;
    private final Logger logger;

    /**
     * Creates a new HardcoreBanCommand instance.
     *
     * @param plugin The main plugin instance
     */
    public HardcoreBanCommand(HardcoreBanVelocityPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
        this.logger = plugin.getLogger();
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            displayHelp(source);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "check":
                handleCheckCommand(source, args);
                break;
            case "list":
                handleListCommand(source, args);
                break;
            case "refresh":
                handleRefreshCommand(source, args);
                break;
            default:
                source.sendMessage(miniMessage.deserialize("<red>Unknown command. Use /vhardcoreban for help."));
                break;
        }
    }

    /**
     * Displays the help message listing all available commands.
     *
     * @param source The command source
     */
    private void displayHelp(CommandSource source) {
        source.sendMessage(miniMessage.deserialize("<yellow>HardcoreBan Velocity commands:"));
        source.sendMessage(miniMessage.deserialize("<yellow>/vhardcoreban check <player> - Check if a player is banned"));
        source.sendMessage(miniMessage.deserialize("<yellow>/vhardcoreban list - List all banned players"));

        if (source.hasPermission("hardcoreban.admin")) {
            source.sendMessage(miniMessage.deserialize("<yellow>/vhardcoreban refresh - Refresh ban data from database"));
        }

        source.sendMessage(miniMessage.deserialize("<yellow>Note: Bans are managed on the Paper server."));
    }

    /**
     * Handles the "check" command to check if a player is banned.
     *
     * @param source The command source
     * @param args The command arguments
     */
    private void handleCheckCommand(CommandSource source, String[] args) {
        if (!source.hasPermission("hardcoreban.check")) {
            source.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return;
        }

        if (args.length < 2) {
            source.sendMessage(miniMessage.deserialize("<red>Usage: /vhardcoreban check <player>"));
            return;
        }

        String playerName = args[1];

        // Force a refresh of ban data
        plugin.refreshBans();

        // Try to find player UUID by name
        UUID uuid = findPlayerUuidByName(playerName);

        if (uuid == null) {
            source.sendMessage(miniMessage.deserialize("<red>Player not found."));
            return;
        }

        if (plugin.isBanned(uuid)) {
            long timeLeft = plugin.getTimeLeft(uuid);
            String formattedTime = TimeFormatter.formatTime(timeLeft);

            source.sendMessage(miniMessage.deserialize("<yellow>" + playerName +
                    " is banned for " + formattedTime + "."));
        } else {
            source.sendMessage(miniMessage.deserialize("<yellow>" + playerName + " is not banned."));
        }
    }

    /**
     * Handles the "list" command to list all banned players.
     *
     * @param source The command source
     * @param args The command arguments
     */
    private void handleListCommand(CommandSource source, String[] args) {
        if (!source.hasPermission("hardcoreban.list")) {
            source.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return;
        }

        // Force a refresh of ban data
        plugin.refreshBans();

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
            String formattedTime = TimeFormatter.formatTimeCompact(timeLeft);

            source.sendMessage(miniMessage.deserialize("<yellow> - " + bannedPlayerName +
                    " - " + formattedTime + " remaining"));
        }
    }

    /**
     * Handles the "refresh" command to refresh ban data from the database.
     *
     * @param source The command source
     * @param args The command arguments
     */
    private void handleRefreshCommand(CommandSource source, String[] args) {
        if (!source.hasPermission("hardcoreban.admin")) {
            source.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return;
        }

        // Force a refresh
        plugin.refreshBans();

        source.sendMessage(miniMessage.deserialize("<green>Ban data refreshed from database."));
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
            // Add online player names
            plugin.getServer().getAllPlayers().forEach(p -> suggestions.add(p.getUsername()));
            return filterByStart(suggestions, args[1]);
        }

        return suggestions;
    }

    /**
     * Filters a list of strings by those that start with a prefix.
     *
     * @param list The list of strings to filter
     * @param prefix The prefix to match
     * @return A filtered list of strings
     */
    private List<String> filterByStart(List<String> list, String prefix) {
        List<String> filtered = new ArrayList<>();
        for (String item : list) {
            if (item.toLowerCase().startsWith(prefix.toLowerCase())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /**
     * Finds a player's UUID by their name.
     *
     * @param name The player's name
     * @return The player's UUID, or null if not found
     */
    private UUID findPlayerUuidByName(String name) {
        // Try online players first
        return plugin.getServer().getPlayer(name)
                .map(p -> p.getUniqueId())
                .orElseGet(() -> {
                    // Then try banned players list
                    for (Map.Entry<UUID, Long> entry : plugin.getBannedPlayers().entrySet()) {
                        UUID id = entry.getKey();
                        String playerName = plugin.getPlayerName(id);
                        if (playerName.equalsIgnoreCase(name)) {
                            return id;
                        }
                    }
                    return null;
                });
    }
}