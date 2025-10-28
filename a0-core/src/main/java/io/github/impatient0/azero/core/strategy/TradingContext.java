package io.github.impatient0.azero.core.strategy;

/**
 * Provides the context and state available to a {@link Strategy} during its execution.
 * <p>
 * This interface acts as the strategy's gateway to interacting with the trading
 * environment, whether in a backtest or live trading scenario. It allows the strategy
 * to query market data, manage positions, and execute orders without being tightly
 * coupled to the underlying execution engine.
 * <p>
 * This is a placeholder and will be expanded with methods like:
 * <ul>
 *   <li>{@code void openPosition(PositionParameters params);}</li>
 *   <li>{@code void closePosition(String symbol);}</li>
 *   <li>{@code Optional<Position> getPosition(String symbol);}</li>
 *   <li>{@code String getSymbol();}</li>
 *   <li>{@code String getTimeframe();}</li>
 * </ul>
 */
public interface TradingContext {
    // TODO: define methods
}