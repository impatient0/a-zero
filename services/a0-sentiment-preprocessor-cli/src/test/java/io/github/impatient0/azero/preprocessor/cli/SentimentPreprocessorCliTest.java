package io.github.impatient0.azero.preprocessor.cli;

import io.github.impatient0.azero.newsfeedclient.RawNewsArticle;
import io.github.impatient0.azero.sentimentprovider.Sentiment;
import io.github.impatient0.azero.sentimentprovider.SentimentProvider;
import io.github.impatient0.azero.sentimentprovider.SentimentSignal;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Sentiment Preprocessor CLI Unit Tests")
class SentimentPreprocessorCliTest {

    private SentimentPreprocessorCli cliApp;
    private SentimentProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = mock(SentimentProvider.class);
        when(mockProvider.getName()).thenReturn("MOCK_PROVIDER");
    }

    @Nested
    @DisplayName("GIVEN valid input and a functioning provider")
    class HappyPathTests {

        @Test
        @DisplayName("WHEN executed with a slow provider, THEN it should process all articles and write output CSV")
        void call_withSlowProvider_shouldSucceed(@TempDir Path tempDir) throws Exception {
            // --- ARRANGE ---
            Path inputFile = tempDir.resolve("input.csv"); // Not actually read due to override, but path needed
            Path outputFile = tempDir.resolve("output.csv");

            // 1. Prepare Mock Data
            RawNewsArticle article1 = new RawNewsArticle(1000L, "Bitcoin is up.");
            RawNewsArticle article2 = new RawNewsArticle(2000L, "Ethereum is down.");
            List<RawNewsArticle> mockArticles = List.of(article1, article2);

            // 2. Configure Mock Provider with artificial delay
            when(mockProvider.analyzeAsync(anyString(), anyLong())).thenAnswer(invocation -> {
                String text = invocation.getArgument(0);
                long timestamp = invocation.getArgument(1);

                // Artificial delay to verify concurrency logic doesn't hang
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}

                SentimentSignal signal;
                if (text.contains("Bitcoin")) {
                    signal = new SentimentSignal(timestamp, "BTCUSDT", Sentiment.BULLISH, 0.9);
                } else {
                    signal = new SentimentSignal(timestamp, "ETHUSDT", Sentiment.BEARISH, 0.8);
                }
                return CompletableFuture.completedFuture(List.of(signal));
            });

            // 3. Instantiate CLI with overrides
            cliApp = new SentimentPreprocessorCli() {
                @Override
                protected List<RawNewsArticle> loadArticles(Path path) {
                    return mockArticles;
                }

                @Override
                protected SentimentProvider loadProvider(String name) {
                    return mockProvider;
                }
            };

            // 4. Set CLI arguments via Picocli to populate private fields
            CommandLine cmd = new CommandLine(cliApp);
            cmd.parseArgs(
                "--raw-news-file", inputFile.toString(),
                "--output-file", outputFile.toString(),
                "--provider", "MOCK_PROVIDER",
                "--max-concurrency", "2"
            );

            // --- ACT ---
            int exitCode = cliApp.call();

            // --- ASSERT ---
            assertEquals(0, exitCode, "CLI should return 0 for success");
            assertTrue(Files.exists(outputFile), "Output file should be created");

            // Verify Output Content
            try (Reader reader = Files.newBufferedReader(outputFile); CSVParser csvParser =
                CSVParser.builder()
                .setReader(reader)
                .setFormat(CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())
                .get()) {

                List<CSVRecord> records = csvParser.getRecords();
                assertEquals(2, records.size());

                // Verify Article 1 (Bitcoin)
                CSVRecord record1 = records.get(0);
                assertEquals("1000", record1.get("timestamp"));
                assertEquals("BTCUSDT", record1.get("symbol"));
                assertEquals("BULLISH", record1.get("sentiment"));

                // Verify Article 2 (Ethereum)
                CSVRecord record2 = records.get(1);
                assertEquals("2000", record2.get("timestamp"));
                assertEquals("ETHUSDT", record2.get("symbol"));
                assertEquals("BEARISH", record2.get("sentiment"));
            }
        }
    }

    @Nested
    @DisplayName("GIVEN some articles fail processing")
    class PartialFailureTests {

        @Test
        @DisplayName("WHEN one article fails and another succeeds, THEN it should save the success and return exit code 1")
        void call_withPartialFailure_shouldSaveSuccessAndReportError(@TempDir Path tempDir) throws Exception {
            // --- ARRANGE ---
            Path inputFile = tempDir.resolve("input.csv");
            Path outputFile = tempDir.resolve("output.csv");

            // 1. Prepare Mock Data
            RawNewsArticle articleSuccess = new RawNewsArticle(1000L, "Bitcoin is great.");
            RawNewsArticle articleFail = new RawNewsArticle(2000L, "This will fail.");
            List<RawNewsArticle> mockArticles = List.of(articleSuccess, articleFail);

            // 2. Configure Mock Provider
            // Success case
            when(mockProvider.analyzeAsync(eq(articleSuccess.content()), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(
                    List.of(new SentimentSignal(1000L, "BTC", Sentiment.BULLISH, 0.9))
                ));

            // Failure case
            when(mockProvider.analyzeAsync(eq(articleFail.content()), anyLong()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Simulated API Failure")));

            // 3. Instantiate CLI with overrides
            cliApp = new SentimentPreprocessorCli() {
                @Override
                protected List<RawNewsArticle> loadArticles(Path path) {
                    return mockArticles;
                }

                @Override
                protected SentimentProvider loadProvider(String name) {
                    return mockProvider;
                }
            };

            // 4. Parse Arguments
            new CommandLine(cliApp).parseArgs(
                "--raw-news-file", inputFile.toString(),
                "--output-file", outputFile.toString(),
                "--provider", "MOCK_PROVIDER"
            );

            // --- ACT ---
            int exitCode = cliApp.call();

            // --- ASSERT ---
            assertEquals(1, exitCode, "CLI should return 1 to indicate partial failure");
            assertTrue(Files.exists(outputFile), "Output file should still be created");

            // Verify Output Content contains ONLY the successful record
            try (Reader reader = Files.newBufferedReader(outputFile);
                CSVParser csvParser =
                    CSVParser.builder()
                        .setReader(reader)
                        .setFormat(CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())
                        .get()) {

                List<CSVRecord> records = csvParser.getRecords();
                assertEquals(1, records.size(), "Output CSV should contain exactly one record (the successful one)");

                CSVRecord record = records.get(0);
                assertEquals("1000", record.get("timestamp"));
                assertEquals("BTC", record.get("symbol"));
            }
        }
    }

    @Nested
    @DisplayName("GIVEN execution errors")
    class ErrorHandlingTests {

        @Test
        @DisplayName("WHEN input file loading fails, THEN it should handle the exception and return exit code 1")
        void call_whenInputFileLoadFails_shouldReturnError(@TempDir Path tempDir) {
            // --- ARRANGE ---
            Path inputFile = tempDir.resolve("missing.csv");
            Path outputFile = tempDir.resolve("output.csv");

            // Instantiate CLI with an override that simulates a file read error
            cliApp = new SentimentPreprocessorCli() {
                @Override
                protected List<RawNewsArticle> loadArticles(Path path) {
                    throw new RuntimeException("Simulated IO Error: File not found");
                }
            };

            new CommandLine(cliApp).parseArgs(
                "--raw-news-file", inputFile.toString(),
                "--output-file", outputFile.toString()
            );

            // --- ACT ---
            int exitCode = cliApp.call();

            // --- ASSERT ---
            assertEquals(1, exitCode, "CLI should return non-zero exit code on file load failure");
        }

        @Test
        @DisplayName("WHEN the requested provider cannot be found, THEN it should handle the exception and return exit code 1")
        void call_whenProviderNotFound_shouldReturnError(@TempDir Path tempDir) {
            // --- ARRANGE ---
            Path inputFile = tempDir.resolve("input.csv");
            Path outputFile = tempDir.resolve("output.csv");

            // Instantiate CLI with an override that simulates ServiceLoader failure
            cliApp = new SentimentPreprocessorCli() {
                @Override
                protected List<RawNewsArticle> loadArticles(Path path) {
                    return List.of(); // Return empty list to bypass the loading check
                }

                @Override
                protected SentimentProvider loadProvider(String name) {
                    throw new IllegalArgumentException("No provider found with name: " + name);
                }
            };

            new CommandLine(cliApp).parseArgs(
                "--raw-news-file", inputFile.toString(),
                "--output-file", outputFile.toString(),
                "--provider", "MISSING_PROVIDER"
            );

            // --- ACT ---
            int exitCode = cliApp.call();

            // --- ASSERT ---
            assertEquals(1, exitCode, "CLI should return non-zero exit code on missing provider");
        }
    }
}