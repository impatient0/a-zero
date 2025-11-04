package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.impatient0.azero.core.model.TradeDirection;

import java.util.List;

/**
 * A data-transfer object that represents the complete configuration for a
 * declarative, rules-based strategy, deserialized from a YAML file.
 * <p>
 * This is an immutable data carrier, implemented as a Java Record.
 *
 * @param strategyName The unique name for this strategy.
 * @param direction    The default trading direction for the strategy (LONG or SHORT).
 * @param timeframe    The timeframe the strategy operates on (e.g., "1h", "4h", "1d").
 *                     This is used to correctly configure the underlying technical indicators.
 * @param entryRules   A list of indicator-based rules that must all be true to trigger a position entry.
 * @param exitRules    A list of rules that define the conditions for exiting a position (e.g., stop-loss).
 */
public record StrategyConfig(
    @JsonProperty("strategy_name")
    String strategyName,

    @JsonProperty("direction")
    TradeDirection direction,

    @JsonProperty("timeframe")
    String timeframe,

    @JsonProperty("entry_rules")
    List<IndicatorConfig> entryRules,

    @JsonProperty("exit_rules")
    List<ExitRuleConfig> exitRules
) {}