package io.github.impatient0.azero.strategy.rules.config;

import java.math.BigDecimal;

/**
 * Configuration for an RSI-based trading rule.
 *
 * @param period    The time period for the RSI calculation (e.g., 14).
 * @param condition The comparison to perform (e.g., LESS_THAN).
 * @param value     The threshold value to compare against (e.g., 30).
 */
public record RsiIndicatorConfig(
    int period,
    SignalCondition condition,
    BigDecimal value
) implements IndicatorConfig {
}