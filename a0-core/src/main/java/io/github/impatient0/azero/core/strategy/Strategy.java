package io.github.impatient0.azero.core.strategy;

import io.github.impatient0.azero.core.model.Candle;

/**
 * The core interface for a trading strategy.
 * <p>
 * Implementations of this interface contain the specific logic for making trading
 * decisions based on incoming market data. The system will feed data to the strategy
 * by invoking the {@link #onCandle(Candle, TradingContext)} method for each new
 * candlestick in the dataset.
 */
public interface Strategy {

    /**
     * This method is called by the trading engine for each incoming candle.
     * It is the heart of the strategy's logic, where decisions to enter or
     * exit positions should be made.
     *
     * @param candle  The most recent {@link Candle} of market data.
     * @param context The {@link TradingContext} which provides access to trading
     *                operations (e.g., opening/closing positions) and state.
     */
    void onCandle(Candle candle, TradingContext context);
}