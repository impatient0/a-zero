package io.github.impatient0.azero.preprocessor.cli;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.impatient0.azero.newsfeedclient.CsvNewsClient;
import io.github.impatient0.azero.newsfeedclient.RawNewsArticle;
import io.github.impatient0.azero.sentimentprovider.ProviderConfig;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import lombok.extern.slf4j.Slf4j;
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

    // Internal record to capture the result of a single article's processing
    private record ProcessingResult(
        RawNewsArticle article,
        List<SentimentSignal> signals,
        Throwable exception
    ) {
        boolean isSuccess() { return exception == null; }
    }

    @Override
    public Integer call() {
        log.info("--- A-Zero Sentiment Preprocessor Initializing ---");

        // 1. Load Data
        List<RawNewsArticle> articles;
        try {
            log.info("Loading news from: {}", rawNewsFile);
            CsvNewsClient newsClient = new CsvNewsClient();
            articles = newsClient.loadFromFile(rawNewsFile);
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
        // 15 RPM is the conservative default for Gemini Free Tier.
        // In the future, this could be configurable via flags/config.
        SimpleRateLimiter rateLimiter = new SimpleRateLimiter(15);
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);

        log.info("Starting processing with Concurrency={} and RateLimit=15 RPM...", maxConcurrency);

        List<CompletableFuture<ProcessingResult>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // 4. Submit Tasks
        for (RawNewsArticle article : articles) {
            CompletableFuture<ProcessingResult> future = CompletableFuture
                .supplyAsync(() -> {
                    // Blocking acquire inside the async task ensures we throttle
                    // the *start* of the API call, not the submission of the task.
                    rateLimiter.acquire();
                    return article;
                }, executor)
                .thenCompose(a -> provider.analyzeAsync(a.content(), a.timestamp()))
                .handle((signals, ex) -> new ProcessingResult(article, signals, ex));

            futures.add(future);
        }

        // 5. Wait for Completion (Resiliently)
        // We join on allOf, ignoring exceptions (because we handle them inside the individual futures)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 6. Aggregate Results
        List<ProcessingResult> results = futures.stream()
            .map(CompletableFuture::join) // Safe to join now as they are all done
            .toList();

        List<ProcessingResult> successes = results.stream().filter(ProcessingResult::isSuccess).toList();
        List<ProcessingResult> failures = results.stream().filter(r -> !r.isSuccess()).toList();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Processing complete in {} ms.", duration);
        log.info("Success: {} / {}", successes.size(), articles.size());
        log.info("Failed:  {} / {}", failures.size(), articles.size());

        if (!failures.isEmpty()) {
            log.warn("Some articles failed to process. Check logs for details. First failure: {}", failures.get(0).exception().getMessage());
        }

        executor.shutdown();

        // STEP 5 (Next): Write 'successes' to CSV
        // writeOutput(successes);

        return failures.isEmpty() ? 0 : 1; // Return 1 if there were partial failures, or 0 if perfect.
    }

    private SentimentProvider loadProvider(String name) {
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SentimentPreprocessorCli()).execute(args);
        System.exit(exitCode);
    }
}