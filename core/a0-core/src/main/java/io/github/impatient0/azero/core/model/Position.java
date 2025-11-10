package io.github.impatient0.azero.core.model;

import java.math.BigDecimal;

/**
 * Represents an active, open trading position. It has been entered but not yet exited.
 * <p>
 * This is an immutable data carrier, implemented as a Java Record.
 *
 * @param symbol         The trading pair symbol (e.g., "BTCUSDT").
 * @param entryTimestamp The timestamp of the entry, in milliseconds since the Unix epoch.
 * @param entryPrice     The volume-weighted average price at which the position was entered.
 * @param quantity       The amount of the asset held in the position.
 * @param direction      The direction of the position (LONG or SHORT).
 * @param costBasis      The total value locked or paid to establish this position. The meaning
 *                       of this field depends on the {@link AccountMode}:
 *                       <ul>
 *                         <li>In {@link AccountMode#SPOT_ONLY}, this is the total cash value
 *                             (including fees) paid to acquire the assets.</li>
 *                         <li>In {@link AccountMode#MARGIN}, this is the initial margin (equity)
 *                             locked to open the position.</li>
 *                       </ul>
 */
public record Position(
    String symbol,
    long entryTimestamp,
    BigDecimal entryPrice,
    BigDecimal quantity,
    TradeDirection direction,
    BigDecimal costBasis
) {}