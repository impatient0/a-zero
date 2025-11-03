package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.backtester.strategy.ConfigurableTestStrategy;
import io.github.impatient0.azero.backtester.util.TestDataFactory;
import io.github.impatient0.azero.core.model.AccountMode;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.TradeDirection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktestEngineTest {

    private BacktestEngine backtestEngine;

    @BeforeEach
    void setUp() {
        backtestEngine = new BacktestEngine();
    }

    @Nested
    @DisplayName("GIVEN AccountMode is SPOT_ONLY")
    class SpotTests {

        @Test
        @DisplayName("WHEN a round-trip trade (buy then sell) is executed, THEN it should result in one completed trade and correct P/L.")
        void spot_buyAndSell_HappyPath() {
            // --- ARRANGE ---
            // 1. Strategy: Buy 1.0 BTC on candle 0, sell it on candle 2.
            Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
            actions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.0")));
            actions.add(new ConfigurableTestStrategy.Action(2, "BTCUSDT", TradeDirection.SHORT, new BigDecimal("1.0")));
            ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

            // 2. Market Data: Price starts at $20k and increases.
            // Price @ candle 0 (entry): $20,000
            // Price @ candle 2 (exit): $20,200
            Map<String, List<Candle>> historicalData = TestDataFactory.createSingleStreamCandleData("BTCUSDT", 5, 20000, 100);
            BigDecimal initialCapital = new BigDecimal("30000");

            // 3. Config: Explicitly set SPOT_ONLY mode.
            BacktestConfig config = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(initialCapital)
                .strategy(strategy)
                .accountMode(AccountMode.SPOT_ONLY)
                .build();

            // --- ACT ---
            BacktestResult result = backtestEngine.run(config);

            // --- ASSERT ---
            // 1. Verify that exactly one trade was completed.
            assertEquals(1, result.getTotalTrades(), "One round-trip trade should be completed.");

            // 2. Calculate expected final value.
            // Entry cost = 1.0 * $20,000 = $20,000. Cash = $30,000 - $20,000 = $10,000.
            // Exit proceeds = 1.0 * $20,200 = $20,200. Cash = $10,000 + $20,200 = $30,200.
            // Position is closed, so final value is the cash balance.
            BigDecimal expectedFinalValue = new BigDecimal("30200.00");
            assertEquals(0, expectedFinalValue.compareTo(result.getFinalValue()),
                "Final value should reflect the cash balance after the profitable trade.");

            // 3. Verify P/L.
            BigDecimal expectedPnl = new BigDecimal("200.00");
            assertEquals(0, expectedPnl.compareTo(result.getPnl()), "P/L should be the profit from the trade.");
        }

        @Test
        @DisplayName("WHEN a sell order's quantity exceeds the open position, THEN the entire position should be closed.")
        void spot_sellOrder_ExceedingBalance_ShouldClosePosition() {
            // --- ARRANGE ---
            // 1. Strategy: Buy 1.0 BTC on candle 0, then attempt to sell 1.5 BTC on candle 2.
            Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
            actions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.0")));
            actions.add(new ConfigurableTestStrategy.Action(2, "BTCUSDT", TradeDirection.SHORT, new BigDecimal("1.5"))); // Attempt to sell more than owned
            ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

            // 2. Market Data.
            // Price @ candle 0 (entry): $20,000
            // Price @ candle 2 (exit): $20,200
            Map<String, List<Candle>> historicalData = TestDataFactory.createSingleStreamCandleData("BTCUSDT", 5, 20000, 100);
            BigDecimal initialCapital = new BigDecimal("30000");

            // 3. Config: Explicitly set SPOT_ONLY mode.
            BacktestConfig config = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(initialCapital)
                .strategy(strategy)
                .accountMode(AccountMode.SPOT_ONLY)
                .build();

            // --- ACT ---
            BacktestResult result = backtestEngine.run(config);

            // --- ASSERT ---
            // 1. Verify that ONE trade was completed. The engine should have capped the sell order at 1.0 BTC.
            assertEquals(1, result.getTotalTrades(), "The oversized sell order should result in one completed trade.");

            // 2. Calculate the expected final value. The entire position should be closed.
            // Entry cost = 1.0 * $20,000 = $20,000. Cash = $30,000 - $20,000 = $10,000.
            // Exit proceeds = 1.0 * $20,200 = $20,200. Cash = $10,000 + $20,200 = $30,200.
            // Position is now closed, so final value is the final cash balance.
            BigDecimal expectedFinalValue = new BigDecimal("30200.00");
            assertEquals(0, expectedFinalValue.compareTo(result.getFinalValue()),
                "Final value should reflect the profit from closing the entire 1.0 BTC position.");
        }

        @Test
        @DisplayName("WHEN a strategy attempts to SELL an asset with no open position, THEN the order should be ignored.")
        void spot_sellAttempt_WithNoPosition_ShouldBeIgnored() {
            // --- ARRANGE ---
            // 1. Strategy: Attempt to sell 1.0 BTC on the first candle, without ever buying.
            Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
            actions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.SHORT, new BigDecimal("1.0")));
            ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

            // 2. Market Data and Capital.
            Map<String, List<Candle>> historicalData = TestDataFactory.createSingleStreamCandleData("BTCUSDT", 5, 20000, 100);
            BigDecimal initialCapital = new BigDecimal("30000");

            // 3. Config: Explicitly set SPOT_ONLY mode.
            BacktestConfig config = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(initialCapital)
                .strategy(strategy)
                .accountMode(AccountMode.SPOT_ONLY)
                .build();

            // --- ACT ---
            BacktestResult result = backtestEngine.run(config);

            // --- ASSERT ---
            // 1. Verify that no trades were completed.
            assertEquals(0, result.getTotalTrades(), "No trades should have been executed.");

            // 2. Verify that the portfolio value is unchanged.
            assertEquals(0, initialCapital.compareTo(result.getFinalValue()),
                "Final value should be equal to initial capital as the order was ignored.");

            // 3. Verify that the Profit & Loss is zero.
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getPnl()),
                "P/L should be zero.");
        }
    }
}