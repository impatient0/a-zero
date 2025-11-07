package io.github.impatient0.azero.core.model;

/**
 * Defines the accounting and risk model for a trading account.
 * <p>
 * This enum enforces a clear distinction between different types of trading accounts,
 * ensuring that the rules for order execution and portfolio valuation are
 * applied correctly and safely across any execution environment (e.g., backtesting or live trading).
 */
public enum AccountMode {
    /**
     * Represents a standard spot trading account.
     * <ul>
     *   <li>Buying an asset requires the full cash value of the transaction.</li>
     *   <li>Selling an asset requires that the asset is already held in the portfolio.</li>
     *   <li>Short selling is <b>not permitted</b> and will result in an error.</li>
     * </ul>
     */
    SPOT_ONLY,

    /**
     * Represents a margin trading account.
     * <ul>
     *   <li>Both LONG and SHORT positions can be opened using borrowed funds or assets.</li>
     *   <li>Opening a position requires locking a fraction of the position's value
     *       as collateral (margin), determined by the configured leverage.</li>
     *   <li>P/L is realized and collateral is released only when a position is closed.</li>
     * </ul>
     */
    MARGIN
}