package io.github.impatient0.azero.preprocessor.cli;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.impatient0.azero.newsfeedclient.CsvNewsClient;
import io.github.impatient0.azero.newsfeedclient.RawNewsArticle;
import io.github.impatient0.azero.sentimentprovider.ProviderConfig;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main command-line interface for the A-Zero sentiment preprocessor application.
 * <p>
 * This class uses picocli to parse command-line arguments and orchestrates the
 * loading of raw news data, sentiment analysis using a pluggable provider, and
 * concurrent processing to generate a cleaned CSV of sentiment signals.
 */
@Slf4j
@Command(
    name = "a0-sentiment-preprocessor-cli",
    mixinStandardHelpOptions = true,
    version = "A-Zero Sentiment Preprocessor CLI 0.3.0",
    description = "Processes raw news data to generate and store sentiment signals."
)
public class SentimentPreprocessorCli implements Callable<Integer> {

    @Option(names = {"-i", "--raw-news-file"}, required = true, description = "The path to the input CSV file containing raw news (timestamp,content).")
    private Path rawNewsFile;

    @Option(names = {"-o", "--output-file"}, required = true, description = "The path for the output CSV file where sentiment signals will be saved.")
    private Path outputFile;

    @Option(names = {"-p", "--provider"}, description = "The name of the sentiment provider to use (e.g., GEMINI).", defaultValue = "GEMINI")
    private String providerName;

    @Option(names = {"-c", "--max-concurrency"}, description = "The maximum number of concurrent API requests to make.", defaultValue = "4")
    private int maxConcurrency;

    /**
     * Internal record to capture the result of a single article's processing.
     *
     * @param article The original raw news article.
     * @param signals The list of sentiment signals generated from the article (empty if failed).
     * @param exception The exception that occurred during processing, or null if successful.
     */
    private record ProcessingResult(
        RawNewsArticle article,
        List<SentimentSignal> signals,
        Throwable exception
    ) {
        boolean isSuccess() { return exception == null; }
    }

    /**
     * The main application logic. This method is executed when the command is run.
     *
     * @return 0 on success, 1 on failure.
     */
    @Override
    public Integer call() {
        log.info("--- A-Zero Sentiment Preprocessor Initializing ---");

        // 1. Load Data
        List<RawNewsArticle> articles;
        try {
            log.info("Loading news from: {}", rawNewsFile);
            articles = loadArticles(rawNewsFile);
            log.info("Loaded {} articles.", articles.size());
        } catch (Exception e) {
            log.error("Failed to load input file.", e);
            return 1;
        }

        // 2. Discover and Initialize Provider
        SentimentProvider provider;
        try {
            provider = loadProvider(providerName);
            log.info("Using Sentiment Provider: {}", provider.getName());
        } catch (Exception e) {
            log.error("Provider initialization failed: {}", e.getMessage());
            return 1;
        }

        // 3. Setup Concurrency and Rate Limiting
        // TODO: make rate limit configurable
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);

        log.info("Starting processing with Concurrency={} and RateLimit=15 RPM...", maxConcurrency);

        List<CompletableFuture<ProcessingResult>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // 4. Submit Tasks
        for (RawNewsArticle article : articles) {
            CompletableFuture<ProcessingResult> future = CompletableFuture
                .supplyAsync(() -> article, executor)
                .thenCompose(a -> provider.analyzeAsync(a.content(), a.timestamp()))
                .handle((signals, ex) -> new ProcessingResult(article, signals, ex));

            futures.add(future);
        }

        // 5. Wait for Completion (Resiliently)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 6. Aggregate Results
        List<ProcessingResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        List<ProcessingResult> successes = results.stream().filter(ProcessingResult::isSuccess).toList();
        List<ProcessingResult> failures = results.stream().filter(r -> !r.isSuccess()).toList();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Processing complete in {} ms.", duration);
        log.info("Success: {} / {}", successes.size(), articles.size());
        log.info("Failed:  {} / {}", failures.size(), articles.size());

        if (!failures.isEmpty()) {
            log.warn("Some articles failed to process. Check logs for details. First failure: {}", failures.getFirst().exception().getMessage());
        }

        executor.shutdown();

        // 7: Write 'successes' to CSV
        if (!successes.isEmpty()) {
            try {
                writeOutput(successes);
            } catch (IOException e) {
                log.error("Failed to write output CSV.", e);
                return 1;
            }
        } else {
            log.warn("No successful results to write.");
        }

        executor.shutdown();
        return failures.isEmpty() ? 0 : 1;
    }

    /**
     * Writes the successful sentiment signals to the output CSV file.
     * @param results The list of successful processing results.
     * @throws IOException if an error occurs while writing to the file.
     */
    private void writeOutput(List<ProcessingResult> results) throws IOException {
        log.info("Writing results to: {}", outputFile);

        // Ensure parent directories exist
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        // Define CSV Format: timestamp,symbol,sentiment,confidence
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader("timestamp", "symbol", "sentiment", "confidence")
            .get();

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            int signalCount = 0;
            for (ProcessingResult result : results) {
                for (SentimentSignal signal : result.signals()) {
                    csvPrinter.printRecord(
                        signal.timestamp(),
                        signal.symbol(),
                        signal.sentiment(),
                        signal.confidence()
                    );
                    signalCount++;
                }
            }
            log.info("Wrote {} sentiment signals to CSV.", signalCount);
        }
    }

    /**
     * Loads raw article data from the input CSV file using a {@link CsvNewsClient}.
     * @param path {@link Path} to the raw news file.
     * @return A list of articles loaded from the file.
     * @throws IOException if an error occurs while reading the file.
     */
    protected List<RawNewsArticle> loadArticles(Path path) throws IOException {
        CsvNewsClient newsClient = new CsvNewsClient();
        return newsClient.loadFromFile(rawNewsFile);
    }

    /**
     * Discovers and initializes the specified {@code SentimentProvider} using Java's {@code ServiceLoader}.
     * <p>
     * The provider is configured with an API key looked up from environment variables,
     * following the convention: {@code PROVIDER_NAME_API_KEY} (e.g., GEMINI_API_KEY).
     *
     * @param name The case-insensitive name of the provider to load (e.g., "GEMINI").
     * @return The initialized SentimentProvider instance.
     * @throws IllegalArgumentException if no provider with the given name is found.
     */
    protected SentimentProvider loadProvider(String name) {
        ServiceLoader<SentimentProvider> loader = ServiceLoader.load(SentimentProvider.class);
        Optional<SentimentProvider> providerOpt = loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(p -> p.getName().equalsIgnoreCase(name))
            .findFirst();

        SentimentProvider provider = providerOpt.orElseThrow(() ->
            new IllegalArgumentException("No provider found with name: " + name));

        Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load();

        // Convention: GEMINI -> GEMINI_API_KEY
        String envVarKey = name.toUpperCase() + "_API_KEY";
        String apiKey = dotenv.get(envVarKey);

        Map<String, String> configMap = new HashMap<>();
        if (apiKey != null) {
            configMap.put("apiKey", apiKey);
        } else {
            log.warn("Warning: API Key environment variable '{}' not found.", envVarKey);
        }

        provider.init(new ProviderConfig(configMap));
        return provider;
    }

    /**
     * The main entry point for the executable JAR.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new SentimentPreprocessorCli()).execute(args);
        System.exit(exitCode);
    }
}