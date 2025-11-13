package io.github.impatient0.azero.sentimentprovider.exception;

import io.github.impatient0.azero.sentimentprovider.SentimentProvider;

/**
 * Custom runtime exception thrown when a {@link SentimentProvider} fails to
 * process a request.
 * <p>
 * This exception wraps underlying causes, such as network errors, API-specific
 * issues, or response parsing failures, providing a consistent error type for
 * consumers of the sentiment provider module.
 */
public class SentimentProviderException extends RuntimeException {

    /**
     * Constructs a new SentimentProviderException with the specified detail message and cause.
     *
     * @param message The detail message (which is saved for later retrieval by the
     *                {@link #getMessage()} method).
     * @param cause   The cause (which is saved for later retrieval by the
     *                {@link #getCause()} method).
     */
    public SentimentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}