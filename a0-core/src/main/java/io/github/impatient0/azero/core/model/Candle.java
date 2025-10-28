package io.github.impatient0.azero.core.model;

import java.math.BigDecimal;

/**
 * Represents a single financial candlestick (K-line), providing a summary
 * of market price movement over a specific time interval.
 * <p>
 * This is an immutable data carrier, implemented as a Java Record.
 *
 * @param timestamp The start time of the candle period, in milliseconds since the Unix epoch.
 * @param open      The price at the beginning of the period.
 * @param high      The highest price reached during the period.
 * @param low       The lowest price reached during the period.
 * @param close     The price at the end of the period.
 * @param volume    The total volume of the asset traded during the period.
 */
public record Candle(
    long timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}