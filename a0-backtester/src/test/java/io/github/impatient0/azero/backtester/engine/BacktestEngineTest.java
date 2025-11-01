package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.backtester.strategy.ConfigurableTestStrategy;
import io.github.impatient0.azero.backtester.util.TestDataFactory;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {

    private BacktestEngine backtestEngine;

    @BeforeEach
    void setUp() {
        backtestEngine = new BacktestEngine();
    }

    @Test
    @DisplayName("A BuyAndHold strategy should execute one buy order and hold the position.")
    void testBuyAndHoldStrategy_HappyPath() {
        // --- ARRANGE ---
        // 1. Define the strategy's actions: Buy 1.0 BTC on the first candle (index 0).
        Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
        actions.add(new ConfigurableTestStrategy.Action(
            0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.0")
        ));
        ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

        // 2. Create market data: Price starts at $20,000 and increases by $100 for 10 candles.
        List<Candle> historicalData = TestDataFactory.createLinearCandleData(10, 20000, 100);
        BigDecimal initialCapital = new BigDecimal("50000");

        // 3. Configure the backtest
        BacktestConfig config = BacktestConfig.builder()
            .historicalData(historicalData)
            .initialCapital(initialCapital)
            .strategy(strategy)
            .build();

        // --- ACT ---
        BacktestResult result = backtestEngine.run(config);

        // --- ASSERT ---
        // 1. Verify no trades were COMPLETED (since the position was never closed).
        assertTrue(result.getExecutedTrades().isEmpty(), "Executed trades list should be empty for a hold strategy.");
        assertEquals(0, result.getTotalTrades());

        // 2. Calculate the expected final portfolio value.
        // Entry cost = 1.0 BTC * $20,000 (price of first candle) = $20,000
        // Cash remaining = $50,000 - $20,000 = $30,000
        // Final price of BTC = $20,000 + 9 * $100 = $20,900 (price of last candle)
        // Value of open position = 1.0 BTC * $20,900 = $20,900
        // Expected Final Value = $30,000 (cash) + $20,900 (position value) = $50,900
        BigDecimal expectedFinalValue = new BigDecimal("50900.00");
        assertEquals(0, expectedFinalValue.compareTo(result.getFinalValue()),
            "Final portfolio value should be correctly calculated via mark-to-market.");

        // 3. Verify Profit & Loss
        BigDecimal expectedPnl = new BigDecimal("900.00"); // $50,900 - $50,000
        assertEquals(0, expectedPnl.compareTo(result.getPnl()), "P/L should be the difference between final and initial capital.");
    }
}