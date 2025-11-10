package io.github.impatient0.azero.backtester.util;

import io.github.impatient0.azero.core.model.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A utility class for creating mock data for unit tests.
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    /**
     * A simple record to define the properties of a single data stream for generation.
     */
    public record StreamDefinition(String symbol, double startPrice, double priceStep) {
    }

    /**
     * Generates a set of synchronized, multi-asset data streams.
     *
     * @param numCandles  The number of candles to generate for each stream.
     * @param definitions A list of definitions for each stream to be created.
     * @return A map where the key is the symbol (e.g., "BTCUSDT") and the value is the list of candles.
     */
    public static Map<String, List<Candle>> createMultiStreamCandleData(int numCandles, List<StreamDefinition> definitions) {
        Map<String, List<Candle>> multiStreamData = new HashMap<>();
        long timestamp = 1672531200000L; // 2023-01-01 00:00:00 UTC

        for (StreamDefinition def : definitions) {
            List<Candle> candles = new ArrayList<>();
            BigDecimal currentPrice = BigDecimal.valueOf(def.startPrice());
            long currentTimestamp = timestamp;

            for (int i = 0; i < numCandles; i++) {
                candles.add(new Candle(
                    currentTimestamp,
                    currentPrice, // open
                    currentPrice.add(BigDecimal.TEN), // high
                    currentPrice.subtract(BigDecimal.TEN), // low
                    currentPrice, // close
                    BigDecimal.valueOf(1000) // volume
                ));
                currentPrice = currentPrice.add(BigDecimal.valueOf(def.priceStep()));
                currentTimestamp += 3600_000; // Advance by 1 hour
            }
            multiStreamData.put(def.symbol(), candles);
        }
        return multiStreamData;
    }

    /**
     * Convenience method to generate data for a single stream.
     *
     * @param symbol     The symbol for the stream (e.g., "BTCUSDT").
     * @param numCandles The number of candles to generate.
     * @param startPrice The starting price.
     * @param priceStep  The amount the price changes per candle.
     * @return A map containing a single entry for the specified symbol.
     */
    public static Map<String, List<Candle>> createSingleStreamCandleData(String symbol, int numCandles, double startPrice, double priceStep) {
        return createMultiStreamCandleData(numCandles, List.of(new StreamDefinition(symbol, startPrice, priceStep)));
    }
}