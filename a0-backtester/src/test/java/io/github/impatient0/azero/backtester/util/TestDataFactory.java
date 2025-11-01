package io.github.impatient0.azero.backtester.util;

import io.github.impatient0.azero.core.model.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for creating mock data for unit tests.
 */
public class TestDataFactory {

    /**
     * Generates a simple list of candles with a linearly increasing price.
     *
     * @param count      The number of candles to generate.
     * @param startPrice The closing price of the first candle.
     * @param priceStep  The amount the price increases with each subsequent candle.
     * @return A list of {@link Candle} objects.
     */
    public static List<Candle> createLinearCandleData(int count, double startPrice, double priceStep) {
        List<Candle> candles = new ArrayList<>();
        BigDecimal currentPrice = BigDecimal.valueOf(startPrice);
        long timestamp = 1672531200000L; // 2023-01-01 00:00:00 UTC

        for (int i = 0; i < count; i++) {
            candles.add(new Candle(
                timestamp,
                currentPrice, // open
                currentPrice.add(BigDecimal.TEN), // high
                currentPrice.subtract(BigDecimal.TEN), // low
                currentPrice, // close
                BigDecimal.valueOf(1000) // volume
            ));
            currentPrice = currentPrice.add(BigDecimal.valueOf(priceStep));
            timestamp += 3600_000; // Advance by 1 hour
        }
        return candles;
    }
}