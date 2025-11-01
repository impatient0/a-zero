package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.Trade;
import io.github.impatient0.azero.core.model.TradeDirection;
import io.github.impatient0.azero.core.strategy.Strategy;
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
        Strategy strategy = config.getStrategy();

        log.info("Starting backtest run with initial capital: {}", config.getInitialCapital());
        log.info("Executing strategy: {}", strategy.getClass().getSimpleName());
        log.info("Simulation costs: Fee={}%, Slippage={}%",
            config.getTradingFeePercentage().multiply(BigDecimal.valueOf(100)),
            config.getSlippagePercentage().multiply(BigDecimal.valueOf(100)));

        var context = new BacktestTradingContext(
            config.getInitialCapital(),
            config.getTradingFeePercentage(),
            config.getSlippagePercentage()
        );
        List<Candle> historicalData = config.getHistoricalData();

        for (Candle candle : historicalData) {
            strategy.onCandle(candle, context);
        }

        log.info("Backtest simulation loop completed.");

        Candle lastCandle = historicalData.isEmpty() ? null : historicalData.getLast();
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
         * The standard scale for all price-related calculations.
         */
        private static final int PRICE_SCALE = 8;

        /**
         * The starting capital for the backtest.
         */
        private final BigDecimal initialCapital;
        /**
         * The fee percentage applied to each trade's value.
         */
        private final BigDecimal tradingFeePercentage;
        /**
         * The slippage percentage applied to the execution price.
         */
        private final BigDecimal slippagePercentage;
        /**
         * The current cash balance available for trading.
         */
        private BigDecimal cash;
        /**
         * The currently held position. The simulation supports only one open position at a time.
         */
        private Position openPosition;
        /**
         * A list of all trades that have been closed during the simulation.
         */
        private final List<Trade> executedTrades = new ArrayList<>();

        /**
         * Constructs a new BacktestTradingContext.
         *
         * @param initialCapital       The starting capital for the backtest.
         * @param tradingFeePercentage The fee applied to each trade.
         * @param slippagePercentage   The slippage applied to each trade.
         */
        public BacktestTradingContext(BigDecimal initialCapital, BigDecimal tradingFeePercentage, BigDecimal slippagePercentage) {
            this.initialCapital = initialCapital;
            this.cash = initialCapital;
            this.tradingFeePercentage = tradingFeePercentage;
            this.slippagePercentage = slippagePercentage;
        }

        /**
         * Retrieves the currently open position for a specific symbol.
         *
         * @param symbol The symbol to check for an open position.
         * @return An {@link Optional} containing the {@link Position} if one exists, otherwise an empty Optional.
         */
        @Override
        public Optional<Position> getOpenPosition(String symbol) {
            return (openPosition != null && Objects.equals(openPosition.symbol(), symbol))
                ? Optional.of(openPosition)
                : Optional.empty();
        }

        /**
         * Submits a trading order.
         * <p>
         * This method acts as the primary interface for the strategy to interact with the
         * simulated market. It handles the logic for opening a new position, adding to an
         * existing one (scaling in), or reducing/closing an existing one (scaling out).
         * It enforces a single-open-position rule.
         *
         * @param symbol    The symbol of the asset to trade.
         * @param direction The direction of the trade (LONG or SHORT).
         * @param quantity  The amount of the asset to trade.
         * @param price     The price at which to attempt the trade.
         */
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

        /**
         * Handles the logic for opening a new position.
         * <p>
         * It calculates the execution price including slippage, deducts the total cost
         * (including fees) from the cash balance, and creates the new {@link Position}
         * object. It will not execute if funds are insufficient.
         *
         * @param symbol    The symbol of the asset.
         * @param direction The direction of the position.
         * @param quantity  The quantity to open.
         * @param price     The requested entry price.
         */
        private void openNewPosition(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            BigDecimal executionPrice = applySlippage(price, direction);
            BigDecimal value = executionPrice.multiply(quantity);
            BigDecimal fee = value.multiply(tradingFeePercentage);

            if (direction == TradeDirection.LONG) {
                BigDecimal totalCost = value.add(fee);
                if (cash.compareTo(totalCost) < 0) {
                    log.warn("Insufficient funds to open LONG position for {}. Required: {}, Available: {}", symbol, totalCost, cash);
                    return;
                }
                cash = cash.subtract(totalCost);
            } else { // SHORT
                BigDecimal proceeds = value.subtract(fee);
                cash = cash.add(proceeds);
            }
            // NOTE: The timestamp here is a placeholder. A future task will involve passing the
            // current candle's timestamp through the context for accurate record-keeping.
            openPosition = new Position(symbol, System.currentTimeMillis(), executionPrice, quantity, direction);
            log.info("OPENED {} position for {} @ exec. price {} (orig. price {}).", direction, symbol, executionPrice, price);
        }

        /**
         * Increases the size of an existing position (scales in).
         * <p>
         * This method calculates the cost of the additional quantity, applies fees and
         * slippage, and updates the position's average entry price and total quantity.
         * It will not execute if funds are insufficient to cover the addition.
         *
         * @param additionalQuantity The quantity to add to the position.
         * @param price              The price for the additional quantity.
         */
        private void scaleInPosition(BigDecimal additionalQuantity, BigDecimal price) {
            TradeDirection direction = openPosition.direction();
            BigDecimal executionPrice = applySlippage(price, direction);
            BigDecimal additionalValue = executionPrice.multiply(additionalQuantity);
            BigDecimal fee = additionalValue.multiply(tradingFeePercentage);

            BigDecimal oldTotalValue = openPosition.entryPrice().multiply(openPosition.quantity());
            BigDecimal newTotalQuantity = openPosition.quantity().add(additionalQuantity);

            if (direction == TradeDirection.LONG) {
                BigDecimal totalCost = additionalValue.add(fee);
                if (cash.compareTo(totalCost) < 0) {
                    log.warn("Insufficient funds to scale in LONG position. Required: {}, Available: {}", totalCost, cash);
                    return;
                }
                cash = cash.subtract(totalCost);
            } else { // SHORT
                BigDecimal proceeds = additionalValue.subtract(fee);
                cash = cash.add(proceeds);
            }

            BigDecimal newAveragePrice = oldTotalValue.add(additionalValue)
                .divide(newTotalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
            openPosition = new Position(openPosition.symbol(), openPosition.entryTimestamp(), newAveragePrice, newTotalQuantity, direction);
            log.info("SCALED IN {} position for {} @ exec. price {}. New Avg Price: {}", direction, openPosition.symbol(), executionPrice, newAveragePrice);
        }

        /**
         * Reduces the size of an existing position or closes it entirely (scales out).
         * <p>
         * It calculates the value of the closing portion of the trade, including slippage
         * and fees, and updates the cash balance accordingly. A {@link Trade} record is
         * created for the closed portion. If the entire position is closed, it is removed.
         *
         * @param reduceQuantity The quantity to close. If this is greater than or equal
         *                       to the position size, the entire position is closed.
         * @param price          The price for the closing trade.
         */
        private void scaleOutPosition(BigDecimal reduceQuantity, BigDecimal price) {
            TradeDirection closeDirection = (openPosition.direction() == TradeDirection.LONG) ? TradeDirection.SHORT : TradeDirection.LONG;
            BigDecimal executionPrice = applySlippage(price, closeDirection);
            BigDecimal quantityToClose = reduceQuantity.min(openPosition.quantity());
            BigDecimal value = executionPrice.multiply(quantityToClose);
            BigDecimal fee = value.multiply(tradingFeePercentage);

            if (closeDirection == TradeDirection.SHORT) { // Closing a LONG
                BigDecimal proceeds = value.subtract(fee);
                cash = cash.add(proceeds);
            } else { // Closing a SHORT
                BigDecimal totalCost = value.add(fee);
                if (cash.compareTo(totalCost) < 0) {
                    log.warn("Insufficient funds to close SHORT position. Required: {}, Available: {}", totalCost, cash);
                    return;
                }
                cash = cash.subtract(totalCost);
            }

            Trade trade = new Trade(openPosition.symbol(), openPosition.entryTimestamp(), System.currentTimeMillis(), openPosition.entryPrice(), executionPrice, quantityToClose, openPosition.direction());
            executedTrades.add(trade);
            log.info("SCALED OUT {} of {} position for {} @ exec. price {}.", quantityToClose, openPosition.direction(), openPosition.symbol(), executionPrice);

            BigDecimal remainingQuantity = openPosition.quantity().subtract(quantityToClose);
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                openPosition = new Position(openPosition.symbol(), openPosition.entryTimestamp(), openPosition.entryPrice(), remainingQuantity, openPosition.direction());
            } else {
                openPosition = null;
            }
        }

        /**
         * Applies slippage to a given price based on the order direction.
         * <p>
         * For a LONG order, slippage increases the execution price. For a SHORT order, it
         * decreases the execution price, simulating a worse fill.
         *
         * @param price          The original requested price.
         * @param orderDirection The direction of the order (LONG or SHORT).
         * @return The price adjusted for slippage.
         */
        private BigDecimal applySlippage(BigDecimal price, TradeDirection orderDirection) {
            if (slippagePercentage.compareTo(BigDecimal.ZERO) == 0) {
                return price;
            }
            BigDecimal slippageMultiplier = (orderDirection == TradeDirection.LONG)
                ? BigDecimal.ONE.add(slippagePercentage)
                : BigDecimal.ONE.subtract(slippagePercentage);
            return price.multiply(slippageMultiplier).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }

        /**
         * Calculates the final results of the backtest after the simulation loop is complete.
         * This version correctly applies simulated fees and slippage to the liquidation of
         * any final open position for a more accurate mark-to-market valuation.
         *
         * @param lastCandle The last candle from the historical data, used for mark-to-market valuation.
         * @return The final {@link BacktestResult}.
         */
        public BacktestResult calculateResult(Candle lastCandle) {
            BigDecimal finalValue = cash;

            // If a position is still open at the end, its value is marked-to-market,
            // including the simulated cost of closing it.
            if (openPosition != null && lastCandle != null) {
                log.info("An open {} position for {} exists at the end of the backtest. Calculating mark-to-market value.",
                    openPosition.direction(), openPosition.symbol());

                // Determine the direction and price of the theoretical closing trade.
                TradeDirection closingDirection = (openPosition.direction() == TradeDirection.LONG) ? TradeDirection.SHORT : TradeDirection.LONG;
                BigDecimal liquidationPrice = applySlippage(lastCandle.close(), closingDirection);

                BigDecimal grossValue = liquidationPrice.multiply(openPosition.quantity());
                BigDecimal fee = grossValue.multiply(tradingFeePercentage);

                if (openPosition.direction() == TradeDirection.LONG) {
                    // For a LONG position, the final value is increased by the proceeds from selling it.
                    BigDecimal netProceeds = grossValue.subtract(fee);
                    finalValue = finalValue.add(netProceeds);
                    log.info("Closing LONG position: Liquidation Price={}, Proceeds={}, Fee={}, Net Proceeds={}",
                        liquidationPrice, grossValue, fee, netProceeds);
                } else { // SHORT
                    // For a SHORT position, the final value is decreased by the cost of buying it back.
                    BigDecimal totalCost = grossValue.add(fee);
                    finalValue = finalValue.subtract(totalCost);
                    log.info("Closing SHORT position: Liquidation Price={}, Cost={}, Fee={}, Total Cost={}",
                        liquidationPrice, grossValue, fee, totalCost);
                }
            }

            BigDecimal pnl = finalValue.subtract(initialCapital);
            double pnlPercent = initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? pnl.divide(initialCapital, 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

            log.info("Backtest finished. Initial Capital: {}, Final Value: {}, P/L: {} ({}%)",
                initialCapital, finalValue.setScale(2, RoundingMode.HALF_UP), pnl.setScale(2, RoundingMode.HALF_UP), String.format("%.2f", pnlPercent));

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