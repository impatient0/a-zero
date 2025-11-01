package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.backtester.strategy.ConfigurableTestStrategy;
import io.github.impatient0.azero.backtester.util.TestDataFactory;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.TradeDirection;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    @Test
    @DisplayName("An order with a cost exceeding available cash should be ignored.")
    void testOpenPosition_WithInsufficientFunds_ShouldBeIgnored() {
        // --- ARRANGE ---
        // 1. Define the strategy: Attempt to buy 1.0 BTC on the first candle.
        Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
        actions.add(new ConfigurableTestStrategy.Action(
            0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.0")
        ));
        ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

        // 2. Create market data where 1.0 BTC costs $20,000.
        List<Candle> historicalData = TestDataFactory.createLinearCandleData(5, 20000, 100);

        // 3. Set initial capital to be LESS than the cost of the trade.
        BigDecimal initialCapital = new BigDecimal("19999.99");

        BacktestConfig config = BacktestConfig.builder()
            .historicalData(historicalData)
            .initialCapital(initialCapital)
            .strategy(strategy)
            .build();

        // --- ACT ---
        BacktestResult result = backtestEngine.run(config);

        // --- ASSERT ---
        // 1. Verify that no trades were completed.
        assertTrue(result.getExecutedTrades().isEmpty(), "No trades should be executed.");
        assertEquals(0, result.getTotalTrades());

        // 2. Verify that the portfolio value is unchanged, as no position was opened.
        assertEquals(0, initialCapital.compareTo(result.getFinalValue()),
            "Final value should be equal to initial capital if no position was opened.");

        // 3. Verify that the Profit & Loss is zero.
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPnl()),
            "P/L should be zero when no trades are made.");
    }

    @Test
    @DisplayName("An order for a new symbol should be ignored if another position is already open.")
    void testOpenPosition_OnNewSymbol_WhileAnotherIsOpen_ShouldBeIgnored() {
        // --- ARRANGE ---
        // 1. Define the strategy:
        //    - On candle 0, buy 1.0 BTC.
        //    - On candle 1, attempt to buy 10 ETH.
        Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
        actions.add(new ConfigurableTestStrategy.Action(
            0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.0")
        ));
        actions.add(new ConfigurableTestStrategy.Action(
            1, "ETHUSDT", TradeDirection.LONG, new BigDecimal("10.0")
        ));
        ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

        // 2. Create market data where BTC price starts at $20k and ETH starts at $1.5k (irrelevant for this test).
        List<Candle> historicalData = TestDataFactory.createLinearCandleData(5, 20000, 100);
        BigDecimal initialCapital = new BigDecimal("50000");

        BacktestConfig config = BacktestConfig.builder()
            .historicalData(historicalData)
            .initialCapital(initialCapital)
            .strategy(strategy)
            .build();

        // --- ACT ---
        BacktestResult result = backtestEngine.run(config);

        // --- ASSERT ---
        // 1. Verify no trades were COMPLETED.
        assertTrue(result.getExecutedTrades().isEmpty(), "Executed trades list should be empty.");

        // 2. Calculate the expected final value based ONLY on the first (BTC) trade.
        // The second (ETH) trade should have been ignored.
        // Entry cost (BTC) = 1.0 * $20,000 = $20,000
        // Cash remaining = $50,000 - $20,000 = $30,000
        // Final price of BTC = $20,000 + 4 * $100 = $20,400 (last of 5 candles)
        // Value of open position = 1.0 * $20,400 = $20,400
        // Expected Final Value = $30,000 (cash) + $20,400 (position value) = $50,400
        BigDecimal expectedFinalValue = new BigDecimal("50400.00");
        assertEquals(0, expectedFinalValue.compareTo(result.getFinalValue()),
            "Final value should only reflect the first trade, as the second should be ignored.");

        // 3. Verify P/L reflects only the BTC position's performance.
        BigDecimal expectedPnl = new BigDecimal("400.00");
        assertEquals(0, expectedPnl.compareTo(result.getPnl()),
            "P/L should be calculated based only on the single open BTC position.");
    }

    @Test
    @DisplayName("A scale-out order exceeding position size should just close the position.")
    void testScaleOut_ExceedingPositionSize_ShouldClosePosition() {
        // --- ARRANGE ---
        // 1. Define the strategy:
        //    - On candle 0, buy 1.5 BTC.
        //    - On candle 2, attempt to sell 2.0 BTC (more than is held).
        Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
        actions.add(new ConfigurableTestStrategy.Action(
            0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.5")
        ));
        actions.add(new ConfigurableTestStrategy.Action(
            2, "BTCUSDT", TradeDirection.SHORT, new BigDecimal("2.0") // Attempt to sell more than owned
        ));
        ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

        // 2. Create market data.
        // Price @ candle 0 (entry): $20,000
        // Price @ candle 2 (exit): $20,000 + 2 * $100 = $20,200
        List<Candle> historicalData = TestDataFactory.createLinearCandleData(5, 20000, 100);
        BigDecimal initialCapital = new BigDecimal("50000");

        BacktestConfig config = BacktestConfig.builder()
            .historicalData(historicalData)
            .initialCapital(initialCapital)
            .strategy(strategy)
            .build();

        // --- ACT ---
        BacktestResult result = backtestEngine.run(config);

        // --- ASSERT ---
        // 1. Verify that exactly ONE trade was completed and recorded.
        assertEquals(1, result.getTotalTrades(), "One trade should have been completed.");

        // 2. Calculate the expected final portfolio value. The engine should have sold only the 1.5 BTC that was held.
        // Entry cost = 1.5 * $20,000 = $30,000
        // Cash after entry = $50,000 - $30,000 = $20,000
        // Proceeds from exit = 1.5 * $20,200 = $30,300
        // Final Cash = $20,000 + $30,300 = $50,300
        // Since the position is closed, the final portfolio value is just the final cash.
        BigDecimal expectedFinalValue = new BigDecimal("50300.00");
        assertEquals(0, expectedFinalValue.compareTo(result.getFinalValue()),
            "Final value should reflect the profit from closing the entire position.");

        // 3. Verify P/L.
        BigDecimal expectedPnl = new BigDecimal("300.00"); // $50,300 - $50,000
        assertEquals(0, expectedPnl.compareTo(result.getPnl()),
            "P/L should be the profit from the single round-trip trade.");
    }

    @Test
    @DisplayName("Should correctly handle closing a position and opening a new one on a different symbol.")
    void testSequentialTrades_OnDifferentSymbols() {
        // --- ARRANGE ---
        // 1. Define the strategy:
        //    - On candle 0, buy 1.0 BTC.
        //    - On candle 2, close the BTC position.
        //    - On candle 3, buy 0.01 ETH.
        Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
        actions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("1.0")));
        actions.add(new ConfigurableTestStrategy.Action(2, "BTCUSDT", TradeDirection.SHORT, new BigDecimal("1.0")));
        actions.add(new ConfigurableTestStrategy.Action(3, "ETHUSDT", TradeDirection.LONG, new BigDecimal("0.01")));
        ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

        // 2. Create market data.
        // Price @ candle 0 (BTC entry): $20,000
        // Price @ candle 2 (BTC exit): $20,200
        // Price @ candle 3 (ETH entry): $2,030 (using a more realistic ETH price)
        // Price @ candle 4 (Final ETH): $2,040
        List<Candle> btcData = TestDataFactory.createLinearCandleData(3, 20000, 100);
        List<Candle> ethData = TestDataFactory.createLinearCandleData(2, 2030, 10);
        List<Candle> historicalData = new ArrayList<>();
        historicalData.addAll(btcData);
        historicalData.addAll(ethData);

        BigDecimal initialCapital = new BigDecimal("30000");

        BacktestConfig config = BacktestConfig.builder()
            .historicalData(historicalData)
            .initialCapital(initialCapital)
            .strategy(strategy)
            .build();

        // --- ACT ---
        BacktestResult result = backtestEngine.run(config);

        // --- ASSERT ---
        // 1. Verify the first (BTC) trade was completed and recorded.
        assertEquals(1, result.getTotalTrades(), "The round-trip BTC trade should be recorded.");

        // 2. Calculate the expected final portfolio value step-by-step.
        // Trade 1 (BTC):
        // BTC entry cost = 1.0 * $20,000 = $20,000. Cash = $30,000 - $20,000 = $10,000.
        // BTC exit proceeds = 1.0 * $20,200 = $20,200. Cash = $10,000 + $20,200 = $30,200.
        //
        // Trade 2 (ETH):
        // ETH entry cost = 0.01 * $2,030 = $20.30. Cash = $30,200 - $20.30 = $30,179.70.
        //
        // Final State:
        // Cash = $30,179.70
        // Value of open ETH position (mark-to-market) = 0.01 * $2,040 = $20.40
        // Final Portfolio Value = $30,179.70 + $20.40 = $30,200.10
        BigDecimal expectedFinalValue = new BigDecimal("30200.10");
        assertEquals(0, expectedFinalValue.compareTo(result.getFinalValue()),
            "Final value should reflect the profit from the BTC trade and the value of the open ETH position.");

        // 3. Verify P/L.
        BigDecimal expectedPnl = new BigDecimal("200.10"); // $30,200.10 - $30,000
        assertEquals(0, expectedPnl.compareTo(result.getPnl()),
            "P/L should be the sum of realized (BTC) and unrealized (ETH) profit.");
    }
}