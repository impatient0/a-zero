package io.github.impatient0.azero.sentimentprovider;

import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import java.util.List;

/**
 * Defines the contract for a service that can analyze a piece of text to extract
 * financial sentiment signals.
 * <p>
 * Implementations of this interface will encapsulate the logic for communicating
 * with a specific sentiment analysis backend, such as an external LLM API or a
 * local model.
 */
public interface SentimentProvider {

    /**
     * Analyzes the given text and extracts a list of sentiment signals for any
     * financial symbols identified within it.
     * <p>
     * The implementation is expected to perform entity recognition to identify
     * relevant symbols (e.g., "Bitcoin" -> "BTCUSDT") and then determine the
     * sentiment for each.
     *
     * @param text The raw text to analyze (e.g., a news headline or article body).
     * @return A {@link List} of {@link SentimentSignal} objects. The list may be
     *         empty if no relevant symbols or sentiment can be extracted from the text.
     * @throws SentimentProviderException if an error occurs during the analysis process,
     *         such as communication failure with the backend or an internal processing error.
     */
    List<SentimentSignal> analyze(String text) throws SentimentProviderException;
}