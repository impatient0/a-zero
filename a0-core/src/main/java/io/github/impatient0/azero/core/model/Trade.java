package io.github.impatient0.azero.core.model;

import java.math.BigDecimal;

/**
 * Represents a completed trade, i.e., a round trip with both an entry and an exit.
 * This is the final record used for performance analysis and logging.
 * <p>
 * This is an immutable data carrier, implemented as a Java Record.
 *
 * @param symbol         The trading pair symbol (e.g., "BTCUSDT").
 * @param entryTimestamp The timestamp of the trade entry, in milliseconds since the Unix epoch.
 * @param exitTimestamp  The timestamp of the trade exit, in milliseconds since the Unix epoch.
 * @param entryPrice     The price at which the position was opened.
 * @param exitPrice      The price at which the position was closed.
 * @param quantity       The amount of the asset that was traded.
 * @param direction      The direction of the trade (LONG or SHORT).
 */
public record Trade(
    String symbol,
    long entryTimestamp,
    long exitTimestamp,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal quantity,
    TradeDirection direction
) {}