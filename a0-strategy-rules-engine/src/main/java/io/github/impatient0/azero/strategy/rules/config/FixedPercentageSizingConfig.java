package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a position sizing rule that allocates a fixed percentage of
 * the total account Net Asset Value (NAV) to a new position.
 *
 * @param percentage The percentage of NAV to allocate (e.g., 2.0 for 2%).
 */
public record FixedPercentageSizingConfig(
    @JsonProperty("percentage")
    double percentage
) implements PositionSizingConfig {
}