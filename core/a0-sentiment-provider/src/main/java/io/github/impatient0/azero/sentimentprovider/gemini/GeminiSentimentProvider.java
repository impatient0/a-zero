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
import io.github.impatient0.azero.sentimentprovider.ProviderConfig;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;
import io.github.impatient0.azero.sentimentprovider.util.SimpleRateLimiter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

/**
 * An implementation of {@link SentimentProvider} that uses the Google Gemini API
 * to perform sentiment analysis.
 */
@Slf4j
public class GeminiSentimentProvider implements SentimentProvider {

    /**
     * The underlying {@link Client} used to communicate with the Google Gemini API.
     */
    private Client geminiClient;
    /**
     * The name of the Gemini model to use for analysis (e.g., "gemini-2.5-flash-lite").
     */
    private String modelName;
    /**
     * The Jackson {@link ObjectMapper} for parsing the JSON response from the Gemini API.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Flag indicating whether the provider has been initialized via the {@link #init(ProviderConfig)} method.
     */
    private boolean isInitialized = false;
    /**
     * Rate limiter to control the frequency of API requests based on the configured RPM.
     */
    private SimpleRateLimiter rateLimiter;
    /**
     * Executor service that uses virtual threads for executing rate limit acquisition and API calls asynchronously.
     */
    private ExecutorService virtualThreadExecutor;

    /**
     * The template for the prompt sent to the Gemini model.
     * It instructs the model to perform sentiment analysis and return a structured JSON array.
     */
    private static final String PROMPT_TEMPLATE = """
        Analyze the following text for its sentiment towards any mentioned cryptocurrency symbols \
        (e.g., BTC, ETH). Your response MUST be a valid JSON array of objects. Each object must contain \
        three fields: "symbol", "sentiment" ("BULLISH", "BEARISH", or "NEUTRAL"), and \
        "confidence" (a number between 0.0 and 1.0). If no symbols are mentioned, return an empty array [].
        
        Text: "{inputText}\"""";

    /**
     * Configuration for content generation, specifically enforcing JSON output
     * based on the defined {@link Schema} for the list of {@link SentimentSignal}s.
     */
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
     * Default, parameterless constructor required for SPI.
     */
    public GeminiSentimentProvider() {
    }

    @Override
    public String getName() {
        return "GEMINI";
    }

    /**
     * Initializes the {@code GeminiSentimentProvider} by configuring the underlying
     * {@link Client} for the Google Gemini API.
     *
     * @param config The {@link ProviderConfig} containing necessary initialization
     *               parameters, most importantly the {@code "apiKey"}.
     */
    @Override
    public void init(ProviderConfig config) {
        String apiKey = config.getRequiredString("apiKey");
        // TODO: un-hardcode model name
        this.modelName = "gemini-2.5-flash-lite";

        int rpm = 15;
        String rpmStr = config.getString("requestsPerMinute");
        if (rpmStr != null && !rpmStr.isBlank()) {
            try {
                rpm = Integer.parseInt(rpmStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid requestsPerMinute config '{}', defaulting to 15.", rpmStr);
            }
        }

        this.rateLimiter = new SimpleRateLimiter(rpm);
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API is blank or missing.");
        }

        this.geminiClient = createClient(apiKey);
        this.isInitialized = true;
        log.info("GeminiSentimentProvider initialized for model '{}'.", this.modelName);
    }

    /**
     * Creates the Gemini Client. Extracted to a protected method to allow for
     * overriding in unit tests for mock injection.
     *
     * @param apiKey The API key used for authentication.
     * @return A configured {@link Client} instance.
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
     * @param timestamp The timestamp to associate with the resulting {@link SentimentSignal}s.
     * @return A non-null {@link CompletableFuture} that, upon completion, holds a
     *         {@link List} of {@link SentimentSignal}s extracted from the text.
     * @throws SentimentProviderException if there is a communication failure with the
     *         Gemini API or if the API's response cannot be parsed (will be set as the
     *         exceptionally completed value of the returned future).
     */
    @Override
    public CompletableFuture<List<SentimentSignal>> analyzeAsync(String text, long timestamp) {
        if (!isInitialized) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("GeminiSentimentProvider has not been initialized. Call init() first."));
        }

        if (text == null || text.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String prompt = PROMPT_TEMPLATE.replace("{inputText}", text);

        return CompletableFuture.supplyAsync(() -> {
                rateLimiter.acquire();
                return null;
            }, virtualThreadExecutor)
            .thenCompose(v -> {
                log.debug("Sending async request to Gemini model '{}'...", modelName);
                return geminiClient.async.models.generateContent(this.modelName, prompt, JSON_CONFIG);
            })
            .handle((response, throwable) -> {
                if (throwable != null) {
                    log.error("A network or API error occurred while contacting the Gemini API.", throwable);
                    throw new SentimentProviderException("Failed to get sentiment due to API/network error.", throwable);
                }

                String jsonResponse = response.text();
                log.debug("Received async raw JSON response from Gemini: {}", jsonResponse);
                try {
                    List<SentimentSignal> signals = objectMapper.readValue(jsonResponse, new TypeReference<>() {});
                    return signals.stream()
                        .map(s -> new SentimentSignal(timestamp, s.symbol(), s.sentiment(), s.confidence()))
                        .toList();
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse a malformed JSON response from the Gemini API.", e);
                    throw new SentimentProviderException("Failed to parse Gemini API response.", e);
                }
            });
    }
}