package io.github.impatient0.azero.strategy.rules.loader;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility for parsing timeframe strings (e.g., "1m", "1h", "1d") into Java {@link Duration} objects.
 */
public final class TimeframeParser {
    private static final Pattern TIMEFRAME_PATTERN = Pattern.compile("(\\d+)([mhd])");

    private TimeframeParser() {
    }

    public static Duration parse(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("Timeframe string cannot be null or empty.");
        }
        Matcher matcher = TIMEFRAME_PATTERN.matcher(timeframe.toLowerCase());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid timeframe format: '" + timeframe + "'. Expected format like '1m', '4h', '1d'.");
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalStateException("Unexpected unit: " + unit); // Should not be reached
        };
    }
}