# HardcoreBan - Paper Plugin

A comprehensive Minecraft plugin for managing hardcore mode temporary bans with MySQL database integration.

## Features

- **Temporary Bans**: Automatically ban players for a configurable duration when they die in hardcore mode
- **Flexible Ban Duration**: Configure ban duration in minutes or hours
- **Database Storage**: Uses MySQL/MariaDB for reliable and scalable ban storage
- **Admin Commands**: Full suite of commands for managing bans
- **Gamemode Reset**: Automatically reset players' gamemode to survival when bans expire
- **Spectator Mode**: Allow players to see their death location briefly before being kicked
- **Configurable Messages**: All messages support MiniMessage format for rich text
- **Permission System**: Granular permissions for admin commands and ban bypass
- **Multi-World Support**: Configure specific worlds as hardcore or apply to all worlds

## Requirements

- Paper 1.21.4 or higher
- MySQL/MariaDB database server

## Installation

1. Download the `HardcoreBan-Paper.jar` file from the releases section
2. Place the JAR file in your Paper server's `plugins` folder
3. Start your server once to generate the default configuration
4. Edit the `plugins/HardcoreBan/config.yml` file to configure database settings
5. Restart your server

## Configuration

```yaml
# HardcoreBan Configuration

# Ban duration configuration
ban-duration:
  # Time unit: "minutes" or "hours"
  unit: "hours"
  # Amount of time in the specified unit
  amount: 24

# The world that is considered hardcore
# Set to "world" by default
hardcore-world: "world"

# Whether to affect all worlds or just the specified hardcore world
affect-all-worlds: false

# What gamemode to set players to when their ban expires
reset-gamemode: "SURVIVAL"

# Whether to set player to spectator mode on death
# This allows them to see their death location briefly before being kicked
set-spectator-on-death: true

# How long to wait before kicking a player after death (in ticks, 20 ticks = 1 second)
# This only applies if set-spectator-on-death is true
kick-delay-ticks: 60

# How often to check for expired bans (in seconds)
check-interval: 60

# Database configuration
database:
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: password
  table-prefix: ''

# Logging level
# Available levels: OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL
log-level: "INFO"

# Messages (supports MiniMessage format)
messages:
  death-ban: "<red>You died in hardcore mode! You are banned for {time}."
  kick-message: "<red>You died in hardcore mode! You are banned for {time}."
  join-banned: "<red>You are still banned from hardcore mode for {time}."
  gamemode-reset: "<green>Your hardcore ban has expired. Your gamemode has been set to survival."
```

## Commands

- `/hardcoreban check <player>` - Check if a player is banned
- `/hardcoreban list` - List all banned players
- `/hardcoreban reset <player>` - Remove a player's ban
- `/hardcoreban clearall` - Remove all bans
- `/hardcoreban debug` - Run database connection tests
- `/hardcoreban sql <query>` - Execute a raw SQL query (admin only)
- `/hardcoreban directban <player> [minutes]` - Directly ban a player for testing

## Permissions

- `hardcoreban.admin` - Grants access to all HardcoreBan commands (given to operators by default)
- `hardcoreban.check` - Allows checking if a player is banned
- `hardcoreban.list` - Allows listing all banned players
- `hardcoreban.reset` - Allows removing a player's ban
- `hardcoreban.clearall` - Allows removing all bans
- `hardcoreban.debug` - Allows using debug commands
- `hardcoreban.sql` - Allows executing raw SQL queries
- `hardcoreban.bypass` - Players with this permission won't be banned on death

## How It Works

1. The plugin monitors for player deaths in hardcore worlds
2. When a player dies, they are automatically banned for the configured duration
3. The ban is stored in the MySQL database
4. If configured, the player is put in spectator mode briefly to see their death location
5. After a short delay, the player is kicked with a ban message
6. When a ban expires, the player can rejoin and their gamemode is reset

## Database Schema

The plugin creates a table called `hardcoreban_bans` with the following structure:

```sql
CREATE TABLE IF NOT EXISTS hardcoreban_bans (
    uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(36),
    expiry BIGINT,
    banned_by VARCHAR(36),
    banned_at BIGINT,
    reason VARCHAR(255)
);
```

## Troubleshooting

### MySQL Connection Issues

- Ensure the database exists and the MySQL user has appropriate permissions
- Check that the credentials in the config match your MySQL server
- Verify that the MySQL server is reachable from your Minecraft server
- Consider adding `useSSL=false` to the JDBC URL if you experience SSL-related issues

### Ban System Issues

- Check logs for any error messages
- Use `/hardcoreban debug` command to test database connectivity
- Use `/hardcoreban sql "SELECT * FROM hardcoreban_bans"` to inspect the database content
- Try a direct ban with `/hardcoreban directban <player> 5` to test a 5-minute ban

### Gamemode Reset Issues

- Ensure the specified `reset-gamemode` is valid (SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR)
- Check if the player has permissions that might interfere with gamemode changes
- Look for any errors in the logs when gamemode reset attempts occur

## Compatible Plugins

- **HardcoreBan Velocity Plugin**: For integration with Velocity proxy to prevent banned players from connecting to the server