package sh.pcx.hardcoreban.commands;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import sh.pcx.hardcoreban.HardcoreBanBootstrap;
import sh.pcx.hardcoreban.util.TimeFormatter;

/**
 * Handles all HardcoreBan commands.
 * Implements command execution and tab completion.
 */
public class HardcoreBanCommand implements CommandExecutor, TabCompleter {
    private final HardcoreBanBootstrap plugin;
    private final MiniMessage miniMessage;

    /**
     * Creates a new HardcoreBanCommand instance.
     *
     * @param plugin The main plugin instance
     */
    public HardcoreBanCommand(HardcoreBanBootstrap plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            displayHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "check":
                return handleCheckCommand(sender, args);
            case "list":
                return handleListCommand(sender, args);
            case "reset":
                return handleResetCommand(sender, args);
            case "clearall":
                return handleClearAllCommand(sender, args);
            case "debug":
                return handleDebugCommand(sender, args);
            case "sql":
                return handleSqlCommand(sender, args);
            case "directban":
                return handleDirectBanCommand(sender, args);
            default:
                sender.sendMessage(miniMessage.deserialize("<red>Unknown command. Use /hardcoreban for help."));
                return true;
        }
    }

    /**
     * Displays the help message listing all available commands.
     *
     * @param sender The command sender
     */
    private void displayHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<yellow>HardcoreBan commands:"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban check <player> - Check if a player is banned"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban list - List all banned players"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban reset <player> - Remove a player's ban"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban clearall - Remove all bans"));

        if (sender.hasPermission("hardcoreban.admin")) {
            sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban debug - Run database connection tests"));
            sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban sql <query> - Execute a raw SQL query"));
            sender.sendMessage(miniMessage.deserialize("<yellow>/hardcoreban directban <player> - Directly ban a player for testing"));
        }
    }

    /**
     * Handles the "check" command to check if a player is banned.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.check")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /hardcoreban check <player>"));
            return true;
        }

        String playerName = args[1];
        UUID uuid = null;
        String displayName = playerName;

        // Try online player first
        Player target = Bukkit.getPlayer(playerName);
        if (target != null) {
            uuid = target.getUniqueId();
            displayName = target.getName();
        } else {
            // Try offline player
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                uuid = offlinePlayer.getUniqueId();
                displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerName;
            }
        }

        if (uuid == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Player not found. Check the spelling of the name."));
            return true;
        }

        if (plugin.isBanned(uuid)) {
            long timeLeft = plugin.getTimeLeft(uuid);
            String formattedTime = TimeFormatter.formatTime(timeLeft);

            sender.sendMessage(miniMessage.deserialize("<yellow>" + displayName + " is banned for " + formattedTime + "."));
        } else {
            sender.sendMessage(miniMessage.deserialize("<yellow>" + displayName + " is not banned."));
        }

        return true;
    }

    /**
     * Handles the "list" command to list all banned players.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.list")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        Map<UUID, Long> bannedPlayers = plugin.getBannedPlayers();
        if (bannedPlayers.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<yellow>There are no banned players."));
            return true;
        }

        sender.sendMessage(miniMessage.deserialize("<yellow>Banned players (" + bannedPlayers.size() + "):"));

        for (Map.Entry<UUID, Long> entry : bannedPlayers.entrySet()) {
            UUID playerUuid = entry.getKey();
            long expiry = entry.getValue();

            // Try to get player name
            String name;
            Player onlinePlayer = Bukkit.getPlayer(playerUuid);
            if (onlinePlayer != null) {
                name = onlinePlayer.getName();
            } else {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
                name = offlinePlayer.getName();
                if (name == null) name = playerUuid.toString();
            }

            long timeLeft = expiry - System.currentTimeMillis();
            String timeLeftStr = TimeFormatter.formatTimeCompact(timeLeft);

            sender.sendMessage(miniMessage.deserialize("<yellow> - " + name + " - " + timeLeftStr + " remaining"));
        }

        return true;
    }

    /**
     * Handles the "reset" command to remove a player's ban.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleResetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.reset")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /hardcoreban reset <player>"));
            return true;
        }

        String resetPlayerName = args[1];
        UUID resetUuid = null;
        String resetDisplayName = resetPlayerName;

        // First try to get an online player
        Player resetTarget = Bukkit.getPlayer(resetPlayerName);
        if (resetTarget != null) {
            resetUuid = resetTarget.getUniqueId();
            resetDisplayName = resetTarget.getName();
        } else {
            // Try to get an offline player
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(resetPlayerName);
            if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                resetUuid = offlinePlayer.getUniqueId();
                resetDisplayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : resetPlayerName;
            }
        }

        if (resetUuid == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Player not found. Check the spelling of the name."));
            return true;
        }

        if (plugin.isBanned(resetUuid)) {
            plugin.removeBan(resetUuid);
            plugin.log(Level.INFO, sender.getName() + " removed the ban for " + resetDisplayName);
            sender.sendMessage(miniMessage.deserialize("<green>Ban for " + resetDisplayName + " has been removed."));
        } else {
            sender.sendMessage(miniMessage.deserialize("<yellow>" + resetDisplayName + " is not banned."));
        }

        return true;
    }

    /**
     * Handles the "clearall" command to remove all bans.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleClearAllCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.clearall")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        int count = plugin.getBannedPlayers().size();
        plugin.clearAllBans();
        plugin.log(Level.INFO, sender.getName() + " cleared all bans (" + count + " bans removed)");
        sender.sendMessage(miniMessage.deserialize("<green>All bans have been cleared (" + count + " bans removed)."));

        return true;
    }

    /**
     * Handles the "debug" command to test database functionality.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.admin")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        // Check database connection
        boolean dbConnected = plugin.checkDatabaseConnection();
        sender.sendMessage(miniMessage.deserialize("<yellow>Database connection: " +
                (dbConnected ? "<green>CONNECTED" : "<red>DISCONNECTED")));

        // Test adding a temporary debug entry
        if (dbConnected) {
            try {
                UUID debugUuid = UUID.randomUUID();
                boolean addSuccess = plugin.getDatabaseManager().addBan(
                        debugUuid, "DEBUG_ENTRY", System.currentTimeMillis() + 5000);
                sender.sendMessage(miniMessage.deserialize("<yellow>Test add: " +
                        (addSuccess ? "<green>SUCCESS" : "<red>FAILED")));

                boolean checkSuccess = plugin.getDatabaseManager().isBanned(debugUuid);
                sender.sendMessage(miniMessage.deserialize("<yellow>Test check: " +
                        (checkSuccess ? "<green>SUCCESS" : "<red>FAILED")));

                boolean removeSuccess = plugin.getDatabaseManager().removeBan(debugUuid);
                sender.sendMessage(miniMessage.deserialize("<yellow>Test remove: " +
                        (removeSuccess ? "<green>SUCCESS" : "<red>FAILED")));
            } catch (Exception e) {
                sender.sendMessage(miniMessage.deserialize("<red>Error during database test: " + e.getMessage()));
            }
        }

        // Show plugin status
        sender.sendMessage(miniMessage.deserialize("<yellow>Banned players: " + plugin.getBannedPlayers().size()));
        sender.sendMessage(miniMessage.deserialize("<yellow>Plugin version: " + plugin.getPlugin().getDescription().getVersion()));

        return true;
    }

    /**
     * Handles the "sql" command to execute raw SQL queries.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleSqlCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.admin")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /hardcoreban sql <SQL query>"));
            return true;
        }

        // Reconstruct the SQL query from the remaining arguments
        StringBuilder sqlBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sqlBuilder.append(args[i]).append(" ");
        }
        String sql = sqlBuilder.toString().trim();

        plugin.executeRawSql(sql, sender);
        return true;
    }

    /**
     * Handles the "directban" command to ban a player directly for testing.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true always
     */
    private boolean handleDirectBanCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hardcoreban.admin")) {
            sender.sendMessage(miniMessage.deserialize("<red>You don't have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Usage: /hardcoreban directban <player> [minutes]"));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Player not found: " + args[1]));
            return true;
        }

        // Default to 5 minutes if no time specified
        int minutes = 5;
        if (args.length >= 3) {
            try {
                minutes = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(miniMessage.deserialize("<red>Invalid time format. Using default 5 minutes."));
            }
        }

        UUID targetUuid = targetPlayer.getUniqueId();
        long directExpiry = System.currentTimeMillis() + (minutes * 60 * 1000);

        boolean directSuccess = plugin.getDatabaseManager().addBan(
                targetUuid,
                targetPlayer.getName(),
                directExpiry,
                sender.getName(),
                System.currentTimeMillis(),
                "Direct ban test"
        );

        sender.sendMessage(miniMessage.deserialize("<yellow>Direct ban result: " +
                (directSuccess ? "<green>SUCCESS" : "<red>FAILURE")));

        boolean directCheck = plugin.getDatabaseManager().isBanned(targetUuid);
        sender.sendMessage(miniMessage.deserialize("<yellow>Ban verification: " +
                (directCheck ? "<green>BANNED" : "<red>NOT BANNED")));

        if (directSuccess) {
            sender.sendMessage(miniMessage.deserialize("<yellow>Player " + targetPlayer.getName() +
                    " has been banned for " + minutes + " minutes."));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            if (sender.hasPermission("hardcoreban.check")) commands.add("check");
            if (sender.hasPermission("hardcoreban.list")) commands.add("list");
            if (sender.hasPermission("hardcoreban.reset")) commands.add("reset");
            if (sender.hasPermission("hardcoreban.clearall")) commands.add("clearall");
            if (sender.hasPermission("hardcoreban.admin")) {
                commands.add("debug");
                commands.add("sql");
                commands.add("directban");
            }

            for (String cmd : commands) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            // Add player names for commands that need player arguments
            if ((args[0].equalsIgnoreCase("check") && sender.hasPermission("hardcoreban.check")) ||
                    (args[0].equalsIgnoreCase("reset") && sender.hasPermission("hardcoreban.reset")) ||
                    (args[0].equalsIgnoreCase("directban") && sender.hasPermission("hardcoreban.admin"))) {

                // Add online players
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }

                // Add banned offline players for reset command
                if (args[0].equalsIgnoreCase("reset")) {
                    for (UUID uuid : plugin.getBannedPlayers().keySet()) {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        String name = offlinePlayer.getName();
                        if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase()) &&
                                !completions.contains(name)) {
                            completions.add(name);
                        }
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("directban")) {
            // Suggest some common ban durations
            completions.add("5");
            completions.add("10");
            completions.add("30");
            completions.add("60");
            completions.add("1440"); // 24 hours
        }

        return completions;
    }
}