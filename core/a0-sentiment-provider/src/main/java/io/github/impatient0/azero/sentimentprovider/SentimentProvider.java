package io.github.impatient0.azero.sentimentprovider;

import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract for a service that can analyze a piece of text to extract
 * financial sentiment signals. Implementations are expected to be asynchronous.
 */
public interface SentimentProvider {

    /**
     * Asynchronously analyzes the given text and extracts a list of sentiment signals for any
     * financial symbols identified within it.
     *
     * @param text The raw text to analyze (e.g., a news headline or article body).
     * @return A {@link CompletableFuture} which will complete with a {@link List} of
     *         {@link SentimentSignal} objects. The future will complete exceptionally
     *         with a {@link SentimentProviderException} if the analysis fails.
     */
    CompletableFuture<List<SentimentSignal>> analyzeAsync(String text);
}