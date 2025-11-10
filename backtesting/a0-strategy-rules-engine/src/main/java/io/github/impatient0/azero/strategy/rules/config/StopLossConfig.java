package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a fixed percentage stop-loss rule.
 * @param percentage The percentage drop from the entry price that triggers an exit.
 */
public record StopLossConfig(@JsonProperty("percentage") double percentage) implements ExitRuleConfig {
}