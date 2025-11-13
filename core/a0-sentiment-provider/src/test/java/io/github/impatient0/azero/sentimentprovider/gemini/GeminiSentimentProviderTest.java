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

/**
 * Unit tests for {@link GeminiSentimentProvider}.
 * <p>
 * These tests use Mockito to mock the Gemini client, ensuring that no real
 * network calls are made.
 */
class GeminiSentimentProviderTest {

    private Client mockGeminiClient;
    private AsyncModels mockGenerativeModel;
    private GeminiSentimentProvider provider;

    private static final String FAKE_API_KEY = "test-api-key";
    private static final String FAKE_MODEL_NAME = "test-model";

    @BeforeEach
    void setUp() throws IllegalAccessException {
        mockGeminiClient = mock(Client.class);
        Client.Async mockAsync = mock(Client.Async.class);
        mockGenerativeModel = mock(AsyncModels.class);

        Field asyncField = ReflectionUtils.findFields(Client.class, f -> f.getName().equals("async"),
            HierarchyTraversalMode.TOP_DOWN).getFirst();

        Field modelsField = ReflectionUtils.findFields(Client.Async.class, f -> f.getName().equals("models"),
            HierarchyTraversalMode.TOP_DOWN).getFirst();

        Field clientField = ReflectionUtils.findFields(GeminiSentimentProvider.class, f -> f.getName().equals("geminiClient"),
            HierarchyTraversalMode.TOP_DOWN).getFirst();

        modelsField.setAccessible(true);
        modelsField.set(mockAsync, mockGenerativeModel);

        asyncField.setAccessible(true);
        asyncField.set(mockGeminiClient, mockAsync);

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

    @Test
    void analyzeAsync_whenApiReturnsValidJson_shouldCompleteSuccessfully() {
        // Arrange
        String validJson = """
            [
              {"symbol": "BTCUSDT", "sentiment": "BULLISH", "confidence": 0.95},
              {"symbol": "ETHUSDT", "sentiment": "NEUTRAL", "confidence": 0.6}
            ]
            """;
        mockSuccessfulResponse(validJson);

        // Act
        CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("Some positive news about Bitcoin.");
        List<SentimentSignal> result = future.join(); // Block for test assertion

        // Assert
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
    void analyzeAsync_whenApiReturnsMalformedJson_shouldCompleteExceptionally() {
        // Arrange
        String malformedJson = "[{\"symbol\":\"BTCUSDT\",]"; // Missing closing braces
        mockSuccessfulResponse(malformedJson);

        // Act
        CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("test");

        // Assert
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(SentimentProviderException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("Failed to parse Gemini API response"));
    }

    @Test
    void analyzeAsync_whenSdkThrowsException_shouldCompleteExceptionally() {
        // Arrange
        mockFailedResponse(new GenAiIOException("Network error"));

        // Act
        CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("test");

        // Assert
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(SentimentProviderException.class, ex.getCause());
        assertInstanceOf(GenAiIOException.class, ex.getCause().getCause());
        assertTrue(ex.getMessage().contains("Failed to get sentiment due to API/network error."));
    }

    @Test
    void analyzeAsync_whenInputTextIsNull_shouldReturnEmptyList() {
        // Act
        CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync(null);

        // Assert
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertTrue(future.join().isEmpty());
        // Verify no interaction with the client
        verify(mockGeminiClient.async.models, never()).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
    }

    @Test
    void analyzeAsync_whenInputTextIsBlank_shouldReturnEmptyList() {
        // Act
        CompletableFuture<List<SentimentSignal>> future = provider.analyzeAsync("   ");

        // Assert
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertTrue(future.join().isEmpty());
        verify(mockGeminiClient.async.models, never()).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
    }

    @Test
    void constructor_withNullApiKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(null, FAKE_MODEL_NAME));
    }

    @Test
    void constructor_withBlankApiKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(" ", FAKE_MODEL_NAME));
    }

    @Test
    void constructor_withNullModelName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(FAKE_API_KEY, null));
    }

    @Test
    void constructor_withBlankModelName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiSentimentProvider(FAKE_API_KEY, ""));
    }

    @Test
    void analyzeAsync_shouldConstructCorrectPrompt() {
        // Arrange
        mockSuccessfulResponse("[]");
        String inputText = "This is a test input.";
        String expectedPromptSubstring = "Text: \"" + inputText + "\"";

        // Use an ArgumentCaptor to capture the prompt string
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        provider.analyzeAsync(inputText).join();

        // Assert
        // Verify that generateContent was called and capture the argument
        verify(mockGenerativeModel).generateContent(promptCaptor.capture(), anyString(), any(GenerateContentConfig.class));
        String actualPrompt = promptCaptor.getValue();

        System.err.println("actual prompt: " + actualPrompt);

        assertTrue(actualPrompt.contains(expectedPromptSubstring), "The prompt did not contain the correct input text.");
    }
}