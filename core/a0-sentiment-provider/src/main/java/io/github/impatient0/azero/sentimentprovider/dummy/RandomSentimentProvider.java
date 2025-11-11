package io.github.impatient0.azero.sentimentprovider.dummy;

import io.github.impatient0.azero.sentimentprovider.Sentiment;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;

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
     * Ignores the input text and returns a list containing a single, randomly
     * generated sentiment signal.
     *
     * @param text The raw text to analyze (ignored by this implementation).
     * @return A list with one {@link SentimentSignal} containing random data.
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