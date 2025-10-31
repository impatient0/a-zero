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
     * A strategy should call this method to check its current state before submitting
     * an order.
     *
     * @param symbol The trading symbol (e.g., "BTCUSDT") to check for an open position.
     * @return an {@link Optional} containing the current {@link Position} if one is open,
     * or an empty Optional if there is no open position for the symbol.
     */
    Optional<Position> getOpenPosition(String symbol);

    /**
     * Submits an order to the trading engine to modify a position.
     * <p>
     * This single method handles all trading actions:
     * <ul>
     *   <li><b>Opening a new position:</b> If no position is open for the symbol.</li>
     *   <li><b>Increasing a position (scaling in):</b> If an order is submitted in the same direction as the existing position.</li>
     *   <li><b>Decreasing or closing a position (scaling out):</b> If an order is submitted in the opposite direction.</li>
     * </ul>
     * The underlying engine is responsible for correctly interpreting the order and
     * updating the portfolio state, including position size, average entry price,
     * and recording any completed trades (e.g., on a partial close).
     *
     * @param symbol    The trading symbol (e.g., "BTCUSDT").
     * @param direction The desired direction of the order (LONG or SHORT).
     * @param quantity  The amount of the asset to trade.
     * @param price     The price at which to execute the order.
     */
    void submitOrder(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price);

}