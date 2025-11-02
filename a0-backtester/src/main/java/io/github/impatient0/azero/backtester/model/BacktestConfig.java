package io.github.impatient0.azero.backtester.model;

import io.github.impatient0.azero.core.model.AccountMode;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.strategy.Strategy;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Configuration object containing all the necessary parameters to run a backtest.
 * <p>
 * Use the generated builder to construct an immutable instance of this class.
 */
@Value
@Builder
public class BacktestConfig {

    /**
     * A map of historical data streams, where the key is the trading symbol
     * (e.g., "BTCUSDT") and the value is a list of {@link Candle} objects for that symbol.
     * The simulation will process these streams in chronological order.
     */
    @NonNull
    Map<String, List<Candle>> historicalData;

    /**
     * The starting cash balance for the simulation.
     * This represents the total amount of capital available for trading.
     */
    @NonNull
    BigDecimal initialCapital;

    /**
     * An instance of the {@link Strategy} implementation to be executed.
     * The backtest engine will call the {@code onCandle} method of this object for each
     * candle in the historical data.
     * <p>
     * <b>IMPORTANT:</b> Strategy implementations are typically stateful. The engine
     * will mutate the state of the provided strategy instance during a run. Therefore,
     * a new instance of the strategy must be provided for each backtest run to ensure
     * simulation correctness. Do not reuse strategy instances across multiple calls
     * to {@code BacktestEngine.run()}.
     */
    @NonNull
    Strategy strategy;

    /**
     * The account mode to simulate, determining the rules for order execution.
     * <p>
     * Defaults to {@link AccountMode#SPOT_ONLY} for maximum safety. In this mode,
     * shorting is disallowed and will cause an exception. To enable margin trading
     * simulation, this must be explicitly set to {@link AccountMode#MARGIN}.
     */
    @NonNull
    @Builder.Default
    AccountMode accountMode = AccountMode.SPOT_ONLY;

    /**
     * The margin leverage to use for calculating collateral on positions in MARGIN mode.
     * For example, a value of 5 represents 5x leverage. A value of 1 represents
     * no leverage, meaning 100% of the position's value is locked as collateral.
     * <p>
     * This parameter is <b>ignored</b> in {@link AccountMode#SPOT_ONLY}. Defaults to 1.
     */
    @Builder.Default
    int marginLeverage = 1;

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