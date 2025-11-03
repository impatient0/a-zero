package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.backtester.strategy.ConfigurableTestStrategy;
import io.github.impatient0.azero.backtester.util.TestDataFactory;
import io.github.impatient0.azero.core.model.AccountMode;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Nested
    @DisplayName("GIVEN AccountMode is MARGIN")
    class MarginTests {

        @Test
        @DisplayName("WHEN a short sell is executed, THEN the wallet balances and position cost basis should be correct.")
        void margin_shortSell_WalletAndPositionCorrectness() {
            // --- ARRANGE ---
            // 1. Strategy: Short 0.5 BTC on the first candle.
            Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
            actions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.SHORT, new BigDecimal("0.5")));
            ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

            // 2. Market Data: Price starts at $50,000.
            Map<String, List<Candle>> historicalData = TestDataFactory.createSingleStreamCandleData("BTCUSDT", 5, 50000, -100);
            BigDecimal initialCapital = new BigDecimal("20000"); // In USDT

            // 3. Config: 2x Leverage
            BacktestConfig config = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(initialCapital)
                .strategy(strategy)
                .accountMode(AccountMode.MARGIN)
                .marginLeverage(2)
                .build();

            // --- ACT ---
            // We need access to the context's internal state, so we can't just run the engine.
            // We will manually create the context and call the strategy's method.
            BacktestEngine.BacktestTradingContext context = new BacktestEngine.BacktestTradingContext(
                config.getInitialCapital(),
                config.getTradingFeePercentage(),
                config.getSlippagePercentage(),
                config.getAccountMode(),
                config.getMarginLeverage(),
                config.getMaintenanceMarginFactor()
            );

            Candle firstCandle = historicalData.get("BTCUSDT").get(0);
            Map<String, BigDecimal> prices = Map.of("BTC", firstCandle.close(), "USDT", BigDecimal.ONE);
            context.updateCurrentPrices(prices);
            strategy.onCandle(firstCandle, context);

            // --- ASSERT ---
            // 1. Verify the wallet balances.
            //    - USDT: 20000 (initial) - 12500 (margin locked) + 25000 (proceeds from short) is WRONG.
            //    - Correct USDT: 20000 (initial) - 12500 (margin locked) = 7500.
            //    - We will test this by checking the final NAV in a full run, but for the wallet:
            //      Wallet should contain a liability of -0.5 BTC and proceeds of +25000 USDT.
            BigDecimal balanceBtc = context.getWalletBalanceForTest("BTC");
            BigDecimal balanceUsdt = context.getWalletBalanceForTest("USDT");
            assertEquals(0, new BigDecimal("-0.5").compareTo(balanceBtc), "Wallet should have a liability of -0.5 BTC.");
            assertEquals(0, new BigDecimal("45000").compareTo(balanceUsdt), "Wallet should have 20k initial + 25k proceeds.");


            // 2. Verify the open position's state.
            Optional<Position> positionOpt = context.getOpenPosition("BTCUSDT");
            assertTrue(positionOpt.isPresent(), "A position should be open.");
            Position position = positionOpt.get();

            // Position value = 0.5 * 50000 = 25000
            // Initial Margin (costBasis) = 25000 / 2 (leverage) * 1.02^2 (adjustment factor) = 13005
            BigDecimal expectedMargin = new BigDecimal("13005");
            assertEquals(0, expectedMargin.compareTo(position.costBasis()), "The position's cost basis should be the initial margin locked.");
            assertEquals(0, new BigDecimal("50000").compareTo(position.entryPrice()), "Entry price should be the candle's close.");
            assertEquals(TradeDirection.SHORT, position.direction());
        }

        @Test
        @DisplayName("WHEN a new order is submitted, THEN the Initial Margin Check should accept or reject it correctly.")
        void margin_initialMarginCheck_SuccessAndFailure() {
            // --- ARRANGE (COMMON) ---
            // Replicate the exact scenario from research: Equity of ~$9.00, 10x leverage.
            // We'll start with 9.00 USDT and no other assets.
            BigDecimal initialCapital = new BigDecimal("9.00");
            Map<String, List<Candle>> historicalData = TestDataFactory.createSingleStreamCandleData("BTCUSDT", 5, 20000, 100);

            // --- SCENARIO 1: ACCEPTED TRADE ---
            // 1. Strategy: Attempt to open a position worth ~$72.
            Queue<ConfigurableTestStrategy.Action> acceptActions = new LinkedList<>();
            // A trade of 0.0036 BTC @ $20,000 = $72 value
            acceptActions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("0.0036")));
            ConfigurableTestStrategy acceptStrategy = new ConfigurableTestStrategy(acceptActions);

            BacktestConfig acceptConfig = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(initialCapital)
                .strategy(acceptStrategy)
                .accountMode(AccountMode.MARGIN)
                .marginLeverage(10)
                .build();

            // --- ACT 1 ---
            BacktestResult acceptResult = backtestEngine.run(acceptConfig);

            // --- ASSERT 1 ---
            assertEquals(0, acceptResult.getTotalTrades(), "The position should be open, so no completed trades yet.");
            assertTrue(acceptResult.getFinalValue().compareTo(initialCapital) != 0,
                "The final value should have changed, indicating the trade was accepted.");


            // --- SCENARIO 2: REJECTED TRADE ---
            // 2. Strategy: Attempt to open a position worth ~$85.
            Queue<ConfigurableTestStrategy.Action> rejectActions = new LinkedList<>();
            // A trade of 0.00425 BTC @ $20,000 = $85 value
            rejectActions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("0.00425")));
            ConfigurableTestStrategy rejectStrategy = new ConfigurableTestStrategy(rejectActions);

            BacktestConfig rejectConfig = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(initialCapital)
                .strategy(rejectStrategy)
                .accountMode(AccountMode.MARGIN)
                .marginLeverage(10)
                .build();

            // --- ACT 2 ---
            BacktestResult rejectResult = backtestEngine.run(rejectConfig);

            // --- ASSERT 2 ---
            assertEquals(0, rejectResult.getTotalTrades(), "No trades should be completed for a rejected order.");
            assertEquals(0, initialCapital.compareTo(rejectResult.getFinalValue()),
                "The final value should be unchanged, indicating the trade was rejected.");
        }

        @Test
        @DisplayName("WHEN calculating total equity, THEN it should be correctly calculated based on wallet balances and collateral ratios.")
        void margin_equityCalculation_Correctness() {
            // --- ARRANGE ---
            // 1. Manually construct the context in MARGIN mode.
            BacktestEngine.BacktestTradingContext context = new BacktestEngine.BacktestTradingContext(
                new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO, AccountMode.MARGIN, 10, new BigDecimal("0.5")
            );

            // 2. Set up the portfolio state using the new test utility.
            //    - A LONG position of 2.0 BTC entered at $30,000.
            //    - A SHORT position of 10.0 ETH entered at $2,000.
            //    (Initial capital of 5000 USDT is already in the wallet from the constructor).
            context.setupMarginPositionForTest("BTCUSDT", TradeDirection.LONG,
                new BigDecimal("30000"), new BigDecimal("2.0"),
                BigDecimal.ZERO); // Margin irrelevant for this test
            context.setupMarginPositionForTest("ETHUSDT", TradeDirection.SHORT,
                new BigDecimal("2000"), new BigDecimal("10.0"),
                BigDecimal.ZERO); // Margin irrelevant for this test

            // 3. Define the current market prices (same as entry prices for simplicity).
            Map<String, BigDecimal> currentPrices = Map.of(
                "BTC", new BigDecimal("30000"),
                "ETH", new BigDecimal("2000"),
                "USDT", BigDecimal.ONE
            );
            context.updateCurrentPrices(currentPrices);

            // --- ACT ---
            BigDecimal totalEquity = context.calculateTotalEquity(currentPrices);

            // --- ASSERT ---
            // Formula: Equity = Sum(Positive Assets * Ratio) - Sum(Value of Negative Assets)
            // Wallet State after setup:
            //   - BTC: +2.0
            //   - ETH: -10.0
            //   - USDT: 5000 (initial) - 60000 (paid for BTC) + 20000 (proceeds from ETH) = -35000
            //
            // Positive Asset Value (Collateralized):
            //   - BTC: 2.0 * $30,000 * 0.95 = $57,000
            //
            // Negative Asset Value (Non-Collateralized):
            //   - ETH: 10.0 * $2,000 = $20,000
            //   - USDT: 35000 * $1 = $35,000
            //   - Total Negative = $55,000
            //
            // Expected Equity = $57,000 - $55,000 = $2,000
            BigDecimal expectedEquity = new BigDecimal("2000");
            assertEquals(0, expectedEquity.compareTo(totalEquity),
                "Total equity should be correctly calculated based on the complex wallet state.");
        }

        @Test
        @DisplayName("WHEN equity falls below maintenance margin, THEN the open position should be liquidated.")
        void margin_liquidation_OnPriceDrop_ShouldClosePosition() {
            // --- ARRANGE ---
            // 1. Strategy: Open a 10x leveraged LONG position in BTC on the first candle.
            Queue<ConfigurableTestStrategy.Action> actions = new LinkedList<>();
            // Buy 0.4 BTC @ $25,000 = $10,000 position value.
            actions.add(new ConfigurableTestStrategy.Action(0, "BTCUSDT", TradeDirection.LONG, new BigDecimal("0.4")));
            ConfigurableTestStrategy strategy = new ConfigurableTestStrategy(actions);

            // 2. Market Data: Price starts at $25k, stays flat, then drops sharply.
            // Candle 0: $25,000 (Entry)
            // Candle 1: $25,000
            // Candle 2: $23,000 (This drop should trigger the liquidation)
            List<Candle> candles = List.of(
                new Candle(1L, new BigDecimal("25000"), new BigDecimal("25100"), new BigDecimal("24900"), new BigDecimal("25000"), BigDecimal.TEN),
                new Candle(2L, new BigDecimal("25000"), new BigDecimal("25100"), new BigDecimal("24900"), new BigDecimal("25000"), BigDecimal.TEN),
                new Candle(3L, new BigDecimal("23000"), new BigDecimal("23100"), new BigDecimal("22900"), new BigDecimal("23000"), BigDecimal.TEN)
            );
            Map<String, List<Candle>> historicalData = new HashMap<>();
            historicalData.put("BTCUSDT", candles);

            // 3. Config: 10x Leverage. Start with $1250, just enough to cover Initial Margin.
            BacktestConfig config = BacktestConfig.builder()
                .historicalData(historicalData)
                .initialCapital(new BigDecimal("1250"))
                .strategy(strategy)
                .accountMode(AccountMode.MARGIN)
                .marginLeverage(10)
                .maintenanceMarginFactor(new BigDecimal("0.5")) // MMR is 50% of IMR
                .build();

            // --- ACT ---
            BacktestResult result = backtestEngine.run(config);

            // --- ASSERT ---
            // 1. Verify that one trade was completed (the forced liquidation).
            assertEquals(1, result.getTotalTrades(), "A single liquidation trade should have been executed.");

            // 2. Verify the final Net Asset Value (NAV).
            //    The strategy opens a LONG position worth $10,000 (0.4 * 25,000).
            //    The price then drops to $23,000, causing a margin call.
            //    The position is liquidated at $23,000.
            //    The unrealized P/L at the moment of liquidation is:
            //    (23,000 - 25,000) * 0.4 = -$800.
            //    The final NAV is the initial capital plus the realized P/L from the liquidation.
            //    Final NAV = $1250 (initial) - $800 (loss) = $450.
            BigDecimal expectedFinalValue = new BigDecimal("450.00");
            // Use a small tolerance for the comparison to account for any potential rounding differences
            // in the engine's high-precision BigDecimal calculations.
            assertTrue(result.getFinalValue().subtract(expectedFinalValue).abs().compareTo(new BigDecimal("0.01")) < 0,
                "Final value should be initial capital minus the loss from liquidation.");
        }
    }
}