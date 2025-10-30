package io.github.impatient0.azero.core.strategy;

import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Provides the context and state available to a {@link Strategy} during its execution.
 * <p>
 * This interface acts as the strategy's gateway to interacting with the trading
 * environment, whether in a backtest or live trading scenario. It allows the strategy
 * to query market data, manage positions, and execute orders without being tightly
 * coupled to the underlying execution engine.
 */
public interface TradingContext {

    /**
     * Retrieves the currently open position for a given symbol, if one exists.
     * <p>
     * A strategy should call this method to check its current state before attempting
     * to open a new position or close an existing one.
     *
     * @param symbol The trading symbol (e.g., "BTCUSDT") to check for an open position.
     * @return an {@link Optional} containing the current {@link Position} if one is open,
     * or an empty Optional if there is no open position for the symbol.
     */
    Optional<Position> getOpenPosition(String symbol);

    /**
     * Submits a request to the trading engine to open a new position.
     * <p>
     * This method signals the strategy's *intent* to enter the market. The underlying
     * implementation (e.g., the backtester) is responsible for handling the state
     * change, such as updating the portfolio's cash balance and creating a new
     * {@link Position} record. A strategy should typically check if a position is
     * already open using {@link #getOpenPosition(String)} before calling this method.
     *
     * @param symbol    The trading symbol (e.g., "BTCUSDT") for the new position.
     * @param direction The desired direction of the position (LONG or SHORT).
     * @param quantity  The amount of the asset to trade.
     * @param price     The price at which to open the position.
     */
    void openPosition(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price);

    /**
     * Submits a request to the trading engine to close the currently open position.
     * <p>
     * This method signals the strategy's *intent* to exit the market. The engine is
     * responsible for calculating the result of the trade, updating the portfolio balance,
     * and converting the open {@link Position} into a completed {@link io.github.impatient0.azero.core.model.Trade}.
     * If no position is open for the given symbol, this method should have no effect.
     *
     * @param symbol The trading symbol (e.g., "BTCUSDT") of the position to close.
     * @param price  The price at which to close the position.
     */
    void closePosition(String symbol, BigDecimal price);
}