package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A sealed interface representing the configuration for a position sizing strategy.
 * This allows for different sizing methods (e.g., fixed percentage, fixed amount)
 * to be defined in the strategy YAML file.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FixedPercentageSizingConfig.class, name = "Fixed_Percentage")
})
public sealed interface PositionSizingConfig permits FixedPercentageSizingConfig {
}