package sh.pcx.hardcorebanelocity.util;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for formatting time durations in a human-readable format.
 */
public class TimeFormatter {

    /**
     * Formats a time duration in milliseconds into a human-readable string.
     * Format: "X hours, Y minutes" or "Y minutes" if hours is 0.
     *
     * @param millis The time in milliseconds
     * @return A formatted string representation of the time
     */
    public static String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder time = new StringBuilder();

        if (hours > 0) {
            time.append(hours).append(" hour").append(hours == 1 ? "" : "s");
            if (minutes > 0) {
                time.append(", ");
            }
        }

        if (minutes > 0 || hours == 0) {
            time.append(minutes).append(" minute").append(minutes == 1 ? "" : "s");
        }

        return time.toString();
    }

    /**
     * Formats a time duration in milliseconds into a compact format.
     * Format: "5h 30m" or "30m" if hours is 0.
     *
     * @param millis The time in milliseconds
     * @return A compact formatted string representation of the time
     */
    public static String formatTimeCompact(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Formats a duration for display in a title or message.
     * Rounds up to the nearest minute to avoid showing "0 minutes remaining"
     * when there are just a few seconds left.
     *
     * @param millis The time in milliseconds
     * @return A formatted string suitable for display to players
     */
    public static String formatDisplayTime(long millis) {
        // Round up to the nearest minute to avoid showing "0 minutes" when there are a few seconds left
        long minutes = (long) Math.ceil(millis / 60000.0);

        if (minutes <= 0) {
            minutes = 1; // Always show at least 1 minute
        }

        long hours = minutes / 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return hours + " hour" + (hours == 1 ? "" : "s") +
                    (minutes > 0 ? ", " + minutes + " minute" + (minutes == 1 ? "" : "s") : "");
        } else {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
    }
}