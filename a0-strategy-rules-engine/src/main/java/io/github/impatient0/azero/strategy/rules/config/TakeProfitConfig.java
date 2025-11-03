package io.github.impatient0.azero.strategy.rules.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a fixed percentage take-profit rule.
 * @param percentage The percentage gain from the entry price that triggers an exit.
 */
public record TakeProfitConfig(@JsonProperty("percentage") double percentage) implements ExitRuleConfig {
}