package io.github.impatient0.azero.core.model;

import java.math.BigDecimal;

/**
 * Represents an active, open trading position. It has been entered but not yet exited.
 * <p>
 * This is an immutable data carrier, implemented as a Java Record.
 *
 * @param symbol         The trading pair symbol (e.g., "BTCUSDT").
 * @param entryTimestamp The timestamp of the entry, in milliseconds since the Unix epoch.
 * @param entryPrice     The price at which the position was opened.
 * @param quantity       The amount of the asset held in the position.
 * @param direction      The direction of the position (LONG or SHORT).
 * @param collateral     The total amount of cash collateral locked for this position.
 */
public record Position(
    String symbol,
    long entryTimestamp,
    BigDecimal entryPrice,
    BigDecimal quantity,
    TradeDirection direction,
    BigDecimal collateral
) {}