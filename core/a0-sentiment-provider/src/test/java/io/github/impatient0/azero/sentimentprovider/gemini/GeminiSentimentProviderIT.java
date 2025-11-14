package io.github.impatient0.azero.sentimentprovider.gemini;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.impatient0.azero.sentimentprovider.Sentiment;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gemini Sentiment Provider Integration Tests")
class GeminiSentimentProviderIT {

    private static final String MODEL_NAME = "gemini-2.5-flash-lite";
    private static Dotenv dotenv;

    @BeforeAll
    static void configure() {
        String projectRoot = System.getProperty("project.basedir", ".");

        dotenv = Dotenv.configure()
            .directory(projectRoot)
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();
    }

    @Nested
    @DisplayName("GIVEN a valid API key is available")
    class WithValidApiKey {

        private String apiKey;
        private GeminiSentimentProvider provider;

        @BeforeEach
        void setUp() {
            // --- ARRANGE ---
            apiKey = dotenv.get("GEMINI_API_KEY");
            Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "GEMINI_API_KEY environment variable not set. Skipping integration test.");
            provider = new GeminiSentimentProvider(apiKey, MODEL_NAME);
        }

        @Test
        @DisplayName("WHEN analyzing a bullish text, THEN it should return a bullish sentiment for the correct symbol")
        void analyzeAsync_withBullishText_shouldReturnBullishSentiment() throws Exception {
            // --- ARRANGE ---
            String bullishText = "Bitcoin price skyrockets to a new all-time high, showing strong upward momentum and investor confidence.";
            long timestamp = System.currentTimeMillis();

            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync(bullishText, timestamp);
            List<SentimentSignal> result = future.get(30, TimeUnit.SECONDS); // Block with timeout for assertion

            // --- ASSERT ---
            assertNotNull(result);
            assertFalse(result.isEmpty(), "The result list should not be empty for a bullish text.");

            boolean btcSignalFound = result.stream().anyMatch(
                signal -> signal.symbol().contains("BTC") && signal.sentiment() == Sentiment.BULLISH
                    && signal.timestamp() == timestamp);

            assertTrue(btcSignalFound, "A bullish sentiment signal for BTC with a correct timestamp was not found in the response.");
        }

        @Test
        @DisplayName("WHEN analyzing text with no crypto symbols, THEN it should return an empty list")
        void analyzeAsync_withNoSymbols_shouldReturnEmptyList() throws Exception {
            // --- ARRANGE ---
            String neutralText = "The weather is sunny today in the city.";

            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync(neutralText, System.currentTimeMillis());
            List<SentimentSignal> result = future.get(30, TimeUnit.SECONDS);

            // --- ASSERT ---
            assertNotNull(result);
            assertTrue(result.isEmpty(), "The result list should be empty for text with no relevant symbols.");
        }
    }

    @Nested
    @DisplayName("GIVEN an invalid API key")
    class WithInvalidApiKey {

        @Test
        @DisplayName("WHEN analyzing text, THEN it should fail with an authentication error")
        void analyzeAsync_withInvalidKey_shouldFailWithAuthenticationError() {
            // --- ARRANGE ---
            String invalidApiKey = "invalid-api-key-that-will-not-work";
            GeminiSentimentProvider provider = new GeminiSentimentProvider(invalidApiKey, MODEL_NAME);

            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("This will fail.", System.currentTimeMillis());

            // --- ASSERT ---
            CompletableFuture<List<SentimentSignal>> timedFuture = future.orTimeout(30, TimeUnit.SECONDS);

            CompletionException ex = assertThrows(CompletionException.class, timedFuture::join,
                "The future should complete exceptionally.");

            assertInstanceOf(SentimentProviderException.class, ex.getCause(),
                "The exception cause should be our custom SentimentProviderException.");
        }
    }
}