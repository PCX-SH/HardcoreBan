# Changelog

All notable changes to HardcoreBan will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-01-30

### Added
- HikariCP connection pooling for improved database performance and reliability
- GitHub Actions workflows for automated nightly and release builds
- Event priorities for proper listener execution order

### Changed
- Updated to Java 21 (required for Paper 1.21.4+)
- Updated MySQL Connector from 8.0.33 to 9.2.0 (new artifact: `com.mysql:mysql-connector-j`)
- Updated HikariCP from 5.0.1 to 6.2.1
- Updated Guava from 32.1.2-jre to 33.4.0-jre
- Updated GSON from 2.10.1 to 2.11.0 (Velocity module)
- Updated maven-compiler-plugin from 3.11.0 to 3.13.0
- Updated maven-shade-plugin from 3.5.0 to 3.6.0

### Removed
- Manual database heartbeat tasks (HikariCP handles connection validation automatically)
- Deprecated JDBC patterns (`Class.forName()` driver loading, `autoReconnect` URL parameter)

### Fixed
- Database connection timeout issues under load
- Connection leak potential with single connection approach

## [1.0.0] - 2025-01-15

### Added
- Initial release
- Paper plugin for hardcore death ban management
- Velocity plugin for proxy-level ban enforcement
- MySQL/MariaDB database storage
- Configurable ban duration (hours or minutes)
- Spectator mode on death option
- MiniMessage support for all messages
- Admin commands for ban management
- Permission system with bypass capability
- Plugin messaging between Paper and Velocity
