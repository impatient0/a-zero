# A-Zero :: Sentiment Provider

This library provides the core framework for integrating sentiment analysis into the A-Zero system. It defines a standard interface for querying sentiment analysis providers and the data models for representing sentiment signals.

## Core Components

### `SentimentProvider` Interface
This is the central abstraction of the module. It defines a single method, `analyze(String text)`, which takes raw text as input and is expected to return a list of `SentimentSignal` objects. Implementations of this interface will contain the logic for communicating with specific backends (e.g., an external LLM API).

### `SentimentSignal` Record
A simple, immutable data record that represents the output of a sentiment analysis for a single asset. It contains:
- `symbol` (String): The trading symbol (e.g., "BTCUSDT").
- `sentiment` (Enum): The categorical sentiment (`BULLISH`, `BEARISH`, `NEUTRAL`).
- `confidence` (double): A score from 0.0 to 1.0 representing the provider's confidence.

### `RandomSentimentProvider`
A dummy, in-memory implementation of `SentimentProvider` that is included for testing and development purposes. It ignores its input and returns a randomly generated `SentimentSignal`, allowing downstream modules to be tested without requiring network access or API keys.

## Usage Example

```java
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import io.github.impatient0.azero.sentimentprovider.dummy.RandomSentimentProvider;

import java.util.List;

public class SentimentExample {
    public static void main(String[] args) {
        // Instantiate a provider (using the dummy for this example)
        SentimentProvider provider = new RandomSentimentProvider();

        // Analyze a piece of text
        String newsHeadline = "Major financial institution announces plans to adopt Bitcoin.";
        List<SentimentSignal> signals = provider.analyze(newsHeadline);

        // Process the results
        for (SentimentSignal signal : signals) {
            System.out.printf(
                "Symbol: %s, Sentiment: %s, Confidence: %.2f%n",
                signal.symbol(),
                signal.sentiment(),
                signal.confidence()
            );
        }
        // Example Output:
        // Symbol: BTCUSDT, Sentiment: BULLISH, Confidence: 0.87
    }
}
```