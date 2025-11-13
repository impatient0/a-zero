# A-Zero :: Sentiment Provider

This library provides the core framework for integrating sentiment analysis into the A-Zero system. It defines a standard interface for querying sentiment analysis providers and the data models for representing sentiment signals.

The core contract is defined by the `SentimentProvider` interface, which is designed to be asynchronous to support non-blocking, I/O-bound operations like network calls to external APIs.

## Core Components

### `SentimentProvider` Interface
This is the central abstraction of the module. It defines a single method, `analyzeAsync(String text)`, which takes raw text as input and returns a `CompletableFuture<List<SentimentSignal>>`. Implementations of this interface contain the logic for communicating with specific backends (e.g., an external LLM API).

### `SentimentSignal` Record
A simple, immutable data record that represents the output of a sentiment analysis for a single asset. It contains:
- `symbol` (String): The trading symbol (e.g., "BTCUSDT").
- `sentiment` (Enum): The categorical sentiment (`BULLISH`, `BEARISH`, `NEUTRAL`).
- `confidence` (double): A score from 0.0 to 1.0 representing the provider's confidence.

---

## Implementations

### `GeminiSentimentProvider`
A production-ready, asynchronous implementation of `SentimentProvider` that uses the Google Gemini API for sentiment analysis. It leverages the model's structured output capabilities to ensure a reliable and parseable JSON response.

#### Configuration and API Key
To use this provider, you must have a Google Gemini API key. For security, it is **strongly recommended** to provide this key via an environment variable and **never hardcode it in your source code.**

The provider expects the key to be loaded from the environment and passed into its constructor:
```java
String apiKey = System.getenv("GEMINI_API_KEY");
if (apiKey == null || apiKey.isBlank()) {
    throw new IllegalStateException("GEMINI_API_KEY environment variable not set.");
}
String modelName = "gemini-2.5-flash-lite"; // Or any other suitable model
SentimentProvider provider = new GeminiSentimentProvider(apiKey, modelName);
```

#### Asynchronous Usage Example
```java
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.gemini.GeminiSentimentProvider;

public class SentimentExample {
    public static void main(String[] args) {
        // --- 1. Configuration ---
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null) {
            System.err.println("Error: GEMINI_API_KEY environment variable is not set.");
            return;
        }
        SentimentProvider provider = new GeminiSentimentProvider(apiKey, "gemini-1.5-flash-latest");

        // --- 2. Asynchronous API Call ---
        String newsHeadline = "Major financial institution announces plans to adopt Bitcoin.";
        provider.analyzeAsync(newsHeadline)
            .thenAccept(signals -> {
                // --- 3. Handle Successful Result ---
                System.out.println("Analysis successful. Signals found: " + signals.size());
                signals.forEach(signal ->
                    System.out.printf(
                        "  - Symbol: %s, Sentiment: %s, Confidence: %.2f%n",
                        signal.symbol(),
                        signal.sentiment(),
                        signal.confidence()
                    )
                );
            })
            .exceptionally(ex -> {
                // --- 4. Handle Failure ---
                System.err.println("Sentiment analysis failed: " + ex.getMessage());
                return null; // Return null to end the exceptionally chain
            })
            .join(); // Block for the result in this simple main method example
    }
}
```

### `RandomSentimentProvider`
A dummy, in-memory implementation of `SentimentProvider` that is included for testing and development purposes. It ignores its input and returns a randomly generated `SentimentSignal` wrapped in an already-completed `CompletableFuture`. It requires no API key.