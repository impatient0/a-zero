package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.core.model.AccountMode;
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
            config.getSlippagePercentage(),
            config.getAccountMode(),
            config.getMarginLeverage()
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
         *  Margin leverage value used for SHORT positions.
         */
        private final int marginLeverage;
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
         * Account mode (SPOT_ONLY/MARGIN) used for the simulation.
         */
        private final AccountMode accountMode;

        /**
         * Constructs a new BacktestTradingContext.
         *
         * @param initialCapital       The starting capital for the backtest.
         * @param marginLeverage       Margin leverage value.
         * @param tradingFeePercentage The fee applied to each trade.
         * @param slippagePercentage   The slippage applied to each trade.
         */
        public BacktestTradingContext(BigDecimal initialCapital, BigDecimal tradingFeePercentage,
            BigDecimal slippagePercentage, AccountMode accountMode, int marginLeverage) {
            this.initialCapital = initialCapital;
            this.cash = initialCapital;
            this.tradingFeePercentage = tradingFeePercentage;
            this.slippagePercentage = slippagePercentage;
            this.accountMode = accountMode;
            this.marginLeverage = Math.max(1, marginLeverage);
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
            switch (accountMode) {
                case SPOT_ONLY:
                    openNewPositionSpot(symbol, direction, quantity, price);
                    break;
                case MARGIN:
                    openNewPositionMargin(symbol, direction, quantity, price);
                    break;
            }
        }

        /**
         * Handles the logic for scaling into an existing position.
         * <p>
         * For both LONG and SHORT positions, it calculates a new volume-weighted
         * average price. It deducts the appropriate cost (full value for LONGs,
         * only collateral for SHORTs) and updates the total collateral locked for
         * the position.
         *
         * @param additionalQuantity The quantity to add to the position.
         * @param price              The price at which to add to the position.
         */
        private void scaleInPosition(BigDecimal additionalQuantity, BigDecimal price) {
            switch (accountMode) {
                case SPOT_ONLY:
                    scaleInPositionSpot(additionalQuantity, price);
                    break;
                case MARGIN:
                    scaleInPositionMargin(additionalQuantity, price);
                    break;
            }
        }

        /**
         * Reduces the size of an existing position or closes it entirely (scales out).
         * <p>
         * It calculates the value of the closing portion of the trade, including slippage
         * and fees, and updates the cash balance accordingly. A {@link Trade} record is
         * created for the closed portion. For a partial close, the state of the remaining
         * position (including its collateral) is correctly updated. If the entire position
         * is closed, it is removed.
         *
         * @param reduceQuantity The quantity to close. If this is greater than or equal
         *                       to the position size, the entire position is closed.
         * @param price          The price for the closing trade.
         */
        private void scaleOutPosition(BigDecimal reduceQuantity, BigDecimal price) {
            switch (accountMode) {
                case SPOT_ONLY:
                    scaleOutPositionSpot(reduceQuantity, price);
                    break;
                case MARGIN:
                    scaleOutPositionMargin(reduceQuantity, price);
                    break;
            }
        }

        private void openNewPositionSpot(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            if (direction == TradeDirection.SHORT) {
                throw new IllegalStateException("Short selling is not permitted in SPOT_ONLY account mode.");
            }
            // Spot LONG buys are cash-based
            BigDecimal executionPrice = applySlippage(price, TradeDirection.LONG);
            BigDecimal value = executionPrice.multiply(quantity);
            BigDecimal fee = value.multiply(tradingFeePercentage);
            BigDecimal totalCost = value.add(fee);

            if (cash.compareTo(totalCost) < 0) {
                log.warn("Insufficient funds to open SPOT position for {}. Required: {}, Available: {}", symbol, totalCost, cash);
                return;
            }
            cash = cash.subtract(totalCost);
            openPosition = new Position(symbol, System.currentTimeMillis(), executionPrice, quantity, direction, totalCost);
            log.info("OPENED {} {} position for {} @ exec. price {} (orig. price {}).",
                direction, quantity, symbol, executionPrice, price);
        }

        private void scaleInPositionSpot(BigDecimal additionalQuantity, BigDecimal price) {
            // Scaling in is the same as opening a new position for cost calculation
            BigDecimal executionPrice = applySlippage(price, TradeDirection.LONG);
            BigDecimal additionalValue = executionPrice.multiply(additionalQuantity);
            BigDecimal fee = additionalValue.multiply(tradingFeePercentage);
            BigDecimal additionalCost = additionalValue.add(fee);

            if (cash.compareTo(additionalCost) < 0) {
                log.warn("Insufficient funds to scale in SPOT position. Required: {}, Available: {}", additionalCost, cash);
                return;
            }
            cash = cash.subtract(additionalCost);

            // Update the position with a new average price and total cost (collateral)
            BigDecimal oldTotalValue = openPosition.entryPrice().multiply(openPosition.quantity());
            BigDecimal newTotalQuantity = openPosition.quantity().add(additionalQuantity);
            BigDecimal newAveragePrice = oldTotalValue.add(additionalValue)
                .divide(newTotalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal newTotalCollateral = openPosition.collateral().add(additionalCost);

            openPosition = new Position(openPosition.symbol(), openPosition.entryTimestamp(), newAveragePrice, newTotalQuantity, openPosition.direction(), newTotalCollateral);
            log.info("SCALED IN {} {} position for {} @ exec. price {}. New Avg Price: {}",
                openPosition.direction(), additionalQuantity, openPosition.symbol(), executionPrice, newAveragePrice);
        }

        private void scaleOutPositionSpot(BigDecimal reduceQuantity, BigDecimal price) {
            // Safety check: In SPOT mode, you cannot sell more than you own.
            if (reduceQuantity.compareTo(openPosition.quantity()) > 0) {
                log.warn("Attempted to sell {} but only hold {}. Adjusting sell quantity to position size.",
                    reduceQuantity, openPosition.quantity());
            }
            BigDecimal quantityToClose = reduceQuantity.min(openPosition.quantity());

            // In SPOT, selling is a simple cash transaction.
            BigDecimal executionPrice = applySlippage(price, TradeDirection.SHORT); // Selling is a SHORT action
            BigDecimal value = executionPrice.multiply(quantityToClose);
            BigDecimal fee = value.multiply(tradingFeePercentage);
            BigDecimal proceeds = value.subtract(fee);
            cash = cash.add(proceeds);

            // Create trade record
            Trade trade = new Trade(openPosition.symbol(), openPosition.entryTimestamp(), System.currentTimeMillis(), openPosition.entryPrice(), executionPrice, quantityToClose, openPosition.direction());
            executedTrades.add(trade);
            log.info("SCALED OUT {} of {} position for {} @ exec. price {}.", quantityToClose, openPosition.direction(), openPosition.symbol(), executionPrice);

            // Update or close position
            BigDecimal remainingQuantity = openPosition.quantity().subtract(quantityToClose);
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingCollateral = calculateProportionalValue(openPosition.collateral(), remainingQuantity, openPosition.quantity());
                openPosition = new Position(openPosition.symbol(), openPosition.entryTimestamp(), openPosition.entryPrice(), remainingQuantity, openPosition.direction(), remainingCollateral);
            } else {
                openPosition = null;
            }
        }

        private void openNewPositionMargin(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            BigDecimal executionPrice = applySlippage(price, direction);
            BigDecimal value = executionPrice.multiply(quantity);
            BigDecimal fee = value.multiply(tradingFeePercentage);
            BigDecimal collateralRequired;

            if (direction == TradeDirection.LONG) {
                collateralRequired = value.divide(BigDecimal.valueOf(marginLeverage), PRICE_SCALE, RoundingMode.HALF_UP);
            } else { // SHORT
                collateralRequired = value.divide(BigDecimal.valueOf(marginLeverage), PRICE_SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal totalCost = collateralRequired.add(fee);
            if (cash.compareTo(totalCost) < 0) {
                log.warn("Insufficient margin to open {} position for {}. Required: {}, Available: {}", direction, symbol, totalCost, cash);
                return;
            }
            cash = cash.subtract(totalCost);
            openPosition = new Position(symbol, System.currentTimeMillis(), executionPrice, quantity, direction, collateralRequired);
            log.info("OPENED {} {} position for {} @ exec. price {} (orig. price {}).",
                direction, quantity, symbol, executionPrice, price);
        }

        private void scaleInPositionMargin(BigDecimal additionalQuantity, BigDecimal price) {
            TradeDirection direction = openPosition.direction();
            BigDecimal executionPrice = applySlippage(price, direction);
            BigDecimal additionalValue = executionPrice.multiply(additionalQuantity);
            BigDecimal fee = additionalValue.multiply(tradingFeePercentage);
            BigDecimal additionalCollateral = additionalValue.divide(BigDecimal.valueOf(marginLeverage), PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal totalCost = additionalCollateral.add(fee);

            if (cash.compareTo(totalCost) < 0) {
                log.warn("Insufficient margin to scale in {} position. Required: {}, Available: {}", direction, totalCost, cash);
                return;
            }
            cash = cash.subtract(totalCost);

            BigDecimal oldTotalValue = openPosition.entryPrice().multiply(openPosition.quantity());
            BigDecimal newTotalQuantity = openPosition.quantity().add(additionalQuantity);
            BigDecimal newAveragePrice = oldTotalValue.add(additionalValue)
                .divide(newTotalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal newTotalCollateral = openPosition.collateral().add(additionalCollateral);

            openPosition = new Position(
                openPosition.symbol(),
                openPosition.entryTimestamp(),
                newAveragePrice,
                newTotalQuantity,
                direction,
                newTotalCollateral
            );
            log.info("SCALED IN {} {} position for {} @ exec. price {}. New Avg Price: {}",
                direction, additionalQuantity, openPosition.symbol(), executionPrice, newAveragePrice);
        }

        private void scaleOutPositionMargin(BigDecimal reduceQuantity, BigDecimal price) {
            TradeDirection closeDirection = (openPosition.direction() == TradeDirection.LONG) ? TradeDirection.SHORT : TradeDirection.LONG;
            BigDecimal executionPrice = applySlippage(price, closeDirection);
            BigDecimal quantityToClose = reduceQuantity.min(openPosition.quantity());

            BigDecimal collateralToRelease = calculateProportionalValue(
                openPosition.collateral(),
                quantityToClose,
                openPosition.quantity()
            );

            BigDecimal realizedPnl;
            if (openPosition.direction() == TradeDirection.LONG) {
                BigDecimal entryValue = openPosition.entryPrice().multiply(quantityToClose);
                BigDecimal exitValue = executionPrice.multiply(quantityToClose);
                BigDecimal fee = exitValue.multiply(tradingFeePercentage);
                realizedPnl = exitValue.subtract(entryValue).subtract(fee);
            } else { // SHORT
                BigDecimal entryValue = openPosition.entryPrice().multiply(quantityToClose);
                BigDecimal exitValue = executionPrice.multiply(quantityToClose);
                BigDecimal fee = exitValue.multiply(tradingFeePercentage); // Fee on exit value
                realizedPnl = entryValue.subtract(exitValue).subtract(fee);
            }

            cash = cash.add(collateralToRelease).add(realizedPnl);

            // Create trade record
            Trade trade = new Trade(openPosition.symbol(), openPosition.entryTimestamp(), System.currentTimeMillis(), openPosition.entryPrice(), executionPrice, quantityToClose, openPosition.direction());
            executedTrades.add(trade);
            log.info("SCALED OUT {} of {} position for {} @ exec. price {}.", quantityToClose, openPosition.direction(), openPosition.symbol(), executionPrice);

            // Update or close position
            BigDecimal remainingQuantity = openPosition.quantity().subtract(quantityToClose);
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingCollateral = openPosition.collateral().subtract(collateralToRelease);
                openPosition = new Position(openPosition.symbol(), openPosition.entryTimestamp(), openPosition.entryPrice(), remainingQuantity, openPosition.direction(), remainingCollateral);
            } else {
                openPosition = null;
            }
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
            if (openPosition != null && lastCandle != null) {
                // The mark-to-market valuation is identical for both modes.
                // It represents the Net Liquidation Value, which is cash + collateral + unrealized P/L.
                log.info("An open {} position for {} exists at the end of the backtest. Calculating mark-to-market value.",
                    openPosition.direction(), openPosition.symbol());

                TradeDirection closingDirection = (openPosition.direction() == TradeDirection.LONG) ? TradeDirection.SHORT : TradeDirection.LONG;
                BigDecimal liquidationPrice = applySlippage(lastCandle.close(), closingDirection);
                BigDecimal tradePnl = calculateTradePnl(liquidationPrice);

                finalValue = cash.add(openPosition.collateral()).add(tradePnl);

                log.info("Liquidating final {} position: Price={}, Unrealized P/L={}, Final Portfolio Value={}",
                    openPosition.direction(),
                    liquidationPrice.setScale(2, RoundingMode.HALF_UP),
                    tradePnl.setScale(2, RoundingMode.HALF_UP),
                    finalValue.setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal pnl = finalValue.subtract(initialCapital);
            double pnlPercent = initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? pnl.divide(initialCapital, 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

            log.info("Backtest finished. Initial Capital: {}, Final Value: {}, P/L: {} ({}%)",
                initialCapital.setScale(2, RoundingMode.HALF_UP),
                finalValue.setScale(2, RoundingMode.HALF_UP),
                pnl.setScale(2, RoundingMode.HALF_UP),
                String.format("%.2f", pnlPercent));

            return BacktestResult.builder()
                .finalValue(finalValue)
                .pnl(pnl)
                .pnlPercent(pnlPercent)
                .executedTrades(List.copyOf(executedTrades))
                .totalTrades(executedTrades.size())
                .build();
        }

        private BigDecimal calculateTradePnl(BigDecimal liquidationPrice) {
            BigDecimal grossValue = liquidationPrice.multiply(openPosition.quantity());
            BigDecimal fee = grossValue.multiply(tradingFeePercentage);

            BigDecimal tradePnl;
            BigDecimal entryValue = openPosition.entryPrice().multiply(openPosition.quantity());
            if (openPosition.direction() == TradeDirection.LONG) {
                tradePnl = grossValue.subtract(entryValue).subtract(fee);
            } else { // SHORT
                tradePnl = entryValue.subtract(grossValue).subtract(fee);
            }
            return tradePnl;
        }

        /**
         * Applies slippage to a given price to simulate a more realistic execution cost.
         * <p>
         * Slippage models the effect of crossing the bid-ask spread, ensuring the execution
         * price is always less favorable than the requested price. The behavior is determined
         * by the market action (buying or selling):
         * <ul>
         *   <li>For a <b>BUY</b> order ({@code orderDirection == TradeDirection.LONG}), slippage <b>increases</b> the execution price.</li>
         *   <li>For a <b>SELL</b> order ({@code orderDirection == TradeDirection.SHORT}), slippage <b>decreases</b> the execution price.</li>
         * </ul>
         *
         * @param price          The original requested price (e.g., the candle's close price).
         * @param orderDirection The direction of the order, which dictates whether it's a BUY or a SELL action.
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

        private BigDecimal calculateProportionalValue(BigDecimal totalValue, BigDecimal partQuantity, BigDecimal wholeQuantity) {
            if (wholeQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalValue.multiply(partQuantity).divide(wholeQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
        }
    }
}