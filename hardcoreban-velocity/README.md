# HardcoreBan - Velocity Plugin

A Velocity proxy plugin that prevents banned players from connecting to hardcore servers. Works in conjunction with the HardcoreBan Paper plugin.

## Features

- **Proxy Integration**: Prevents banned players from connecting to the hardcore server
- **Database Synchronization**: Uses the same MySQL/MariaDB database as the Paper plugin
- **Visual Feedback**: Shows title and chat messages to banned players
- **Admin Commands**: View and manage bans from the proxy
- **Ban Duration Display**: Shows remaining ban time in a user-friendly format

## Requirements

- Velocity 3.4.0 or higher
- MySQL/MariaDB database server
- HardcoreBan Paper plugin installed on your backend server

## Installation

1. Download the `HardcoreBan-Velocity.jar` file from the releases section
2. Place the JAR file in your Velocity proxy's `plugins` folder
3. Start your proxy once to generate the default configuration
4. Edit the `plugins/hardcoreban/config.yml` file to configure database settings (ensure they match the Paper settings)
5. Restart your proxy

## Configuration

```yaml
# HardcoreBan Velocity Configuration

# The name of the server that is running in hardcore mode
hardcore-server: world

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

- `/vhardcoreban check <player>` - Check if a player is banned
- `/vhardcoreban list` - List all banned players
- `/vhardcoreban refresh` - Refresh the ban cache from the database

## Permissions

- `hardcoreban.check` - Allows checking if a player is banned
- `hardcoreban.list` - Allows listing all banned players
- `hardcoreban.admin` - Allows using admin commands like refresh

## How It Works

1. When a player attempts to connect to the hardcore server, the plugin checks the database
2. If the player is banned, the connection is denied
3. The player is shown a title message and chat message explaining the ban
4. Ban information is regularly refreshed from the database

## Connecting to the Paper Plugin

The Velocity plugin must connect to the same MySQL database as the Paper plugin. This ensures that bans are synchronized across both plugins.

Make sure that:
1. The database configuration matches in both plugins
2. The `hardcore-server` value in the Velocity config matches the server name in Velocity

## Troubleshooting

### MySQL Connection Issues

- Ensure the database exists and the MySQL user has appropriate permissions
- Check that the credentials match the Paper plugin configuration
- Verify that the MySQL server is reachable from your Velocity proxy
- Consider adding `useSSL=false` to the JDBC URL if you experience SSL-related issues

### Server Connection Issues

- Make sure the `hardcore-server` value in the config matches the exact name of your server in Velocity
- If players can still connect despite being banned, make sure both plugins are using the same database
- Check logs for any error messages during connection attempts