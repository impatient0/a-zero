package io.github.impatient0.azero.strategy.rules.indicator;

import io.github.impatient0.azero.core.model.Candle;

/**
 * An interface representing a single, stateful technical indicator rule.
 * <p>
 * Implementations of this interface encapsulate the logic for a specific
 * technical indicator (e.g., RSI, EMA Cross), track its value over time,
 * and determine when a specific signal condition is met.
 * <p>
 * The expected usage is to call {@link #update(Candle)} sequentially for each candle,
 * and then call {@link #isSignalTriggered()} to check the rule's current state.
 */
public interface Indicator {

    /**
     * Updates the indicator with the latest market data.
     * This method should be called for each new candle in the series.
     *
     * @param candle The latest {@link Candle} data.
     */
    void update(Candle candle);

    /**
     * Checks if the indicator's defined signal condition is currently met.
     * <p>
     * It is expected that this method will return {@code false} during the initial
     * lookback period before the indicator has received enough data to be meaningful.
     *
     * @return {@code true} if the signal is triggered, otherwise {@code false}.
     */
    boolean isSignalTriggered();

    /**
     * Returns the number of candles required by this indicator before it can produce a stable value.
     * <p>
     * For example, an RSI with a period of 14 will have a lookback period of 14. An
     * indicator for an EMA(50) crossing an EMA(200) will have a lookback period of 200.
     *
     * @return The number of candles in the indicator's lookback period.
     */
    int getLookbackPeriod();
}