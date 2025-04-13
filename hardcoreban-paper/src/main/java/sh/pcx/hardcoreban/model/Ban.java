package sh.pcx.hardcoreban.model;

import java.util.UUID;

/**
 * Represents a player ban in the HardcoreBan system.
 * This class encapsulates all data related to a player ban.
 */
public class Ban {
    private final UUID uuid;
    private final String playerName;
    private final long expiry;
    private final String bannedBy;
    private final long bannedAt;
    private final String reason;

    /**
     * Creates a new Ban instance with all details specified.
     *
     * @param uuid The UUID of the banned player
     * @param playerName The name of the banned player
     * @param expiry The time (in milliseconds) when the ban expires
     * @param bannedBy The name of who banned the player
     * @param bannedAt The time (in milliseconds) when the ban was created
     * @param reason The reason for the ban
     */
    public Ban(UUID uuid, String playerName, long expiry, String bannedBy, long bannedAt, String reason) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.expiry = expiry;
        this.bannedBy = bannedBy;
        this.bannedAt = bannedAt;
        this.reason = reason;
    }

    /**
     * Creates a new Ban instance for a player who died in hardcore mode.
     * Uses standard death-related values for banned by and reason.
     *
     * @param uuid The UUID of the banned player
     * @param playerName The name of the banned player
     * @param expiry The time (in milliseconds) when the ban expires
     * @return A new Ban instance
     */
    public static Ban createDeathBan(UUID uuid, String playerName, long expiry) {
        return new Ban(uuid, playerName, expiry, "Console", System.currentTimeMillis(), "Death in hardcore mode");
    }

    /**
     * Checks if this ban is currently active.
     *
     * @return true if the ban has not expired, false otherwise
     */
    public boolean isActive() {
        return System.currentTimeMillis() < expiry;
    }

    /**
     * Gets the amount of time left on this ban in milliseconds.
     *
     * @return The time remaining in milliseconds, or 0 if the ban has expired
     */
    public long getTimeLeft() {
        if (!isActive()) {
            return 0;
        }
        return expiry - System.currentTimeMillis();
    }

    /**
     * Gets the UUID of the banned player.
     *
     * @return The UUID of the banned player
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the name of the banned player.
     *
     * @return The name of the banned player
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gets the expiry time of this ban in milliseconds.
     *
     * @return The time when this ban expires
     */
    public long getExpiry() {
        return expiry;
    }

    /**
     * Gets the name of who banned the player.
     *
     * @return The name of who banned the player
     */
    public String getBannedBy() {
        return bannedBy;
    }

    /**
     * Gets the time when this ban was created in milliseconds.
     *
     * @return The time when this ban was created
     */
    public long getBannedAt() {
        return bannedAt;
    }

    /**
     * Gets the reason for this ban.
     *
     * @return The reason for this ban
     */
    public String getReason() {
        return reason;
    }
}