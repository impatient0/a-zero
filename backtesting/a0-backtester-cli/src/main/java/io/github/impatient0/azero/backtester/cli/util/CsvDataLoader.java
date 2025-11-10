package io.github.impatient0.azero.backtester.cli.util;

import io.github.impatient0.azero.core.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for loading historical candlestick data from CSV files.
 */
@Slf4j
public final class CsvDataLoader {

    private static final String[] FILE_HEADER = {"timestamp_ms", "open", "high", "low", "close", "volume"};

    private CsvDataLoader() {
    }

    /**
     * Loads and parses a CSV file containing historical candle data.
     * <p>
     * The CSV file is expected to have a header and columns in the following order:
     * {@code timestamp_ms, open, high, low, close, volume}.
     *
     * @param filePath The path to the CSV data file.
     * @return A list of {@link Candle} objects sorted by timestamp.
     * @throws IOException           if an I/O error occurs when reading the file.
     * @throws IllegalStateException if the file is empty, has an invalid header, or a record is malformed.
     */
    public static List<Candle> load(Path filePath) throws IOException {
        log.info("Loading historical data from: {}", filePath);
        if (!Files.exists(filePath)) {
            throw new IOException("Data file not found: " + filePath);
        }

        List<Candle> candles = new ArrayList<>();
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader(FILE_HEADER)
            .setSkipHeaderRecord(true)
            .get();

        try (
            BufferedReader reader = Files.newBufferedReader(filePath);
            CSVParser parser = CSVParser.builder().setReader(reader).setFormat(csvFormat).get()
        ) {
            for (CSVRecord record : parser) {
                try {
                    Candle candle = new Candle(
                        Long.parseLong(record.get("timestamp_ms")),
                        new BigDecimal(record.get("open")),
                        new BigDecimal(record.get("high")),
                        new BigDecimal(record.get("low")),
                        new BigDecimal(record.get("close")),
                        new BigDecimal(record.get("volume"))
                    );
                    candles.add(candle);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Malformed data record found at line " + record.getRecordNumber(), e);
                }
            }
        }

        if (candles.isEmpty()) {
            throw new IllegalStateException("Data file is empty or contains no valid records: " + filePath);
        }

        log.info("Successfully loaded {} candle records.", candles.size());
        return candles;
    }
}