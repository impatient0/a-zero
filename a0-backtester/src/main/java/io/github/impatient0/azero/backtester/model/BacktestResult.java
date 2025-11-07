package io.github.impatient0.azero.backtester.model;

import io.github.impatient0.azero.core.model.Trade;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * An immutable data object that holds the final outcome of a backtest simulation.
 * Use the generated builder to construct an instance of this class.
 */
@Value
@Builder
public class BacktestResult {

    /**
     * The final value of the portfolio at the end of the simulation.
     * This includes the remaining cash balance plus the value of any open positions.
     */
    @NonNull
    BigDecimal finalValue;

    /**
     * The total profit or loss achieved during the simulation.
     * Calculated as: (Final Value - Initial Capital).
     */
    @NonNull
    BigDecimal pnl;

    /**
     * The total profit or loss as a percentage of the initial capital.
     * For example, 0.10 means a 10% gain.
     */
    double pnlPercent;

    /**
     * A list of all {@link Trade} objects that were completed (opened and closed)
     * during the backtest simulation.
     */
    @NonNull
    List<Trade> executedTrades;

    /**
     * The total count of all executed (completed) trades.
     * This should match the size of the {@code executedTrades} list.
     */
    int totalTrades;
}