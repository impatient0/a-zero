package io.github.impatient0.azero.core.strategy;

import io.github.impatient0.azero.core.model.Sentiment;

import java.util.Optional;

/**
 * Provides a read-only view of the current market state.
 * <p>
 * This interface isolates data access methods (e.g., retrieving sentiment,
 * volatility, or regime data) from transactional methods found in {@link TradingContext}.
 */
public interface MarketContext {

    /**
     * Retrieves the latest known market sentiment for the specified symbol.
     * <p>
     * This value represents the most recent sentiment signal (derived from external sources
     * like news analysis or LLMs) available at the current point in time.
     * <ul>
     *   <li>In <b>Backtesting</b>: This returns the sentiment from the latest signal
     *       occurring at or before the current simulation timestamp.</li>
     *   <li>In <b>Live Trading</b>: This returns the most recent signal received from
     *       the configured sentiment provider.</li>
     * </ul>
     *
     * @param symbol The trading symbol (e.g., "BTCUSDT") to check.
     * @return an {@link Optional} containing the current {@link Sentiment} (BULLISH, BEARISH, NEUTRAL)
     *         if data is available, or an empty Optional if no sentiment has been established yet.
     */
    Optional<Sentiment> getCurrentSentiment(String symbol);
}