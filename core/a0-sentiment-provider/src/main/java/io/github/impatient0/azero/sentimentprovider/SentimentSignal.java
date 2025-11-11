package io.github.impatient0.azero.sentimentprovider;

/**
 * Represents a single, actionable sentiment signal for a specific trading symbol.
 * <p>
 * This is an immutable data carrier that encapsulates the complete output from a
 * {@link SentimentProvider} for one asset.
 *
 * @param symbol     The trading symbol the sentiment applies to (e.g., "BTCUSDT").
 * @param sentiment  The categorical sentiment derived from the analysis.
 * @param confidence A score between 0.0 and 1.0 indicating the provider's confidence
 *                   in the assigned sentiment.
 */
public record SentimentSignal(
    String symbol,
    Sentiment sentiment,
    double confidence
) {}