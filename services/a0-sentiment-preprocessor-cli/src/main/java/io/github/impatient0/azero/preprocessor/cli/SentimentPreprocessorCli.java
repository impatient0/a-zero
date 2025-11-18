package io.github.impatient0.azero.preprocessor.cli;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The main command-line interface for the A-Zero sentiment preprocessor application.
 * <p>
 * This tool reads raw news data from a local CSV, analyzes it for sentiment using
 * a configured {@link io.github.impatient0.azero.sentimentprovider.SentimentProvider}
 * (discovered via SPI), and saves the structured sentiment signals to a new CSV file.
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

    @Option(names = {"-p", "--provider"}, description = "The name of the sentiment provider to use (e.g., GEMINI). Case-sensitive matching against SPI implementations.", defaultValue = "GEMINI")
    private String providerName;

    @Option(names = {"-c", "--max-concurrency"}, description = "The maximum number of concurrent API requests to make. Default is conservative to respect rate limits.", defaultValue = "4")
    private int maxConcurrency;

    /**
     * The main application logic. This method is executed when the command is run.
     *
     * @return 0 on success, 1 on failure.
     */
    @Override
    public Integer call() {
        log.info("--- A-Zero Sentiment Preprocessor Initializing ---");
        log.info("Input File:      {}", rawNewsFile);
        log.info("Output File:     {}", outputFile);
        log.info("Provider:        {}", providerName);
        log.info("Max Concurrency: {}", maxConcurrency);

        // NOTE: Core logic integration (Service Discovery, Rate Limiting, Execution)
        // will be implemented in the next step.

        log.info("Initialization complete. (Stub execution)");

        return 0; // Success
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