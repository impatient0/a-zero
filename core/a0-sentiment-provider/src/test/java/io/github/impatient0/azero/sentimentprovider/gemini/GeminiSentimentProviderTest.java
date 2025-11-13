package io.github.impatient0.azero.sentimentprovider.gemini;

import com.google.genai.AsyncModels;
import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import io.github.impatient0.azero.sentimentprovider.Sentiment;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Gemini Sentiment Provider Unit Tests")
class GeminiSentimentProviderTest {

    private Client mockGeminiClient;
    private AsyncModels mockGenerativeModel;
    private GeminiSentimentProvider provider;

    private static final String FAKE_API_KEY = "test-api-key";
    private static final String FAKE_MODEL_NAME = "test-model";

    @BeforeEach
    void setUp() throws IllegalAccessException {
        // Mock the Gemini client and its internal structure for test isolation
        mockGeminiClient = mock(Client.class);
        Client.Async mockAsync = mock(Client.Async.class);
        mockGenerativeModel = mock(AsyncModels.class);

        // Use reflection to set the private fields of the mocked objects.
        // This is necessary because the Gemini SDK uses chained field references (client.async.models...)
        // and we need to mock the deepest dependency (AsyncModels) which is a field of an internal class.
        Field asyncField = ReflectionUtils.findFields(Client.class, f -> f.getName().equals("async"),
            HierarchyTraversalMode.TOP_DOWN).getFirst();

        Field modelsField = ReflectionUtils.findFields(Client.Async.class, f -> f.getName().equals("models"),
            HierarchyTraversalMode.TOP_DOWN).getFirst();

        modelsField.setAccessible(true);
        modelsField.set(mockAsync, mockGenerativeModel);

        asyncField.setAccessible(true);
        asyncField.set(mockGeminiClient, mockAsync);

        // Instantiate the provider, overriding createClient to return the mock
        provider = new GeminiSentimentProvider(FAKE_API_KEY, FAKE_MODEL_NAME) {
            @Override
            protected Client createClient(String apiKey) {
                return mockGeminiClient;
            }
        };
    }

    // Helper method to simulate a successful API response
    private void mockSuccessfulResponse(String jsonResponse) {
        GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
        when(mockResponse.text()).thenReturn(jsonResponse);
        CompletableFuture<GenerateContentResponse> future = CompletableFuture.completedFuture(mockResponse);
        when(mockGenerativeModel.generateContent(anyString(), anyString(), any(GenerateContentConfig.class))).thenReturn(future);
    }

    // Helper method to simulate a failed API call
    private void mockFailedResponse(Throwable exception) {
        CompletableFuture<GenerateContentResponse> future = CompletableFuture.failedFuture(exception);
        when(mockGenerativeModel.generateContent(anyString(), anyString(), any(GenerateContentConfig.class))).thenReturn(future);
    }

    @Nested
    @DisplayName("GIVEN an attempt to instantiate GeminiSentimentProvider")
    class ConstructorTests {

        @Test
        @DisplayName("WHEN API key is NULL, THEN it should throw IllegalArgumentException.")
        void constructor_withNullApiKey_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(null, FAKE_MODEL_NAME));
        }

        @Test
        @DisplayName("WHEN API key is BLANK, THEN it should throw IllegalArgumentException.")
        void constructor_withBlankApiKey_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(" ", FAKE_MODEL_NAME));
        }

        @Test
        @DisplayName("WHEN model name is NULL, THEN it should throw IllegalArgumentException.")
        void constructor_withNullModelName_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(FAKE_API_KEY, null));
        }

        @Test
        @DisplayName("WHEN model name is BLANK, THEN it should throw IllegalArgumentException.")
        void constructor_withBlankModelName_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(FAKE_API_KEY, ""));
        }
    }

    @Nested
    @DisplayName("GIVEN a call to analyzeAsync with non-text input")
    class AnalyzeAsyncInputValidationTests {
        @Test
        @DisplayName("WHEN input text is NULL, THEN it should immediately return an empty list without calling the API.")
        void analyzeAsync_whenInputTextIsNull_shouldReturnEmptyList() {
            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync(null);

            // --- ASSERT ---
            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
            assertTrue(future.join().isEmpty());
            // Verify no interaction with the client
            verify(mockGenerativeModel, never()).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
        }

        @Test
        @DisplayName("WHEN input text is BLANK, THEN it should immediately return an empty list without calling the API.")
        void analyzeAsync_whenInputTextIsBlank_shouldReturnEmptyList() {
            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("   ");

            // --- ASSERT ---
            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
            assertTrue(future.join().isEmpty());
            // Verify no interaction with the client
            verify(mockGenerativeModel, never()).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
        }
    }

    @Nested
    @DisplayName("GIVEN a successful setup for analyzeAsync")
    class AnalyzeAsyncApiInteractionTests {

        @Test
        @DisplayName("WHEN the Gemini API returns valid JSON, THEN it should complete successfully with the correct SentimentSignal list.")
        void analyzeAsync_whenApiReturnsValidJson_shouldCompleteSuccessfully() {
            // --- ARRANGE ---
            String validJson = """
            [
              {"symbol": "BTCUSDT", "sentiment": "BULLISH", "confidence": 0.95},
              {"symbol": "ETHUSDT", "sentiment": "NEUTRAL", "confidence": 0.6}
            ]
            """;
            mockSuccessfulResponse(validJson);

            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("Some positive news about Bitcoin.");
            List<SentimentSignal> result = future.join(); // Block for test assertion

            // --- ASSERT ---
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("BTCUSDT", result.get(0).symbol());
            assertEquals(Sentiment.BULLISH, result.get(0).sentiment());
            assertEquals(0.95, result.get(0).confidence());
            assertEquals("ETHUSDT", result.get(1).symbol());
            assertEquals(Sentiment.NEUTRAL, result.get(1).sentiment());
            assertEquals(0.6, result.get(1).confidence());
        }

        @Test
        @DisplayName("WHEN the Gemini API returns malformed JSON, THEN it should complete exceptionally with a SentimentProviderException.")
        void analyzeAsync_whenApiReturnsMalformedJson_shouldCompleteExceptionally() {
            // --- ARRANGE ---
            String malformedJson = "[{\"symbol\":\"BTCUSDT\",]"; // Missing closing braces
            mockSuccessfulResponse(malformedJson);

            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("test");

            // --- ASSERT ---
            CompletionException ex = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(SentimentProviderException.class, ex.getCause());
            assertTrue(ex.getMessage().contains("Failed to parse Gemini API response"));
        }

        @Test
        @DisplayName("WHEN the Gemini SDK throws a network/API exception, THEN it should complete exceptionally with a wrapped SentimentProviderException.")
        void analyzeAsync_whenSdkThrowsException_shouldCompleteExceptionally() {
            // --- ARRANGE ---
            mockFailedResponse(new GenAiIOException("Network error"));

            // --- ACT ---
            CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("test");

            // --- ASSERT ---
            CompletionException ex = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(SentimentProviderException.class, ex.getCause());
            assertInstanceOf(GenAiIOException.class, ex.getCause().getCause());
            assertTrue(ex.getMessage().contains("Failed to get sentiment due to API/network error."));
        }

        @Test
        @DisplayName("WHEN analyzeAsync is called, THEN it should construct a prompt that correctly embeds the input text.")
        void analyzeAsync_shouldConstructCorrectPrompt() {
            // --- ARRANGE ---
            mockSuccessfulResponse("[]");
            String inputText = "This is a test input.";
            String expectedPromptSubstring = "Text: \"" + inputText + "\"";

            // Use an ArgumentCaptor to capture the prompt string
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

            // --- ACT ---
            provider.analyzeAsync(inputText).join();

            // --- ASSERT ---
            // Verify that generateContent was called and capture the argument
            verify(mockGenerativeModel).generateContent(anyString(), promptCaptor.capture(), any(GenerateContentConfig.class));
            String actualPrompt = promptCaptor.getValue();

            assertTrue(actualPrompt.contains(expectedPromptSubstring), "The prompt did not contain the correct input text.");
        }
    }
}