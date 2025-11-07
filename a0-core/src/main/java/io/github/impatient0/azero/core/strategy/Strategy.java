package io.github.impatient0.azero.core.strategy;

import io.github.impatient0.azero.core.event.MarketEvent;

/**
 * The core interface for a trading strategy.
 * <p>
 * Implementations of this interface contain the specific logic for making trading
 * decisions based on incoming market events. The system will feed data to the strategy
 * by invoking the {@link #onMarketEvent(MarketEvent, TradingContext)} method for each new
 * piece of market data.
 */
public interface Strategy {

    /**
     * This method is called by the trading engine for each incoming market event.
     * It is the heart of the strategy's logic, where decisions to enter or
     * exit positions should be made.
     *
     * @param event   The {@link MarketEvent} containing the latest market data (e.g., a candle).
     * @param context The {@link TradingContext} which provides access to trading
     *                operations (e.g., opening/closing positions) and state.
     */
    void onMarketEvent(MarketEvent event, TradingContext context);
}