package io.github.impatient0.azero.backtester.model;

import io.github.impatient0.azero.core.model.Candle;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuration object containing all the necessary parameters to run a backtest.
 * <p>
 * Use the generated builder to construct an immutable instance of this class.
 */
@Value
@Builder
public class BacktestConfig {

    /**
     * A list of {@link Candle} objects, representing the market data for the backtest.
     * Typically, this list should be sorted by timestamp in ascending order.
     */
    @NonNull
    List<Candle> historicalData;

    /**
     * The starting cash balance for the simulation.
     * This represents the total amount of capital available for trading.
     */
    @NonNull
    BigDecimal initialCapital;

    /**
     * A placeholder for the strategy configuration.
     * This will be defined more concretely in a future task (e.g., holding parsed YAML data).
     */
    @NonNull
    Object strategyDefinition;

    /**
     * The trading fee charged by the exchange, expressed as a percentage.
     * For example, a 0.1% fee should be provided as {@code new BigDecimal("0.001")}.
     * Defaults to zero if not specified.
     */
    @NonNull
    @Builder.Default
    BigDecimal tradingFeePercentage = BigDecimal.ZERO;

    /**
     * The estimated price slippage, expressed as a percentage.
     * This simulates the difference between the expected trade price and the actual
     * execution price. For example, 0.05% slippage should be provided as
     * {@code new BigDecimal("0.0005")}. Defaults to zero if not specified.
     */
    @NonNull
    @Builder.Default
    BigDecimal slippagePercentage = BigDecimal.ZERO;
}