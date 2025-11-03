package io.github.impatient0.azero.core.strategy;

import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;

import java.math.BigDecimal;
import java.util.Map;
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
     * This is the primary method for checking the state of a single asset.
     *
     * @param symbol The trading symbol (e.g., "BTCUSDT") to check.
     * @return an {@link Optional} containing the current {@link Position} if one is open,
     * or an empty Optional otherwise.
     */
    Optional<Position> getOpenPosition(String symbol);

    /**
     * Retrieves an unmodifiable view of all currently open positions.
     * This is useful for portfolio-level analysis and logic.
     *
     * @return An unmodifiable Map of symbol to {@link Position}.
     */
    Map<String, Position> getOpenPositions();

    /**
     * Submits an order to the trading engine to modify a position.
     * <p>
     * This single method handles all trading actions:
     * <ul>
     *   <li><b>Opening a new position:</b> If no position is open for the symbol.</li>
     *   <li><b>Increasing a position (scaling in):</b> If an order is submitted in the same direction as the existing position.</li>
     *   <li><b>Decreasing or closing a position (scaling out):</b> If an order is submitted in the opposite direction.</li>
     * </ul>
     * <b>Interaction Pattern:</b> This method follows a "fire-and-reconcile" model.
     * It returns {@code void} immediately and does not guarantee execution. The strategy
     * is responsible for checking the result of its action by calling
     * {@link #getOpenPosition(String)} on a subsequent event to reconcile its state
     * with the environment's ground truth.
     *
     * @param symbol    The trading symbol (e.g., "BTCUSDT").
     * @param direction The desired direction of the order (LONG or SHORT).
     * @param quantity  The amount of the <b>base</b> asset to trade (e.g., the amount of BTC
     *                  in a BTCUSDT trade).
     * @param price     The target price for the order. The actual execution price may differ due
     *                  to market conditions (e.g., slippage, fees).
     */
    void submitOrder(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price);

}