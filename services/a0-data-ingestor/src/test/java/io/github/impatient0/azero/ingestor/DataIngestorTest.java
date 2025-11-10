package io.github.impatient0.azero.ingestor;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.spot.Market;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataIngestorTest {

    @Mock
    private SpotClient spotClient;

    @Mock
    private Market market;

    private DataIngestor dataIngestor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanup() throws IOException {
        Path path = Path.of("BTCUSDT-1h.csv");
        if (Files.isDirectory(path)) {
            // Simple directory deletion; for more complex cases, a recursive delete would be needed.
            Files.deleteIfExists(path);
        } else {
            Files.deleteIfExists(path);
        }
    }

    @Nested
    @DisplayName("GIVEN DataIngestor is configured to fetch Klines data")
    class KlinesIngestionTests {

        @Test
        @DisplayName("WHEN the API returns paginated data (full page then partial), THEN it should fetch all data and write the complete CSV file.")
        void call_shouldFetchAndWritePaginatedData() throws Exception {
            // --- ARRANGE ---
            dataIngestor = new DataIngestor(spotClient, new ObjectMapper());
            injectField(dataIngestor, "symbol", "BTCUSDT");
            injectField(dataIngestor, "timeframe", "1h");
            injectField(dataIngestor, "startDateStr", "2023-01-01");

            when(spotClient.createMarket()).thenReturn(market);

            String firstPageResponse = generateFakeKlinesResponse(1000, 1672531200000L);
            String secondPageResponse = "[ [1676131200000, \"110\", \"120\", \"100\", \"115\", \"1500\"] ]";
            String emptyResponse = "[]";

            when(market.klines(any(LinkedHashMap.class)))
                .thenReturn(firstPageResponse)  // First call returns a full page
                .thenReturn(secondPageResponse) // Second call returns a partial page
                .thenReturn(emptyResponse);     // Subsequent calls return nothing

            // --- ACT ---
            Integer exitCode = dataIngestor.call();

            // --- ASSERT ---
            assertEquals(0, exitCode, "The command should exit with code 0 for success.");

            Path outputFile = Path.of("BTCUSDT-1h.csv");
            assertTrue(Files.exists(outputFile), "Output CSV file should be created.");

            List<String> lines = Files.readAllLines(outputFile);
            assertEquals(1002, lines.size(), "CSV should contain a header and 1001 data rows.");
            assertEquals("timestamp_ms,open,high,low,close,volume", lines.get(0));
            assertEquals("1672531200000,100,110,90,105,1000", lines.get(1)); // Check first record
            assertEquals("1676131200000,110,120,100,115,1500", lines.get(1001)); // Check last record
        }


        @Test
        @DisplayName("WHEN the API call throws an exception, THEN it should return a failure code and not create the output file.")
        void call_whenApiThrowsException_shouldLogErrorAndReturnFailureCode() throws Exception {
            // --- ARRANGE ---
            dataIngestor = new DataIngestor(spotClient, objectMapper);
            injectField(dataIngestor, "symbol", "BTCUSDT");
            injectField(dataIngestor, "timeframe", "1h");
            injectField(dataIngestor, "startDateStr", "2023-01-01");

            // Simulate the API call throwing an exception (e.g., network timeout, 500 error)
            when(spotClient.createMarket()).thenReturn(market);
            when(market.klines(any(LinkedHashMap.class)))
                .thenThrow(new RuntimeException("Simulated API Error: Binance API is down"));

            // --- ACT ---
            Integer exitCode = dataIngestor.call();

            // --- ASSERT ---
            assertEquals(1, exitCode, "The command should exit with code 1 for failure.");

            Path outputFile = Path.of("BTCUSDT-1h.csv");
            assertFalse(Files.exists(outputFile), "Output CSV file should not be created on API failure.");
        }


        @Test
        @DisplayName("WHEN the output file cannot be written due to an I/O error, THEN it should return a failure code.")
        void call_whenFileCannotBeWritten_shouldLogErrorAndReturnFailureCode() throws Exception {
            // --- ARRANGE ---
            dataIngestor = new DataIngestor(spotClient, objectMapper);
            injectField(dataIngestor, "symbol", "BTCUSDT");
            injectField(dataIngestor, "timeframe", "1h");
            injectField(dataIngestor, "startDateStr", "2023-01-01");

            // Create a directory with the same name as the target file.
            // This will cause 'new FileWriter(...)' to throw an IOException.
            Path blockingDirectory = Path.of("BTCUSDT-1h.csv");
            Files.createDirectory(blockingDirectory);

            // --- ACT ---
            Integer exitCode = dataIngestor.call();

            // --- ASSERT ---
            assertEquals(1, exitCode, "The command should exit with code 1 for I/O failure.");
        }

        @Test
        @DisplayName("WHEN the API returns no data initially, THEN it should create a CSV file with only the header and succeed.")
        void call_whenApiReturnsNoDataInitially_shouldCreateEmptyFileAndSucceed() throws Exception {
            // --- ARRANGE ---
            dataIngestor = new DataIngestor(spotClient, objectMapper);
            injectField(dataIngestor, "symbol", "BTCUSDT");
            injectField(dataIngestor, "timeframe", "1h");
            injectField(dataIngestor, "startDateStr", "2023-01-01");

            // Simulate the API returning an empty list on the first and only call
            String emptyResponse = "[]";
            when(spotClient.createMarket()).thenReturn(market);
            when(market.klines(any(LinkedHashMap.class))).thenReturn(emptyResponse);

            // --- ACT ---
            Integer exitCode = dataIngestor.call();

            // --- ASSERT ---
            assertEquals(0, exitCode, "The command should exit with code 0 for success even with no data.");

            Path outputFile = Path.of("BTCUSDT-1h.csv");
            assertTrue(Files.exists(outputFile), "Output CSV file should still be created.");

            List<String> lines = Files.readAllLines(outputFile);
            assertEquals(1, lines.size(), "CSV should contain only the header row.");
            assertEquals("timestamp_ms,open,high,low,close,volume", lines.get(0));
        }

        @Test
        @DisplayName("WHEN the API call throws an exception, THEN the temporary file should be cleaned up.")
        void call_whenApiThrowsException_shouldCleanupTemporaryFile() throws Exception {
            // --- ARRANGE ---
            dataIngestor = new DataIngestor(spotClient, objectMapper);
            injectField(dataIngestor, "symbol", "BTCUSDT");
            injectField(dataIngestor, "timeframe", "1h");
            injectField(dataIngestor, "startDateStr", "2023-01-01");

            Path tempFilePath = Paths.get("BTCUSDT-1h.csv.tmp");

            // Arrange the mock to throw an exception after the temp file has been created.
            when(spotClient.createMarket()).thenReturn(market);
            when(market.klines(any(LinkedHashMap.class)))
                .thenThrow(new RuntimeException("Simulated API Error"));

            // --- ACT ---
            dataIngestor.call();

            // --- ASSERT ---
            assertFalse(Files.exists(tempFilePath), "The temporary file should be deleted on failure.");
        }
    }

    private void injectField(Object target, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String generateFakeKlinesResponse(int count, long startTimestamp) {
        return IntStream.range(0, count)
            .mapToObj(i -> String.format("[ %d, \"100\", \"110\", \"90\", \"105\", \"1000\" ]", startTimestamp + (i * 3600000L)))
            .collect(Collectors.joining(", ", "[", "]"));
    }
}