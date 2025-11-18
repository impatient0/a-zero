package io.github.impatient0.azero.sentimentprovider;

import java.util.Map;
import java.util.Objects;

/**
 * A generic configuration container for initializing a {@link SentimentProvider}.
 * <p>
 * This class wraps a simple map, providing a standardized way for the framework
 * to pass configuration settings (e.g., API keys, model names, URLs) to any
 * provider implementation discovered via SPI.
 */
public class ProviderConfig {

    private final Map<String, String> settings;

    public ProviderConfig(Map<String, String> settings) {
        this.settings = Objects.requireNonNull(settings, "Settings map cannot be null.");
    }

    /**
     * Retrieves a configuration value as a String.
     *
     * @param key The configuration key.
     * @return The configuration value, or null if the key is not present.
     */
    public String getString(String key) {
        return settings.get(key);
    }

    /**
     * Retrieves a required configuration value as a String.
     *
     * @param key The configuration key.
     * @return The configuration value.
     * @throws IllegalArgumentException if the key is not present or the value is blank.
     */
    public String getRequiredString(String key) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(String.format("Required configuration key '%s' is missing or blank.", key));
        }
        return value;
    }
}