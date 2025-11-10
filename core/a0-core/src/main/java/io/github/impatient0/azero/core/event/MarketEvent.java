package io.github.impatient0.azero.core.event;

import io.github.impatient0.azero.core.model.Candle;

/**
 * Represents a single, discrete event from a market data stream.
 * <p>
 * This record encapsulates a {@link Candle} and the {@code symbol} it belongs to,
 * providing a unified and extensible data structure for market updates.
 *
 * @param symbol The trading symbol for which this event occurred (e.g., "BTCUSDT").
 * @param candle The candle data for the event.
 */
public record MarketEvent(
    String symbol,
    Candle candle
) {}