package io.github.impatient0.azero.strategy.rules.exit;

import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StopLossRuleTest {

    private static final Candle DUMMY_CANDLE = new Candle(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    @Nested
    @DisplayName("GIVEN a LONG position")
    class LongPositionTests {
        private final Position longPosition = new Position("BTCUSDT", 0L, new BigDecimal("100"), BigDecimal.ONE, TradeDirection.LONG, BigDecimal.ZERO);

        @ParameterizedTest(name = "WHEN price drops to {0}, THEN shouldExit returns {1}")
        @CsvSource({
            "98.0, true",  // Price hits stop loss exactly
            "97.9, true",  // Price drops below stop loss
            "98.1, false", // Price is above stop loss
            "105.0, false" // Price is in profit
        })
        @DisplayName("should trigger correctly based on price drop")
        void testStopLossForLong(String currentPrice, boolean shouldExit) {
            StopLossRule rule = new StopLossRule(2.0); // 2% stop loss
            Candle candle = DUMMY_CANDLE.withClose(new BigDecimal(currentPrice));
            assertEquals(shouldExit, rule.shouldExit(longPosition, candle));
        }
    }

    @Nested
    @DisplayName("GIVEN a SHORT position")
    class ShortPositionTests {
        private final Position shortPosition = new Position("BTCUSDT", 0L, new BigDecimal("100"), BigDecimal.ONE, TradeDirection.SHORT, BigDecimal.ZERO);

        @ParameterizedTest(name = "WHEN price rises to {0}, THEN shouldExit returns {1}")
        @CsvSource({
            "102.0, true", // Price hits stop loss exactly
            "102.1, true", // Price rises above stop loss
            "101.9, false",// Price is below stop loss
            "95.0, false"  // Price is in profit
        })
        @DisplayName("should trigger correctly based on price rise")
        void testStopLossForShort(String currentPrice, boolean shouldExit) {
            StopLossRule rule = new StopLossRule(2.0); // 2% stop loss
            Candle candle = DUMMY_CANDLE.withClose(new BigDecimal(currentPrice));
            assertEquals(shouldExit, rule.shouldExit(shortPosition, candle));
        }
    }
}