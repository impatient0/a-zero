package io.github.impatient0.azero.backtester.engine;

import io.github.impatient0.azero.backtester.config.CollateralRatioLoader;
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
import java.util.Optional;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * The core engine for running multi-asset, multi-account-mode backtest simulations.
 * <p>
 * This class orchestrates the simulation by processing a chronologically sorted stream
 * of candle events for multiple assets (e.g., BTCUSDT, ETHUSDT). It feeds these
 * events to a provided {@link io.github.impatient0.azero.core.strategy.Strategy} and
 * manages the complete state of a simulated portfolio.
 * <p>
 * The engine supports distinct accounting modes, including
 * {@link io.github.impatient0.azero.core.model.AccountMode#SPOT_ONLY} and
 * {@link io.github.impatient0.azero.core.model.AccountMode#MARGIN}, with realistic
 * modeling of trading costs (fees, slippage) and risk mechanics like margin calls
 * and liquidations.
 */
@Slf4j
public class BacktestEngine {

    /**
     * Executes a backtest simulation based on the provided configuration.
     * <p>
     * This method is the main entry point for the engine. It initializes the
     * {@link BacktestTradingContext} with the user-defined parameters, prepares the
     * historical data for efficient time-series processing, and runs the main event
     * loop that drives the simulation candle by candle.
     *
     * @param config The {@link BacktestConfig} object containing all parameters for the
     *               simulation run, including the strategy to be tested, initial capital,
     *               historical data, and trading cost settings.
     * @return A {@link BacktestResult} object containing a comprehensive summary of the
     *         strategy's performance, including final portfolio value, P/L, and a
     *         list of all executed trades.
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
            config.getMarginLeverage(),
            config.getMaintenanceMarginFactor()
        );

        // 1. Pre-process data for efficient lookup
        Map<String, NavigableMap<Long, Candle>> dataByTimestamp = new HashMap<>();
        NavigableMap<Long, List<Candle>> eventsByTimestamp = new TreeMap<>();

        for (Map.Entry<String, List<Candle>> entry : config.getHistoricalData().entrySet()) {
            String symbol = entry.getKey();
            dataByTimestamp.put(symbol, new TreeMap<>());
            for (Candle candle : entry.getValue()) {
                dataByTimestamp.get(symbol).put(candle.timestamp(), candle);
                eventsByTimestamp.computeIfAbsent(candle.timestamp(), k -> new ArrayList<>()).add(candle);
            }
        }

        // 2. Main event loop
        for (Map.Entry<Long, List<Candle>> eventEntry : eventsByTimestamp.entrySet()) {
            long currentTimestamp = eventEntry.getKey();
            List<Candle> newCandles = eventEntry.getValue();

            // 3. Construct the current price map (forward-filling missing prices)
            Map<String, BigDecimal> currentPrices = new HashMap<>();
            for (String symbol : dataByTimestamp.keySet()) {
                // Find the latest candle at or before the current timestamp
                Map.Entry<Long, Candle> priceEntry = dataByTimestamp.get(symbol).floorEntry(currentTimestamp);
                if (priceEntry != null) {
                    BigDecimal price = priceEntry.getValue().close();
                    currentPrices.put(symbol.replace("USDT", ""), price); // Store as "BTC", "ETH"
                }
            }
            currentPrices.put("USDT", BigDecimal.ONE); // Always add the quote currency

            // 4. Update the context with the latest state of the world
            context.updateCurrentPrices(currentPrices);

            if (config.getAccountMode() == AccountMode.MARGIN) {
                if (context.isMarginCallTriggered()) {
                    context.liquidateAllPositions();
                }
            }

            // 5. Notify the strategy of each new candle for this timestamp
            for (Candle newCandle : newCandles) {
                strategy.onCandle(newCandle, context);
            }
        }

        log.info("Backtest simulation loop completed.");

        return context.calculateResult();
    }

    /**
     * An internal implementation of the {@link TradingContext} interface, tailored for backtesting.
     * <p>
     * This class is responsible for all state management within a single backtest run.
     * It simulates the behavior of a real trading account by tracking cash,
     * open positions, and executed trades in memory.
     */
    private static class BacktestTradingContext implements TradingContext {

        /** The standard scale for all price-related calculations. */
        private static final int PRICE_SCALE = 8;

        /** The starting capital for the backtest. */
        private final BigDecimal initialCapital;

        /** The simulation mode, which dictates all accounting rules. */
        private final AccountMode accountMode;

        /** The configured leverage for the account (only used in MARGIN mode). */
        private final int marginLeverage;

        /** The configured factor to calculate the Maintenance Margin (only used in MARGIN mode). */
        private final BigDecimal maintenanceMarginFactor;

        /** The fee percentage applied to each trade's value. */
        private final BigDecimal tradingFeePercentage;

        /** The slippage percentage applied to the execution price. */
        private final BigDecimal slippagePercentage;

        /** A map of asset symbols to their configured collateral ratios (only used in MARGIN mode). */
        private final Map<String, BigDecimal> collateralRatios;

        /** Holds the balance of each asset in the portfolio. The interpretation depends on the accountMode. */
        private final Map<String, BigDecimal> wallet;

        /** Tracks all currently open positions. */
        private final Map<String, Position> openPositions;

        /** The latest market prices, updated on each tick by the engine. */
        private Map<String, BigDecimal> currentPrices;

        /** A list of all trades that have been closed during the simulation. */
        private final List<Trade> executedTrades = new ArrayList<>();

        /**
         * Constructs and initializes a new BacktestTradingContext for a single simulation run.
         * <p>
         * This sets up the initial state of the portfolio, including starting capital,
         * and configures the rules for the simulation based on the provided parameters.
         * It conditionally loads data like collateral ratios only if required by the
         * selected account mode.
         *
         * @param initialCapital          The starting capital for the backtest, denominated in USDT.
         * @param tradingFeePercentage    The percentage-based fee applied to the value of each trade.
         * @param slippagePercentage      The percentage-based slippage applied to the execution price
         *                                to simulate a less favorable fill.
         * @param accountMode             The accounting mode for the simulation (e.g., SPOT_ONLY or MARGIN).
         * @param marginLeverage          The leverage to use for initial margin calculations in MARGIN mode.
         * @param maintenanceMarginFactor The factor multiplied by a position's initial margin to determine
         *                                its maintenance margin requirement.
         */
        private BacktestTradingContext(BigDecimal initialCapital, BigDecimal tradingFeePercentage,
            BigDecimal slippagePercentage, AccountMode accountMode, int marginLeverage,
            BigDecimal maintenanceMarginFactor) {
            this.initialCapital = initialCapital;
            this.accountMode = accountMode;
            this.marginLeverage = Math.max(1, marginLeverage);
            this.maintenanceMarginFactor = maintenanceMarginFactor;

            // Initialize wallet with starting capital (always in USDT).
            this.wallet = new ConcurrentHashMap<>();
            this.wallet.put("USDT", initialCapital);

            this.openPositions = new ConcurrentHashMap<>();

            // Load collateral ratios only if they are needed for the selected mode.
            if (this.accountMode == AccountMode.MARGIN) {
                this.collateralRatios = CollateralRatioLoader.load();
            } else {
                this.collateralRatios = Collections.emptyMap();
            }

            this.currentPrices = Collections.emptyMap();

            this.tradingFeePercentage = tradingFeePercentage;
            this.slippagePercentage = slippagePercentage;
        }

        /**
         * {@inheritDoc}
         * <p>
         * In this backtesting implementation, the order is simulated against historical data.
         * The execution price will be adjusted for the configured slippage, and the order
         * may be rejected if there are insufficient funds (in SPOT mode) or if it fails
         * the pre-trade margin check (in MARGIN mode).
         */
        @Override
        public void submitOrder(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            if (!symbol.endsWith("USDT")) {
                log.error("Unsupported symbol format: {}. Only USDT pairs are currently supported.", symbol);
                return;
            }
            String baseAsset = symbol.substring(0, symbol.length() - 4);
            String quoteAsset = "USDT";

            Position existingPosition = openPositions.get(symbol);

            if (accountMode == AccountMode.MARGIN) {
                if (existingPosition == null) {
                    openPositionMargin(symbol, baseAsset, quoteAsset, direction, quantity, price);
                } else if (existingPosition.direction() == direction) {
                    scaleInPositionMargin(existingPosition, baseAsset, quoteAsset, quantity, price);
                } else {
                    scaleOutPositionMargin(existingPosition, baseAsset, quoteAsset, quantity, price);
                }
            } else { // SPOT_ONLY
                if (direction == TradeDirection.LONG) { // BUY
                    openOrScaleInSpot(symbol, baseAsset, quoteAsset, quantity, price);
                } else { // SELL
                    closeOrScaleOutSpot(symbol, baseAsset, quoteAsset, quantity, price);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Position> getOpenPosition(String symbol) {
            return Optional.ofNullable(openPositions.get(symbol));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<String, Position> getOpenPositions() {
            return Collections.unmodifiableMap(openPositions);
        }

        /**
         * Opens a new position in MARGIN mode.
         * <p>
         * It performs a margin check, updates the wallet to reflect the borrowing
         * of assets, and creates a new {@link Position} record.
         *
         * @param symbol    The trading pair for the new position.
         * @param baseAsset The base asset of the pair (e.g., "BTC").
         * @param quoteAsset The quote asset of the pair (e.g., "USDT").
         * @param direction The direction of the position (LONG or SHORT).
         * @param quantity  The quantity of the base asset to trade.
         * @param price     The target price for the opening trade.
         */
        private void openPositionMargin(String symbol, String baseAsset, String quoteAsset, TradeDirection direction, BigDecimal quantity, BigDecimal price) {
            BigDecimal executionPrice = applySlippage(price, direction);
            BigDecimal positionValue = executionPrice.multiply(quantity);

            // Margin Check
            BigDecimal imr = calculateInitialMarginRate();
            BigDecimal newMarginRequired = positionValue.multiply(imr);
            BigDecimal totalEquity = calculateTotalEquity(this.currentPrices);
            BigDecimal existingMargin = calculateTotalInitialMargin();

            if (totalEquity.subtract(existingMargin).compareTo(newMarginRequired) < 0) {
                log.warn("MARGIN CHECK FAILED: Cannot open {} position for {}. Required: {}, Available: {}",
                    direction, symbol, newMarginRequired, totalEquity.subtract(existingMargin));
                return;
            }

            // Execute Trade - Update Wallet
            BigDecimal fee = positionValue.multiply(tradingFeePercentage);
            wallet.merge(quoteAsset, fee.negate(), BigDecimal::add); // Deduct fee from USDT

            if (direction == TradeDirection.LONG) {
                wallet.merge(baseAsset, quantity, BigDecimal::add); // Receive base asset
                wallet.merge(quoteAsset, positionValue.negate(), BigDecimal::add); // Pay for it
            } else { // SHORT
                wallet.merge(baseAsset, quantity.negate(), BigDecimal::add); // Borrow base asset (negative balance)
                wallet.merge(quoteAsset, positionValue, BigDecimal::add); // Receive proceeds
            }

            Position position = new Position(symbol, System.currentTimeMillis(), executionPrice, quantity, direction, newMarginRequired);
            openPositions.put(symbol, position);
            log.info("Opened MARGIN {} position for {} {} @ {}", direction, quantity, baseAsset, executionPrice);
        }

        /**
         * Increases the size of an existing position in MARGIN mode.
         * <p>
         * It performs a margin check for the additional size, updates the wallet,
         * and recalculates the position's average entry price.
         *
         * @param existingPosition   The position to add to.
         * @param baseAsset          The base asset of the pair.
         * @param quoteAsset         The quote asset of the pair.
         * @param additionalQuantity The additional quantity of the base asset to trade.
         * @param price              The target price for the scaling trade.
         */
        private void scaleInPositionMargin(Position existingPosition, String baseAsset, String quoteAsset, BigDecimal additionalQuantity, BigDecimal price) {
            BigDecimal executionPrice = applySlippage(price, existingPosition.direction());
            BigDecimal additionalValue = executionPrice.multiply(additionalQuantity);

            // Margin Check for the additional amount
            BigDecimal imr = calculateInitialMarginRate();
            BigDecimal newMarginRequired = additionalValue.multiply(imr);
            BigDecimal totalEquity = calculateTotalEquity(this.currentPrices);
            BigDecimal existingMargin = calculateTotalInitialMargin();

            if (totalEquity.subtract(existingMargin).compareTo(newMarginRequired) < 0) {
                log.warn("MARGIN CHECK FAILED: Cannot scale in {}. Required: {}, Available: {}",
                    existingPosition.symbol(), newMarginRequired, totalEquity.subtract(existingMargin));
                return;
            }

            // Update Wallet
            BigDecimal fee = additionalValue.multiply(tradingFeePercentage);
            wallet.merge(quoteAsset, fee.negate(), BigDecimal::add); // Deduct fee

            if (existingPosition.direction() == TradeDirection.LONG) {
                wallet.merge(baseAsset, additionalQuantity, BigDecimal::add);
                wallet.merge(quoteAsset, additionalValue.negate(), BigDecimal::add);
            } else { // SHORT
                wallet.merge(baseAsset, additionalQuantity.negate(), BigDecimal::add);
                wallet.merge(quoteAsset, additionalValue, BigDecimal::add);
            }

            // Update Position
            BigDecimal oldTotalValue = existingPosition.entryPrice().multiply(existingPosition.quantity());
            BigDecimal newTotalQuantity = existingPosition.quantity().add(additionalQuantity);
            BigDecimal newAveragePrice = oldTotalValue.add(additionalValue).divide(newTotalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal newTotalMargin = existingPosition.costBasis().add(newMarginRequired);

            Position updatedPosition = new Position(existingPosition.symbol(), existingPosition.entryTimestamp(), newAveragePrice, newTotalQuantity, existingPosition.direction(), newTotalMargin);
            openPositions.put(existingPosition.symbol(), updatedPosition);
            log.info("SCALED IN MARGIN {} position for {} {} @ {}", existingPosition.direction(), additionalQuantity, baseAsset, executionPrice);
        }

        /**
         * Decreases the size of, or fully closes, an existing position in MARGIN mode.
         * <p>
         * It updates the wallet to reflect the closing trade, records the realized P/L
         * in a new {@link Trade} record, and updates or removes the {@link Position}.
         *
         * @param existingPosition  The position to reduce or close.
         * @param baseAsset         The base asset of the pair.
         * @param quoteAsset        The quote asset of the pair.
         * @param quantityToCloseRaw The requested quantity of the base asset to close.
         * @param price             The target price for the closing trade.
         */
        private void scaleOutPositionMargin(Position existingPosition, String baseAsset, String quoteAsset, BigDecimal quantityToCloseRaw, BigDecimal price) {
            TradeDirection closingDirection = (existingPosition.direction() == TradeDirection.LONG) ? TradeDirection.SHORT : TradeDirection.LONG;
            BigDecimal executionPrice = applySlippage(price, closingDirection);
            BigDecimal quantityToClose = quantityToCloseRaw.min(existingPosition.quantity());
            BigDecimal valueOfClosedPortion = executionPrice.multiply(quantityToClose);
            BigDecimal fee = valueOfClosedPortion.multiply(tradingFeePercentage);

            // Update Wallet
            wallet.merge(quoteAsset, fee.negate(), BigDecimal::add); // Deduct fee for the close

            if (existingPosition.direction() == TradeDirection.LONG) {
                wallet.merge(baseAsset, quantityToClose.negate(), BigDecimal::add); // Sell asset
                wallet.merge(quoteAsset, valueOfClosedPortion, BigDecimal::add);     // Receive proceeds
            } else { // SHORT
                wallet.merge(baseAsset, quantityToClose, BigDecimal::add);      // Buy back asset
                wallet.merge(quoteAsset, valueOfClosedPortion.negate(), BigDecimal::add); // Pay for it
            }

            // Create Trade record for realized P/L
            Trade trade = new Trade(existingPosition.symbol(), existingPosition.entryTimestamp(), System.currentTimeMillis(), existingPosition.entryPrice(), executionPrice, quantityToClose, existingPosition.direction());
            executedTrades.add(trade);
            log.info("SCALED OUT MARGIN {} position for {} {} @ {}", existingPosition.direction(), quantityToClose, baseAsset, executionPrice);

            // Update or Remove Position
            BigDecimal remainingQuantity = existingPosition.quantity().subtract(quantityToClose);
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingMargin = calculateProportionalValue(existingPosition.costBasis(), remainingQuantity, existingPosition.quantity());
                Position updatedPosition = new Position(existingPosition.symbol(), existingPosition.entryTimestamp(), existingPosition.entryPrice(), remainingQuantity, existingPosition.direction(), remainingMargin);
                openPositions.put(existingPosition.symbol(), updatedPosition);
            } else {
                openPositions.remove(existingPosition.symbol());
            }
        }

        /**
         * Executes a BUY order in SPOT mode, opening a new position or scaling into an existing one.
         * <p>
         * It checks for sufficient quote asset funds, updates the wallet, and creates or
         * updates the {@link Position} record to track the average entry price and cost basis.
         *
         * @param symbol    The trading pair for the new position.
         * @param baseAsset The base asset of the pair (e.g., "BTC").
         * @param quoteAsset The quote asset of the pair (e.g., "USDT").
         * @param quantity  The quantity of the base asset to buy.
         * @param price     The target price for the buy order.
         */
        private void openOrScaleInSpot(String symbol, String baseAsset, String quoteAsset, BigDecimal quantity, BigDecimal price) {
            BigDecimal executionPrice = applySlippage(price, TradeDirection.LONG);
            BigDecimal positionValue = executionPrice.multiply(quantity);
            BigDecimal fee = positionValue.multiply(tradingFeePercentage);
            BigDecimal totalCost = positionValue.add(fee);

            if (wallet.getOrDefault(quoteAsset, BigDecimal.ZERO).compareTo(totalCost) < 0) {
                log.warn("SPOT: Insufficient funds to buy {}. Required: {}, Available: {}", symbol, totalCost, wallet.get(quoteAsset));
                return;
            }

            // Update wallet
            wallet.merge(quoteAsset, totalCost.negate(), BigDecimal::add);
            wallet.merge(baseAsset, quantity, BigDecimal::add);

            // Create or update Position for tracking entry price
            Position existing = openPositions.get(symbol);
            if (existing == null) {
                openPositions.put(symbol, new Position(symbol, System.currentTimeMillis(), executionPrice, quantity, TradeDirection.LONG, totalCost));
            } else {
                BigDecimal oldTotalValue = existing.entryPrice().multiply(existing.quantity());
                BigDecimal newTotalQuantity = existing.quantity().add(quantity);
                BigDecimal newAveragePrice = oldTotalValue.add(positionValue).divide(newTotalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
                BigDecimal newTotalCollateral = existing.costBasis().add(totalCost);
                openPositions.put(symbol, new Position(symbol, existing.entryTimestamp(), newAveragePrice, newTotalQuantity, TradeDirection.LONG, newTotalCollateral));
            }
            log.info("Executed SPOT BUY for {} {} @ {}", quantity, baseAsset, executionPrice);
        }

        /**
         * Executes a SELL order in SPOT mode, reducing or closing an existing position.
         * <p>
         * It validates that a position exists with sufficient quantity, updates wallet
         * balances, and updates or removes the corresponding {@link Position} record.
         *
         * @param symbol    The trading pair for the position to sell from.
         * @param baseAsset The base asset of the pair.
         * @param quoteAsset The quote asset of the pair.
         * @param quantity  The quantity of the base asset to sell.
         * @param price     The target price for the sell order.
         */
        private void closeOrScaleOutSpot(String symbol, String baseAsset, String quoteAsset, BigDecimal quantity, BigDecimal price) {
            Position existing = openPositions.get(symbol);
            if (existing == null || existing.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("SPOT: Attempted to sell {} but no position is open.", symbol);
                return;
            }

            BigDecimal quantityToSell = quantity.min(existing.quantity());

            if (wallet.getOrDefault(baseAsset, BigDecimal.ZERO).compareTo(quantityToSell) < 0) {
                log.error("CRITICAL: Wallet-Position mismatch. Wallet holds less {} than position record. Aborting sell.", baseAsset);
                return;
            }

            BigDecimal executionPrice = applySlippage(price, TradeDirection.SHORT);
            BigDecimal positionValue = executionPrice.multiply(quantityToSell);
            BigDecimal fee = positionValue.multiply(tradingFeePercentage);
            BigDecimal proceeds = positionValue.subtract(fee);

            // Update wallet
            wallet.merge(baseAsset, quantityToSell.negate(), BigDecimal::add);
            wallet.merge(quoteAsset, proceeds, BigDecimal::add);

            // Create and record the completed trade
            Trade trade = new Trade(
                symbol,
                existing.entryTimestamp(),
                System.currentTimeMillis(), // Placeholder timestamp
                existing.entryPrice(),
                executionPrice,
                quantityToSell,
                existing.direction()
            );
            executedTrades.add(trade);

            // Update or remove position
            BigDecimal remainingQuantity = existing.quantity().subtract(quantityToSell);
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingCollateral = calculateProportionalValue(existing.costBasis(), remainingQuantity, existing.quantity());
                openPositions.put(symbol, new Position(symbol, existing.entryTimestamp(), existing.entryPrice(), remainingQuantity, TradeDirection.LONG, remainingCollateral));
            } else {
                openPositions.remove(symbol);
            }
            log.info("Executed SPOT SELL for {} {} @ {}", quantityToSell, baseAsset, executionPrice);
        }

        /**
         * Checks if the portfolio's equity has fallen below the total maintenance margin requirement.
         * This method is only relevant in MARGIN mode.
         *
         * @return {@code true} if a margin call is triggered, otherwise {@code false}.
         */
        boolean isMarginCallTriggered() {
            if (openPositions.isEmpty()) {
                return false;
            }
            BigDecimal totalEquity = calculateTotalEquity(this.currentPrices);
            BigDecimal totalMaintenanceMargin = calculateTotalMaintenanceMargin();
            boolean isTriggered = totalEquity.compareTo(totalMaintenanceMargin) < 0;
            if (isTriggered) {
                log.warn("MARGIN CALL TRIGGERED: Total Equity ({}) is below Total Maintenance Margin ({})",
                    totalEquity.setScale(2, RoundingMode.HALF_UP),
                    totalMaintenanceMargin.setScale(2, RoundingMode.HALF_UP));
            }
            return isTriggered;
        }

        /**
         * Executes a forced liquidation of all open positions in the portfolio.
         * This is a critical safety mechanism triggered by a margin call. It simulates the
         * exchange closing all positions at the current market price to cover losses.
         */
        void liquidateAllPositions() {
            log.error("!!! MARGIN CALL - LIQUIDATING ALL POSITIONS DUE TO INSUFFICIENT EQUITY !!!");
            // Create a copy of the keys to iterate over, avoiding ConcurrentModificationException
            Set<String> symbolsToLiquidate = new HashSet<>(openPositions.keySet());

            for (String symbol : symbolsToLiquidate) {
                Position position = openPositions.get(symbol);
                if (position == null) continue; // Position might have been closed by another logic path, though unlikely

                String baseAsset = symbol.replace("USDT", "");
                BigDecimal liquidationPrice = this.currentPrices.get(baseAsset);

                if (liquidationPrice == null) {
                    log.error("CRITICAL: Cannot liquidate position for {} due to missing price data. This position will remain open, resulting in an inconsistent state.", symbol);
                    continue;
                }

                log.warn("Liquidating position: {} {} {} at price {}", position.direction(), position.quantity(), symbol, liquidationPrice.setScale(2, RoundingMode.HALF_UP));
                // Reuse the existing scale-out logic to perform the liquidation.
                // This ensures correct P/L calculation, wallet updates, and trade recording.
                scaleOutPositionMargin(position, baseAsset, "USDT", position.quantity(), liquidationPrice);
            }
        }

        /**
         * Calculates the portfolio's total <strong>Margin Equity</strong>.
         * <p>
         * This value represents the capital available to back open positions in MARGIN mode.
         * It is calculated as:
         * {@code Sum(Value of Positive Assets * Collateral Ratio) - Sum(Value of Negative Assets)}.
         * <p>
         * It is used exclusively for pre-trade margin checks and for monitoring maintenance
         * margin requirements. It does <strong>not</strong> represent the true net asset value
         * of the portfolio. For final P/L calculations, see {@link #calculateNetAssetValue(Map)}.
         *
         * @param currentPrices A map of asset symbols to their current market prices.
         * @return The total calculated margin equity of the portfolio.
         */
        private BigDecimal calculateTotalEquity(Map<String, BigDecimal> currentPrices) {
            BigDecimal positiveAssetValue = BigDecimal.ZERO;
            BigDecimal negativeAssetValue = BigDecimal.ZERO;

            for (Map.Entry<String, BigDecimal> entry : wallet.entrySet()) {
                String asset = entry.getKey();
                BigDecimal balance = entry.getValue();
                BigDecimal price = currentPrices.getOrDefault(asset, BigDecimal.ONE); // Assume price is 1 for quote currency like USDT

                BigDecimal value = balance.multiply(price);

                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    // Asset is owned (positive balance)
                    BigDecimal collateralRatio = collateralRatios.getOrDefault(asset, BigDecimal.ZERO);
                    positiveAssetValue = positiveAssetValue.add(value.multiply(collateralRatio));
                } else {
                    // Asset is a liability (negative balance, i.e., borrowed)
                    // We use abs() because the formula subtracts the positive value of liabilities.
                    negativeAssetValue = negativeAssetValue.add(value.abs());
                }
            }
            return positiveAssetValue.subtract(negativeAssetValue);
        }

        /**
         * Calculates the Initial Margin Rate (IMR) based on the configured leverage.
         * The formula is a conservative approximation: IMR = (1 / leverage) * (1.02 ^ leverage).
         *
         * @return The calculated Initial Margin Rate as a BigDecimal.
         */
        private BigDecimal calculateInitialMarginRate() {
            if (marginLeverage <= 0) {
                return BigDecimal.ONE; // Should not happen, but a safe default
            }
            // Use double for the pow() calculation as BigDecimal does not support it natively.
            double baseImr = 1.0 / marginLeverage;
            double stressFactor = Math.pow(1.02, marginLeverage);
            double finalImr = baseImr * stressFactor;

            return BigDecimal.valueOf(finalImr);
        }

        /**
         * Calculates the sum of the initial margin locked for all currently open positions.
         *
         * @return The total initial margin currently in use.
         */
        private BigDecimal calculateTotalInitialMargin() {
            return openPositions.values().stream()
                .map(Position::costBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * Calculates the total maintenance margin required for all currently open positions.
         *
         * @return The total maintenance margin as a BigDecimal.
         */
        private BigDecimal calculateTotalMaintenanceMargin() {
            return openPositions.values().stream()
                .map(p -> p.costBasis().multiply(this.maintenanceMarginFactor))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * Updates the context with the latest market prices for all tracked assets.
         *
         * @param prices A map where the key is the asset symbol (e.g., "BTC") and the
         *               value is its latest market price.
         */
        void updateCurrentPrices(Map<String, BigDecimal> prices) {
            this.currentPrices = prices;
        }

        /**
         * Finalizes the simulation and calculates the overall performance results.
         * <p>
         * This terminal operation is called by the {@link BacktestEngine} after the
         * main event loop has completed. It performs a final portfolio valuation using
         * the true {@link #calculateNetAssetValue(Map)} to determine the total profit
         * or loss against the initial capital.
         *
         * @return A {@link BacktestResult} object containing the comprehensive
         *         summary of the simulation's performance, including final value, P/L,
         *         and the list of executed trades.
         */
        BacktestResult calculateResult() {
            BigDecimal finalNetAssetValue = calculateNetAssetValue(this.currentPrices);
            BigDecimal totalPnl = finalNetAssetValue.subtract(initialCapital);

            double pnlPercent = initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.divide(initialCapital, 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

            log.info("Backtest finished. Initial Capital: {}, Final NAV: {}, P/L: {} ({}%)",
                initialCapital.setScale(2, RoundingMode.HALF_UP),
                finalNetAssetValue.setScale(2, RoundingMode.HALF_UP),
                totalPnl.setScale(2, RoundingMode.HALF_UP),
                String.format("%.2f", pnlPercent));

            return BacktestResult.builder()
                .finalValue(finalNetAssetValue)
                .pnl(totalPnl)
                .pnlPercent(pnlPercent)
                .executedTrades(List.copyOf(executedTrades))
                .totalTrades(executedTrades.size())
                .build();
        }

        /**
         * Calculates the Net Asset Value (NAV) of the portfolio.
         * This is the true, unleveraged mark-to-market value of all assets and liabilities.
         * NAV = Sum(balance_of_each_asset * current_price_of_asset).
         * <p>
         * This method does NOT use collateral ratios and is used for the final P/L report.
         *
         * @param currentPrices A map of asset symbols to their current market prices.
         * @return The total Net Asset Value of the portfolio.
         */
        private BigDecimal calculateNetAssetValue(Map<String, BigDecimal> currentPrices) {
            BigDecimal totalValue = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> walletEntry : wallet.entrySet()) {
                String asset = walletEntry.getKey();
                BigDecimal balance = walletEntry.getValue();
                BigDecimal price = currentPrices.getOrDefault(asset, BigDecimal.ZERO);
                totalValue = totalValue.add(balance.multiply(price));
            }
            return totalValue;
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

        /**
         * Calculates a proportional part of a total value.
         * <p>
         * This is a utility method used to determine the remaining cost basis of a
         * position after a portion of it has been sold.
         *
         * @param totalValue    The total value from which a proportion is being calculated
         *                      (e.g., the original cost basis).
         * @param partQuantity  The partial quantity corresponding to the desired proportional
         *                      value (e.g., the remaining quantity).
         * @param wholeQuantity The total quantity that corresponds to the {@code totalValue}
         *                      (e.g., the original quantity).
         * @return The calculated proportional value. Returns zero if {@code wholeQuantity} is zero
         *         to prevent division-by-zero errors.
         */
        private BigDecimal calculateProportionalValue(BigDecimal totalValue, BigDecimal partQuantity, BigDecimal wholeQuantity) {
            if (wholeQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalValue.multiply(partQuantity).divide(wholeQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
        }
    }
}