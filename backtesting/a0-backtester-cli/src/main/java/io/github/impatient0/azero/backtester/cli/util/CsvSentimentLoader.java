package io.github.impatient0.azero.backtester.cli.util;

import io.github.impatient0.azero.core.model.Sentiment;
import io.github.impatient0.azero.core.model.SentimentSignal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility for loading sentiment data from CSV files.
 */
@Slf4j
public class CsvSentimentLoader {

    private static final String[] HEADERS = {"timestamp", "symbol", "sentiment", "confidence"};

    /**
     * Loads sentiment signals from a CSV file.
     *
     * @param path The path to the CSV file.
     * @return A Map where the key is the symbol and the value is a list of signals.
     */
    public static Map<String, List<SentimentSignal>> load(Path path) {
        if (path == null) {
            return Collections.emptyMap();
        }

        log.info("Loading sentiment data from: {}", path);
        List<SentimentSignal> allSignals = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader(HEADERS)
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

        try (BufferedReader reader = Files.newBufferedReader(path);
            CSVParser parser = CSVParser.builder().setReader(reader).setFormat(format).get()) {

            for (CSVRecord record : parser) {
                try {
                    long timestamp = Long.parseLong(record.get("timestamp"));
                    String symbol = record.get("symbol");
                    Sentiment sentiment = Sentiment.valueOf(record.get("sentiment"));
                    double confidence = Double.parseDouble(record.get("confidence"));

                    allSignals.add(new SentimentSignal(timestamp, symbol, sentiment, confidence));
                } catch (Exception e) {
                    log.warn("Skipping malformed sentiment record at line {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Failed to read sentiment file: {}", path, e);
            throw new RuntimeException("Failed to load sentiment data", e);
        }

        log.info("Loaded {} sentiment signals.", allSignals.size());

        // Group by symbol
        return allSignals.stream()
            .collect(Collectors.groupingBy(SentimentSignal::symbol));
    }
}