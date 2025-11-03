package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.impatient0.azero.core.model.TradeDirection;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A data-transfer object that represents the complete configuration for a
 * declarative, rules-based strategy, deserialized from a YAML file.
 */
@Value
@Builder
public class StrategyConfig {
    /**
     * The unique name for this strategy.
     */
    @JsonProperty("strategy_name")
    String strategyName;

    /**
     * The default trading direction for the strategy (LONG or SHORT).
     */
    @JsonProperty("direction")
    TradeDirection direction;

    /**
     * A list of indicator-based rules that must all be true to trigger a position entry.
     */
    @JsonProperty("entry_rules")
    List<IndicatorConfig> entryRules;

    /**
     * A list of rules that define the conditions for exiting a position (e.g., stop-loss).
     */
    @JsonProperty("exit_rules")
    List<ExitRuleConfig> exitRules;
}