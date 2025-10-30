package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.Trade;
import io.github.impatient0.azero.core.model.TradeDirection;
import io.github.impatient0.azero.core.strategy.TradingContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The core engine for running backtest simulations.
 * <p>
 * This class orchestrates the simulation by iterating through historical data and
 * feeding it to a trading strategy. It manages the state of the portfolio (cash,
 * positions) and records all trades executed during the run.
 */
@Slf4j
public class BacktestEngine {

    /**
     * Executes a backtest simulation based on the provided configuration.
     *
     * @param config The {@link BacktestConfig} containing all parameters for the run.
     * @return A {@link BacktestResult} object summarizing the performance of the simulation.
     */
    public BacktestResult run(BacktestConfig config) {
        log.info("Starting backtest run with initial capital: {}", config.getInitialCapital());

        var context = new BacktestTradingContext(config.getInitialCapital());
        List<Candle> historicalData = config.getHistoricalData();

        // --- Core Simulation Loop ---
        for (Candle candle : historicalData) {
            // In a future step, this is where we will call the strategy:
            // strategy.onCandle(candle, context);

            // For now, the context's internal state is only modified when a strategy
            // calls its methods. This loop just advances the simulation time.
        }

        log.info("Backtest simulation loop completed.");

        // Mark-to-market any open position using the last available price.
        Candle lastCandle = historicalData.isEmpty() ? null : historicalData.get(historicalData.size() - 1);
        return context.calculateResult(lastCandle);
    }

    /**
     * An internal implementation of the {@link TradingContext} interface, tailored for backtesting.
     * <p>
     * This class is responsible for all state management within a single backtest run.
     * It simulates the behavior of a real trading account by tracking cash,
     * open positions, and executed trades in memory.
     */
    private static class BacktestTradingContext implements TradingContext {

        /**
         * The initial capital at the start of the simulation.
         */
        private final BigDecimal initialCapital;
        /**
         * The current cash balance of the portfolio.
         */
        private BigDecimal cash;
        /**
         * The currently open position. This implementation assumes only one position
         * can be open at any given time.
         */
        private Position openPosition;
        /**
         * A list of all trades that have been completed (both entry and exit).
         */
        private final List<Trade> executedTrades = new ArrayList<>();

        public BacktestTradingContext(BigDecimal initialCapital) {
            this.initialCapital = initialCapital;
            this.cash = initialCapital;
        }

        @Override
        public Optional<Position> getOpenPosition(String symbol) {
            // This implementation only supports one open position at a time,
            // so we check if the requested symbol matches the open position.
            if (openPosition != null && Objects.equals(openPosition.symbol(), symbol)) {
                return Optional.of(openPosition);
            }
            return Optional.empty();
        }

        @Override
        public void openPosition(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            if (openPosition != null) {
                log.warn("Request to open a new position for {} while one is already open for {}. Ignoring.",
                    symbol, openPosition.symbol());
                return;
            }

            BigDecimal cost = price.multiply(quantity);
            if (cash.compareTo(cost) < 0) {
                log.warn("Insufficient funds to open position for {}. Required: {}, Available: {}", symbol, cost, cash);
                return;
            }

            // Deduct cost from cash and create the new position record.
            cash = cash.subtract(cost);
            openPosition = new Position(symbol, System.currentTimeMillis(), price, quantity, direction); // Timestamp will be refined later
            log.info("Opened {} position for {} @ {}. Cost: {}", direction, symbol, price, cost);
        }

        @Override
        public void closePosition(String symbol, BigDecimal price) {
            if (getOpenPosition(symbol).isEmpty()) {
                log.warn("Request to close position for {}, but no position is open. Ignoring.", symbol);
                return;
            }

            BigDecimal proceeds = price.multiply(openPosition.quantity());

            // Add proceeds to cash balance.
            cash = cash.add(proceeds);

            // Create a completed trade record.
            Trade trade = new Trade(
                openPosition.symbol(),
                openPosition.entryTimestamp(),
                System.currentTimeMillis(), // Timestamp will be refined later
                openPosition.entryPrice(),
                price,
                openPosition.quantity(),
                openPosition.direction()
            );
            executedTrades.add(trade);

            log.info("Closed {} position for {} @ {}. Proceeds: {}", openPosition.direction(), symbol, price, proceeds);

            // Clear the open position.
            openPosition = null;
        }

        /**
         * Calculates the final results of the backtest after the simulation loop is complete.
         *
         * @param lastCandle The last candle from the historical data, used for mark-to-market valuation.
         * @return The final {@link BacktestResult}.
         */
        public BacktestResult calculateResult(Candle lastCandle) {
            BigDecimal finalValue = cash;
            // If a position is still open at the end, its value is marked-to-market.
            if (openPosition != null && lastCandle != null) {
                BigDecimal openPositionValue = openPosition.quantity().multiply(lastCandle.close());
                finalValue = finalValue.add(openPositionValue);
            }

            BigDecimal pnl = finalValue.subtract(initialCapital);
            double pnlPercent = 0.0;
            if (initialCapital.compareTo(BigDecimal.ZERO) != 0) {
                pnlPercent = pnl.divide(initialCapital, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            }

            log.info("Backtest finished. Initial Capital: {}, Final Value: {}, P/L: {} ({}%)",
                initialCapital, finalValue, pnl, String.format("%.2f", pnlPercent));

            return BacktestResult.builder()
                .finalValue(finalValue)
                .pnl(pnl)
                .pnlPercent(pnlPercent)
                .executedTrades(List.copyOf(executedTrades))
                .totalTrades(executedTrades.size())
                .build();
        }
    }
}