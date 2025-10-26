package io.github.impatient0.azero.ingestor;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.SpotClientImpl;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "data-ingestor",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Fetches historical K-line data from Binance and saves it to a CSV file.")
@Slf4j
public class DataIngestor implements Callable<Integer> {

    private static final int BINANCE_API_LIMIT = 1000;
    private static final long API_CALL_DELAY_MS = 500; // Respectful delay to avoid rate limiting
    private static final String[] CSV_HEADER = {"timestamp_ms", "open", "high", "low", "close", "volume"};

    @Option(names = "--symbol", required = true, description = "The crypto symbol pair (e.g., BTCUSDT).")
    private String symbol;

    @Option(names = "--timeframe", required = true, description = "The candle timeframe (e.g., 1h, 4h, 1d).")
    private String timeframe;

    @Option(names = "--start-date", required = true, description = "The start date in YYYY-MM-DD format.")
    private String startDateStr;

    private final SpotClient spotClient;
    private final ObjectMapper objectMapper;

    public DataIngestor() {
        this(new SpotClientImpl(), new ObjectMapper());
    }

    DataIngestor(SpotClient spotClient, ObjectMapper objectMapper) {
        this.spotClient = spotClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() throws Exception {
        log.info("Starting data ingestion process for symbol={}, timeframe={}, startDate={}",
            symbol, timeframe, startDateStr);

        long startTimeMs;
        try {
            startTimeMs = LocalDate.parse(startDateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        } catch (Exception e) {
            log.error("Invalid date format for --start-date. Please use YYYY-MM-DD.", e);
            return 1; // Non-zero exit code indicates failure
        }

        String outputFilename = String.format("%s-%s.csv", symbol.toUpperCase(), timeframe);
        int totalRecordsWritten = 0;

        try (
            FileWriter out = new FileWriter(outputFilename);
            CSVPrinter csvPrinter = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(CSV_HEADER))
        ) {
            log.info("Output will be written to {}", outputFilename);

            while (true) {
                log.info("Fetching up to {} records starting from timestamp {}", BINANCE_API_LIMIT, startTimeMs);

                List<List<Object>> klines;
                try {
                    klines = fetchKlines(startTimeMs);
                } catch (Exception e) {
                    log.error("An error occurred while fetching data from Binance API. Halting process.", e);
                    return 1;
                }

                if (klines.isEmpty()) {
                    log.info("No more data returned from API. Ingestion complete.");
                    break;
                }

                for (List<Object> kline : klines) {
                    // Data Contract: [timestamp_ms, open, high, low, close, volume, ...]
                    // We only need the first 6 elements.
                    csvPrinter.printRecord(
                        kline.get(0), // timestamp_ms
                        kline.get(1), // open
                        kline.get(2), // high
                        kline.get(3), // low
                        kline.get(4), // close
                        kline.get(5)  // volume
                    );
                    totalRecordsWritten++;
                }

                // Prepare for the next API call
                // The next start time is the timestamp of the last record + 1 millisecond
                long lastTimestamp = (long) klines.get(klines.size() - 1).get(0);
                startTimeMs = lastTimestamp + 1;

                // If we received fewer records than the limit, we've reached the end of the available data.
                if (klines.size() < BINANCE_API_LIMIT) {
                    log.info("Received fewer records than the limit ({} < {}). Assuming all data has been fetched.", klines.size(), BINANCE_API_LIMIT);
                    break;
                }

                // Defensive delay between API calls
                try {
                    Thread.sleep(API_CALL_DELAY_MS);
                } catch (InterruptedException e) {
                    log.warn("API call delay was interrupted. Continuing...", e);
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
            }

            log.info("Successfully completed data ingestion.");
            log.info("Total records written: {}", totalRecordsWritten);
            log.info("Output file: {}", outputFilename);
            return 0; // Success

        } catch (IOException e) {
            log.error("Failed to write to CSV file: {}", outputFilename, e);
            return 1;
        }
    }

    private List<List<Object>> fetchKlines(long startTimeMs) throws IOException {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol.toUpperCase());
        params.put("interval", timeframe);
        params.put("startTime", startTimeMs);
        params.put("limit", BINANCE_API_LIMIT);

        // The official library returns a raw JSON string which must be parsed.
        String jsonResponse = spotClient.createMarket().klines(params);

        // The response is a JSON array of arrays, e.g., [[t, o, h, l, c, v], [...]]
        // We use Jackson to parse it into a List of Lists.
        return objectMapper.readValue(jsonResponse, new TypeReference<List<List<Object>>>() {});
    }

    public static void main(String[] args) {
        // picocli's CommandLine will parse args, handle errors, and call the `call()` method.
        int exitCode = new CommandLine(new DataIngestor()).execute(args);
        System.exit(exitCode);
    }
}