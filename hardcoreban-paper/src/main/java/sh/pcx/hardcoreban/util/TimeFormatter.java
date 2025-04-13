package sh.pcx.hardcoreban.util;

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
     * Formats a time duration based on a configuration-specified unit.
     * Primarily used for formatting ban durations in user messages.
     *
     * @param durationMillis The duration in milliseconds
     * @param unit The time unit ("minutes" or "hours")
     * @return A formatted string like "5 hours" or "30 minutes"
     */
    public static String formatBanTime(long durationMillis, String unit) {
        if ("minutes".equals(unit.toLowerCase())) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis);
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else {
            // Default to hours
            long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
            return hours + " hour" + (hours == 1 ? "" : "s");
        }
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
}