package io.github.impatient0.azero.ingestor;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.spot.Market;
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

    @Test
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

        // Cleanup
        Files.delete(outputFile);
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