<p align="center">
  <img src="images/BANNER.png" alt="HardcoreBan Banner" width="800">
</p>

<p align="center">
  <a href="https://github.com/PCX-SH/HardcoreBan/actions/workflows/release.yml"><img src="https://github.com/PCX-SH/HardcoreBan/actions/workflows/release.yml/badge.svg" alt="Release Build"></a>
  <a href="https://github.com/PCX-SH/HardcoreBan/actions/workflows/nightly.yml"><img src="https://github.com/PCX-SH/HardcoreBan/actions/workflows/nightly.yml/badge.svg" alt="Nightly Build"></a>
  <a href="https://github.com/PCX-SH/HardcoreBan/releases"><img src="https://img.shields.io/github/v/release/PCX-SH/HardcoreBan?style=flat" alt="Latest Release"></a>
</p>

A comprehensive Minecraft plugin system for managing hardcore mode temporary bans with MySQL database integration.

## Features

- **Temporary Bans**: Automatically bans players for a configurable duration when they die in hardcore mode
- **Proxy Integration**: Works with Velocity to prevent banned players from connecting to the hardcore server
- **Database Storage**: Uses MySQL/MariaDB with HikariCP connection pooling for reliable and scalable ban storage
- **Spigot & Paper Support**: Works on both Spigot and Paper servers with runtime dependency loading
- **Admin Commands**: Full suite of commands for managing bans across both Paper and Velocity
- **Gamemode Reset**: Automatically resets players' gamemode to survival when bans expire
- **Spectator Mode**: Optionally allows players to see their death location briefly before being kicked
- **Configurable Messages**: All messages support MiniMessage format for rich text
- **Permission System**: Granular permissions for admin commands and ban bypass

## Requirements

- Java 21 or higher
- Paper 1.21.5+ or Spigot 1.21.5+
- Velocity 3.4.0 or higher (optional, for proxy support)
- MySQL/MariaDB database server

## Installation

### Paper Plugin

1. Download the `HardcoreBan-Paper.jar` file from the [releases](https://github.com/PCX-SH/HardcoreBan/releases) page
2. Place the JAR file in your Paper server's `plugins` folder
3. Start your server once to generate the default configuration
4. Edit the `plugins/HardcoreBan/config.yml` file to configure database settings
5. Restart your server

### Velocity Plugin

1. Download the `HardcoreBan-Velocity.jar` file from the [releases](https://github.com/PCX-SH/HardcoreBan/releases) page
2. Place the JAR file in your Velocity proxy's `plugins` folder
3. Start your proxy once to generate the default configuration
4. Edit the `plugins/hardcoreban/config.yml` file to configure database settings (ensure they match the Paper settings)
5. Restart your proxy

## Configuration

### Paper Plugin Configuration

```yaml
# HardcoreBan Configuration

# Ban duration settings
ban-duration:
  amount: 24
  unit: "hours"  # "hours" or "minutes"

# The world that is considered hardcore
hardcore-world: "world"

# Whether to affect all worlds or just the specified hardcore world
affect-all-worlds: false

# What gamemode to set players to when their ban expires
reset-gamemode: "SURVIVAL"

# Whether to set player to spectator mode on death
# This allows them to see their death location briefly before being kicked
set-spectator-on-death: true

# How long to wait before kicking a player after death (in ticks, 20 ticks = 1 second)
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

# Logging level
log-level: "INFO"

# Messages (supports MiniMessage format)
messages:
  death-ban: "<red>You died in hardcore mode! You are banned for {time}."
  kick-message: "<red>You died in hardcore mode! You are banned for {time}."
  join-banned: "<red>You are still banned from hardcore mode for {time}."
  gamemode-reset: "<green>Your hardcore ban has expired. Your gamemode has been set to survival."
```

### Velocity Plugin Configuration

```yaml
# HardcoreBan Velocity Configuration

# The name of the server that is running in hardcore mode
hardcore-server: world

# How often to refresh bans from the database (in seconds)
check-interval: 10

# Database configuration (must match Paper plugin settings)
database:
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: password

# Messages (supports MiniMessage format)
messages:
  title-banned: "<red>Hardcore Mode Banned"
  subtitle-banned: "<yellow>Ban expires in {time}"
  chat-banned: "<red>You cannot connect to the hardcore server for {time}."
```

## Commands

### Paper Plugin Commands

| Command | Description |
|---------|-------------|
| `/hardcoreban check <player>` | Check if a player is banned |
| `/hardcoreban list` | List all banned players |
| `/hardcoreban reset <player>` | Remove a player's ban |
| `/hardcoreban clearall` | Remove all bans |
| `/hardcoreban debug` | Run database connection tests |
| `/hardcoreban sql <query>` | Execute a raw SQL query (admin only) |
| `/hardcoreban directban <player>` | Directly ban a player for testing |

### Velocity Plugin Commands

| Command | Description |
|---------|-------------|
| `/vhardcoreban check <player>` | Check if a player is banned |
| `/vhardcoreban list` | List all banned players |
| `/vhardcoreban refresh` | Refresh the ban cache from the database |

## Permissions

### Paper Plugin Permissions

| Permission | Description |
|------------|-------------|
| `hardcoreban.admin` | Grants access to all HardcoreBan commands (default: op) |
| `hardcoreban.check` | Allows checking if a player is banned |
| `hardcoreban.list` | Allows listing all banned players |
| `hardcoreban.reset` | Allows removing a player's ban |
| `hardcoreban.clearall` | Allows removing all bans |
| `hardcoreban.debug` | Allows using debug commands |
| `hardcoreban.sql` | Allows executing raw SQL queries |
| `hardcoreban.bypass` | Players with this permission won't be banned on death |

### Velocity Plugin Permissions

| Permission | Description |
|------------|-------------|
| `hardcoreban.check` | Allows checking if a player is banned |
| `hardcoreban.list` | Allows listing all banned players |
| `hardcoreban.admin` | Allows using admin commands like refresh |

## How It Works

1. The Paper plugin monitors for player deaths in hardcore worlds
2. When a player dies, they are automatically banned for the configured duration
3. The ban is stored in the MySQL database using HikariCP connection pooling
4. If configured, the player is put in spectator mode briefly to see their death location
5. After a short delay, the player is kicked with a ban message
6. The Velocity plugin checks the database when players try to connect
7. Banned players are prevented from joining and shown a title message
8. When a ban expires, the player can rejoin and their gamemode is reset

## Database Schema

The plugin automatically creates a table called `hardcoreban_bans` with the following structure:

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

## Building from Source

```bash
git clone https://github.com/PCX-SH/HardcoreBan.git
cd HardcoreBan
mvn clean package
```

The compiled JARs will be in `hardcoreban-paper/target/` and `hardcoreban-velocity/target/`.

## Troubleshooting

### MySQL Connection Issues

- Ensure the database exists and the MySQL user has appropriate permissions
- Check that the credentials in both Paper and Velocity configs match
- Verify that the MySQL server is reachable from your Minecraft servers
- The plugin uses HikariCP connection pooling which handles reconnections automatically

### Ban Synchronization Issues

- Ensure both plugins are using the same database
- Check that the table structure is correct
- Verify database connection is successful on both sides
- Try using the `/hardcoreban debug` command to test database connectivity
- Use `/vhardcoreban refresh` on Velocity to force a cache refresh

### Performance Considerations

- HikariCP manages a connection pool (default: 2-10 connections) for optimal performance
- Consider adding indexes to the database if you have a large number of bans
- Adjust the check interval based on your server's needs

### Runtime Dependencies (Spigot)

On Spigot servers, the plugin automatically downloads required dependencies on first startup:
- HikariCP 6.2.1
- MySQL Connector/J 9.2.0
- SLF4J API 2.0.16

Dependencies are cached in `plugins/HardcoreBan-Paper/libraries/` and verified with SHA-256 checksums. Paper 1.21+ servers provide these libraries natively, so no download is needed.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
