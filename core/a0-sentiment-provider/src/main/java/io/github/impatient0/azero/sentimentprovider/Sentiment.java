package io.github.impatient0.azero.sentimentprovider;

/**
 * Represents the categorical sentiment of a piece of text or data.
 * <p>
 * This enum provides a standardized, type-safe way to represent the output of
 * a sentiment analysis process.
 */
public enum Sentiment {
    /**
     * Indicates a positive or optimistic sentiment, suggesting a potential upward
     * price movement.
     */
    BULLISH,

    /**
     * Indicates a negative or pessimistic sentiment, suggesting a potential
     * downward price movement.
     */
    BEARISH,

    /**
     * Indicates a lack of strong directional sentiment.
     */
    NEUTRAL
}