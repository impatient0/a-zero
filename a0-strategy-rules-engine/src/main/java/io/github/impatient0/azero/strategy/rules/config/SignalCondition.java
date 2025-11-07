package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines the type of comparison to be used in an indicator rule.
 */
public enum SignalCondition {
    @JsonProperty("less_than")
    LESS_THAN,

    @JsonProperty("greater_than")
    GREATER_THAN
    // Future conditions like "crosses_above" could be added here
}