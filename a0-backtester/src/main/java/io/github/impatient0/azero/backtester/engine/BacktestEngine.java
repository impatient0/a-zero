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
            // Future task: Call the strategy here.
            // strategy.onCandle(candle, context);
        }

        log.info("Backtest simulation loop completed.");

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
            if (openPosition != null && Objects.equals(openPosition.symbol(), symbol)) {
                return Optional.of(openPosition);
            }
            return Optional.empty();
        }

        @Override
        public void submitOrder(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            if (openPosition == null) {
                openNewPosition(symbol, direction, quantity, price);
            } else if (Objects.equals(openPosition.symbol(), symbol)) {
                if (openPosition.direction() == direction) {
                    scaleInPosition(quantity, price);
                } else {
                    scaleOutPosition(quantity, price);
                }
            } else {
                log.warn("Request to trade symbol {} but a position is already open for {}. Ignoring.",
                    symbol, openPosition.symbol());
            }
        }

        private void openNewPosition(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            BigDecimal cost = price.multiply(quantity);
            if (cash.compareTo(cost) < 0) {
                log.warn("Insufficient funds to open position for {}. Required: {}, Available: {}", symbol, cost, cash);
                return;
            }
            cash = cash.subtract(cost);
            // NOTE: The timestamp here is a placeholder. A future task will involve passing the
            // current candle's timestamp through the context for accurate record-keeping.
            openPosition = new Position(symbol, System.currentTimeMillis(), price, quantity, direction);
            log.info("OPENED {} position for {} @ {}. Cost: {}", direction, symbol, price, cost);
        }

        private void scaleInPosition(BigDecimal additionalQuantity, BigDecimal price) {
            BigDecimal additionalCost = price.multiply(additionalQuantity);
            if (cash.compareTo(additionalCost) < 0) {
                log.warn("Insufficient funds to scale in position for {}. Required: {}, Available: {}",
                    openPosition.symbol(), additionalCost, cash);
                return;
            }
            cash = cash.subtract(additionalCost);

            // Calculate new volume-weighted average price
            BigDecimal oldCost = openPosition.entryPrice().multiply(openPosition.quantity());
            BigDecimal totalQuantity = openPosition.quantity().add(additionalQuantity);
            BigDecimal newAveragePrice = oldCost.add(additionalCost)
                .divide(totalQuantity, 8, RoundingMode.HALF_UP);

            openPosition = new Position(
                openPosition.symbol(),
                openPosition.entryTimestamp(),
                newAveragePrice,
                totalQuantity,
                openPosition.direction()
            );
            log.info("SCALED IN {} position for {} @ {}. New Avg Price: {}, New Qty: {}",
                openPosition.direction(), openPosition.symbol(), price, newAveragePrice, totalQuantity);
        }

        private void scaleOutPosition(BigDecimal reduceQuantity, BigDecimal price) {
            BigDecimal quantityToClose = reduceQuantity.min(openPosition.quantity());

            BigDecimal proceeds = price.multiply(quantityToClose);
            cash = cash.add(proceeds);

            Trade trade = new Trade(
                openPosition.symbol(),
                openPosition.entryTimestamp(),
                System.currentTimeMillis(), // Placeholder timestamp
                openPosition.entryPrice(),
                price,
                quantityToClose,
                openPosition.direction()
            );
            executedTrades.add(trade);
            log.info("SCALED OUT {} of {} position for {} @ {}. Proceeds: {}",
                quantityToClose, openPosition.direction(), openPosition.symbol(), price, proceeds);

            BigDecimal remainingQuantity = openPosition.quantity().subtract(quantityToClose);
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                openPosition = new Position(
                    openPosition.symbol(),
                    openPosition.entryTimestamp(),
                    openPosition.entryPrice(),
                    remainingQuantity,
                    openPosition.direction()
                );
            } else {
                openPosition = null; // Position is fully closed
            }
        }

        /**
         * Calculates the final results of the backtest after the simulation loop is complete.
         *
         * @param lastCandle The last candle from the historical data, used for mark-to-market valuation.
         * @return The final {@link BacktestResult}.
         */
        public BacktestResult calculateResult(Candle lastCandle) {
            BigDecimal finalValue = cash;
            if (openPosition != null && lastCandle != null) {
                BigDecimal openPositionValue = openPosition.quantity().multiply(lastCandle.close());
                finalValue = finalValue.add(openPositionValue);
            }

            BigDecimal pnl = finalValue.subtract(initialCapital);
            double pnlPercent = 0.0;
            if (initialCapital.compareTo(BigDecimal.ZERO) > 0) {
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