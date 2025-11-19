# A-Zero :: Sentiment Provider

This library provides the core framework for integrating sentiment analysis into the A-Zero system. It defines a standard interface for querying sentiment analysis providers and the data models for representing sentiment signals.

The core contract is defined by the `SentimentProvider` interface, which is designed to be asynchronous to support non-blocking, I/O-bound operations like network calls to external APIs.

## Core Components

### `SentimentProvider` Interface
This is the central abstraction. It is designed for **Service Provider Interface (SPI)** discovery and **Asynchronous** execution.

*   **Discovery:** Implementations are discovered at runtime using `java.util.ServiceLoader`.
*   **Initialization:** Providers are initialized via `init(ProviderConfig)` rather than constructors.
*   **Execution:** The `analyzeAsync` method returns a `CompletableFuture` to support non-blocking I/O.

### `SentimentSignal` Record
A simple, immutable data record that represents the output of a sentiment analysis for a single asset. It contains:
- `timestamp` (long): The timestamp of the source data (e.g., news article time).
- `symbol` (String): The trading symbol (e.g., "BTCUSDT").
- `sentiment` (Enum): The categorical sentiment (`BULLISH`, `BEARISH`, `NEUTRAL`).
- `confidence` (double): A score from 0.0 to 1.0 representing the provider's confidence.

---

## Included Implementations

### 1. `GeminiSentimentProvider` (SPI Name: `GEMINI`)
A production-ready implementation using the Google Gemini API.
*   **Features:** Structured JSON output, internal rate limiting (defaults to 15 RPM), automatic retries on 429 errors.
*   **Configuration:** Requires `apiKey` in the `ProviderConfig`.

### 2. `RandomSentimentProvider` (SPI Name: `RANDOM`)
A dummy implementation for testing.
*   **Features:** Returns random signals immediately. No network usage.
*   **Configuration:** None required.

---

## Usage Example (SPI)

```java
import io.github.impatient0.azero.sentimentprovider.ProviderConfig;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import java.util.Map;
import java.util.ServiceLoader;

public class SentimentExample {
    public static void main(String[] args) {
        // 1. Discover Provider via SPI
        ServiceLoader<SentimentProvider> loader = ServiceLoader.load(SentimentProvider.class);
        SentimentProvider provider = loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(p -> p.getName().equals("GEMINI"))
            .findFirst()
            .orElseThrow();

        // 2. Initialize
        Map<String, String> configMap = Map.of("apiKey", System.getenv("GEMINI_API_KEY"));
        provider.init(new ProviderConfig(configMap));

        // 3. Analyze Asynchronously
        long articleTime = System.currentTimeMillis();
        String text = "Bitcoin hits all-time high!";

        provider.analyzeAsync(text, articleTime)
            .thenAccept(signals -> {
                signals.forEach(System.out::println);
            })
            .join(); // Block for demonstration
    }
}
```
## Future Roadmap & Known Limitations

The following items are currently in the backlog for this library:

*   **Automatic Schema Generation:** Currently, the JSON schema sent to the Gemini API is manually constructed using the SDK's builder. A future enhancement should generate this schema dynamically from the `SentimentSignal` record class (using reflection or a library like Jackson) to ensure a single source of truth and reduce maintenance overhead.
*   **Token Bucket Rate Limiter:** The current `SimpleRateLimiter` enforces a strict interval between requests. Implementing a Token Bucket algorithm would allow for better handling of request bursts and improved throughput while still respecting API quotas.