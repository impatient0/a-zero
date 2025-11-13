package io.github.impatient0.azero.sentimentprovider.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * An implementation of {@link SentimentProvider} that uses the Google Gemini API
 * to perform sentiment analysis.
 */
@Slf4j
public class GeminiSentimentProvider implements SentimentProvider {

    private final Client geminiClient;
    private final String modelName;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_TEMPLATE = """
        Analyze the following text for its sentiment towards any mentioned cryptocurrency symbols \
        (e.g., BTC, ETH). Your response MUST be a valid JSON array of objects. Each object must contain \
        three fields: "symbol", "sentiment" ("BULLISH", "BEARISH", or "NEUTRAL"), and \
        "confidence" (a number between 0.0 and 1.0). If no symbols are mentioned, return an empty array [].
        
        Text: "{inputText}\"""";

    private static final GenerateContentConfig JSON_CONFIG;

    static {
        Schema signalSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(ImmutableMap.of(
                "symbol", Schema.builder().type(Type.Known.STRING).build(),
                "sentiment", Schema.builder()
                    .type(Type.Known.STRING)
                    .enum_("BULLISH", "BEARISH", "NEUTRAL")
                    .build(),
                "confidence", Schema.builder().type(Type.Known.NUMBER).build()
            ))
            .required("symbol", "sentiment", "confidence")
            .build();

        Schema rootSchema = Schema.builder()
            .type(Type.Known.ARRAY)
            .items(signalSchema)
            .build();

        JSON_CONFIG = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(rootSchema)
            .build();
    }

    /**
     * Constructs a new GeminiSentimentProvider.
     *
     * @param apiKey    The Google Gemini API key. Must not be null or blank.
     * @param modelName The name of the Gemini model to use (e.g., "gemini-2.5-flash"). Must not be null or blank.
     * @throws IllegalArgumentException if apiKey or modelName are null or blank.
     */
    public GeminiSentimentProvider(String apiKey, String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or blank.");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be null or blank.");
        }

        this.geminiClient = createClient(apiKey);
        this.modelName = modelName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates the Gemini Client. Extracted to a protected method to allow for
     * overriding in unit tests for mock injection.
     */
    protected Client createClient(String apiKey) {
        HttpOptions httpOptions = HttpOptions.builder()
            .retryOptions(
                HttpRetryOptions.builder()
                    .attempts(3)
                    .build())
            .build();
        return Client.builder().apiKey(apiKey).httpOptions(httpOptions).build();
    }

    /**
     * Asynchronously sends the input text to the Google Gemini API for sentiment analysis.
     * <p>
     * This implementation sends the input text to the Google Gemini API using a
     * structured JSON prompt. It returns a {@link CompletableFuture} that completes
     * with an empty list if the input text is null or blank without making an API call.
     *
     * @param text The raw text to analyze.
     * @return A non-null {@link CompletableFuture} that, upon completion, holds a
     *         {@link List} of {@link SentimentSignal}s extracted from the text.
     * @throws SentimentProviderException if there is a communication failure with the
     *         Gemini API or if the API's response cannot be parsed (will be set as the
     *         exceptionally completed value of the returned future).
     */
    @Override
    public CompletableFuture<List<SentimentSignal>> analyzeAsync(String text) {
        if (text == null || text.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String prompt = PROMPT_TEMPLATE.replace("{inputText}", text);

        log.debug("Sending async request to Gemini model '{}'...", modelName);
        return geminiClient.async.models
            .generateContent(this.modelName, prompt, JSON_CONFIG)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    log.error("A network or API error occurred while contacting the Gemini API.", throwable);
                    throw new SentimentProviderException("Failed to get sentiment due to API/network error.", throwable);
                }

                String jsonResponse = response.text();
                log.debug("Received async raw JSON response from Gemini: {}", jsonResponse);
                try {
                    return objectMapper.readValue(jsonResponse, new TypeReference<>() {});
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse a malformed JSON response from the Gemini API.", e);
                    throw new SentimentProviderException("Failed to parse Gemini API response.", e);
                }
            });
    }
}