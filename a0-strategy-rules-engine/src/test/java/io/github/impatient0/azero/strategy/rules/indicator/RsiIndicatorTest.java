package io.github.impatient0.azero.strategy.rules.indicator;

import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.strategy.rules.config.RsiIndicatorConfig;
import io.github.impatient0.azero.strategy.rules.config.SignalCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsiIndicatorTest {

    private static final Duration DUMMY_DURATION = Duration.ofHours(1);
    private List<Candle> candles;

    @BeforeEach
    void setUp() {
        // A series of 20 candles following a parabola, with RSI going from low to high.
        candles = new java.util.ArrayList<>();
        long timestamp = 1L;
        BigDecimal basePrice = new BigDecimal("100");
        for (int i = 0; i < 20; i++) {
            BigDecimal currentPrice = basePrice.add(BigDecimal.valueOf((i - 10) * (i - 10)));
            candles.add(new Candle(timestamp++, currentPrice, currentPrice, currentPrice, currentPrice, BigDecimal.TEN));
        }
    }

    @Test
    @DisplayName("WHEN RSI is below threshold, THEN isSignalTriggered for LESS_THAN should be true.")
    void rsi_whenBelowThreshold_shouldTriggerLessThan() {
        // Arrange: RSI(14) will be low at the start. Threshold is 30.
        RsiIndicatorConfig config = new RsiIndicatorConfig(14, SignalCondition.LESS_THAN, new BigDecimal("30"));
        RsiIndicator indicator = new RsiIndicator(config, DUMMY_DURATION);

        // Act: Feed in 15 candles (just enough to be stable).
        candles.subList(0, 15).forEach(indicator::update);

        // Assert
        assertTrue(indicator.isSignalTriggered(), "RSI should be low and trigger a LESS_THAN signal.");
    }

    @Test
    @DisplayName("WHEN RSI is above threshold, THEN isSignalTriggered for GREATER_THAN should be true.")
    void rsi_whenAboveThreshold_shouldTriggerGreaterThan() {
        // Arrange: RSI(14) will be high after a long upward trend. Threshold is 60.
        RsiIndicatorConfig config = new RsiIndicatorConfig(14, SignalCondition.GREATER_THAN, new BigDecimal("60"));
        RsiIndicator indicator = new RsiIndicator(config, DUMMY_DURATION);

        // Act: Feed in all 20 candles.
        candles.forEach(indicator::update);

        // Assert
        assertTrue(indicator.isSignalTriggered(), "RSI should be high and trigger a GREATER_THAN signal.");
    }

    @Test
    @DisplayName("WHEN bar count is not sufficient, THEN isSignalTriggered should be false.")
    void rsi_whenInLookbackPeriod_shouldNotTrigger() {
        // Arrange: RSI period is 14.
        RsiIndicatorConfig config = new RsiIndicatorConfig(14, SignalCondition.GREATER_THAN, new BigDecimal("70"));
        RsiIndicator indicator = new RsiIndicator(config, DUMMY_DURATION);

        // Act: Feed in exactly 14 candles (still in the unstable period).
        candles.subList(0, 14).forEach(indicator::update);

        // Assert
        assertFalse(indicator.isSignalTriggered(), "Signal should not trigger during the lookback period.");
    }
}