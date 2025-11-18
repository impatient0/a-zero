package io.github.impatient0.azero.preprocessor.cli;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sentiment Preprocessor CLI Integration Tests")
class SentimentPreprocessorCliIT {

    @Nested
    @DisplayName("GIVEN a valid environment and inputs")
    class SuccessScenarios {

        @Test
        @DisplayName("WHEN using the RANDOM provider, THEN it should successfully process files via SPI")
        void execute_withRandomProvider_shouldSucceed(@TempDir Path tempDir) throws IOException {
            // --- ARRANGE ---
            Path inputFile = tempDir.resolve("news_input.csv");
            Path outputFile = tempDir.resolve("sentiment_output.csv");

            // 1. Create a real input CSV file
            createInputCsv(inputFile, List.of(
                new String[]{"1670000000000", "Bitcoin is surging today."},
                new String[]{"1670000060000", "Ethereum faces resistance."}
            ));

            // 2. Prepare arguments
            // We use "RANDOM" to trigger the ServiceLoader to find RandomSentimentProvider
            String[] args = {
                "--raw-news-file", inputFile.toString(),
                "--output-file", outputFile.toString(),
                "--provider", "RANDOM",
                "--max-concurrency", "2"
            };

            // --- ACT ---
            // We execute via CommandLine to simulate the main entry point without System.exit()
            int exitCode = new CommandLine(new SentimentPreprocessorCli()).execute(args);

            // --- ASSERT ---
            assertEquals(0, exitCode, "Exit code should be 0 (Success)");
            assertTrue(Files.exists(outputFile), "Output file should be created");

            // 3. Verify the content of the output file
            try (Reader reader = Files.newBufferedReader(outputFile);
                CSVParser parser =
                    CSVParser.builder()
                        .setReader(reader)
                        .setFormat(CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()).get()) {

                List<CSVRecord> records = parser.getRecords();
                assertEquals(2, records.size(), "Should have 2 output records");

                CSVRecord record1 = records.get(0);
                assertEquals("1670000000000", record1.get("timestamp"), "Timestamp should be preserved");
                assertNotNull(record1.get("symbol"), "Symbol should be generated");
                assertNotNull(record1.get("sentiment"), "Sentiment should be generated");

                CSVRecord record2 = records.get(1);
                assertEquals("1670000060000", record2.get("timestamp"), "Timestamp should be preserved");
            }
        }
    }

    @Nested
    @DisplayName("GIVEN invalid configurations")
    class FailureScenarios {

        @Test
        @DisplayName("WHEN a non-existent provider is specified, THEN it should fail gracefully")
        void execute_withMissingProvider_shouldFail(@TempDir Path tempDir) throws IOException {
            // --- ARRANGE ---
            Path inputFile = tempDir.resolve("news_input.csv");
            Path outputFile = tempDir.resolve("sentiment_output.csv");

            createInputCsv(inputFile, List.<String[]>of(new String[]{"123", "Test"}));

            String[] args = {
                "--raw-news-file", inputFile.toString(),
                "--output-file", outputFile.toString(),
                "--provider", "NON_EXISTENT_PROVIDER"
            };

            // --- ACT ---
            int exitCode = new CommandLine(new SentimentPreprocessorCli()).execute(args);

            // --- ASSERT ---
            assertEquals(1, exitCode, "Exit code should be 1 (Error) for missing provider");
            assertFalse(Files.exists(outputFile), "Output file should NOT be created");
        }
    }

    // Helper to write a simple input CSV
    private void createInputCsv(Path path, List<String[]> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path);
            CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader("timestamp", "content").build())) {
            for (String[] row : rows) {
                printer.printRecord((Object[]) row);
            }
        }
    }
}