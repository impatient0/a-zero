package io.github.impatient0.azero.sentimentprovider.dummy;

import io.github.impatient0.azero.sentimentprovider.Sentiment;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import io.github.impatient0.azero.sentimentprovider.exception.SentimentProviderException;

import java.util.List;
import java.util.Random;

/**
 * A dummy implementation of {@link SentimentProvider} for testing and development.
 * <p>
 * This provider ignores the input text and instead returns a randomly generated
 * {@link SentimentSignal}. It is useful for testing the integration of sentiment
 * analysis components without needing to connect to a live external API, thus
 * avoiding costs and network dependencies in test environments.
 */
public class RandomSentimentProvider implements SentimentProvider {

    private static final List<String> SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT");
    private static final Sentiment[] SENTIMENTS = Sentiment.values();
    private final Random random = new Random();

    /**
     * Generates a single, random sentiment signal while completely ignoring the input text.
     * <p>
     * This method is designed for testing and development. It randomly selects a
     * symbol and sentiment from a predefined list and generates a random confidence value.
     * It will always return a list containing exactly one {@link SentimentSignal}.
     * <p>
     * This implementation does not perform any real analysis and will never throw a
     * {@link SentimentProviderException}.
     *
     * @param text The raw text to analyze (ignored by this implementation).
     * @return A non-null {@link List} containing exactly one randomly generated
     *         {@link SentimentSignal}.
     */
    @Override
    public List<SentimentSignal> analyze(String text) {
        String randomSymbol = SYMBOLS.get(random.nextInt(SYMBOLS.size()));
        Sentiment randomSentiment = SENTIMENTS[random.nextInt(SENTIMENTS.length)];
        double randomConfidence = random.nextDouble(); // Generates a double between 0.0 (inclusive) and 1.0 (exclusive)

        SentimentSignal signal = new SentimentSignal(randomSymbol, randomSentiment, randomConfidence);

        return List.of(signal);
    }
}