package io.github.impatient0.azero.sentimentprovider;

import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract for a service that can analyze a piece of text to extract
 * financial sentiment signals. Implementations are expected to be asynchronous
 * and discoverable via Java's Service Provider Interface (SPI).
 */
public interface SentimentProvider {

    /**
     * Returns the unique, simple name for this provider (e.g., "GEMINI", "RANDOM").
     * This name is used by consumers like the CLI to select the desired provider.
     *
     * @return The unique name of the provider.
     */
    String getName();

    /**
     * Initializes the provider with the given configuration. This method will be
     * called once by the framework before any calls to {@link #analyzeAsync(String, long)}.
     * All expensive setup, such as creating API clients, should be done here.
     *
     * @param config The configuration object containing settings for this provider.
     */
    void init(ProviderConfig config);

    /**
     * Asynchronously analyzes the given text and extracts a list of sentiment signals.
     *
     * @param text      The raw text to analyze (e.g., a news headline or article body).
     * @param timestamp The timestamp from the original source data, to be associated
     *                  with the resulting signals.
     * @return A {@link CompletableFuture} which will complete with a {@link List} of
     *         {@link SentimentSignal} objects. The future will complete exceptionally
     *         with a {@link SentimentProviderException} if the analysis fails.
     */
    CompletableFuture<List<SentimentSignal>> analyzeAsync(String text, long timestamp);
}