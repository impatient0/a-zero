package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A sealed interface representing the configuration for a single technical indicator rule.
 * This uses Jackson's polymorphism to allow for different types of indicators
 * to be defined in the strategy YAML file.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "indicator" // The field in YAML that determines the type
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RsiIndicatorConfig.class, name = "RSI")
    // Future indicators like "EMA_Cross" would be added here
})
public sealed interface IndicatorConfig permits RsiIndicatorConfig {
}