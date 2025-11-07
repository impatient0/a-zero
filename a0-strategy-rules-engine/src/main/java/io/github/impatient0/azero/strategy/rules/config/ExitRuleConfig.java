package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A sealed interface representing the configuration for a single exit rule.
 * This uses Jackson's polymorphism to allow for different types of exit logic.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StopLossConfig.class, name = "StopLoss"),
    @JsonSubTypes.Type(value = TakeProfitConfig.class, name = "TakeProfit")
})
public sealed interface ExitRuleConfig permits StopLossConfig, TakeProfitConfig {
}