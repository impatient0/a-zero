package io.github.impatient0.azero.newsfeedclient;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A client for loading news articles from a CSV file.
 * <p>
 * This class provides a method to parse a specifically formatted CSV file
 * containing historical news data. The expected format is a file with a header
 * row and two columns: {@code timestamp,content}.
 */
@Slf4j
public class CsvNewsClient {

    private static final String[] CSV_HEADERS = {"timestamp", "content"};

    /**
     * Loads a list of {@link RawNewsArticle} objects from the specified CSV file.
     * <p>
     * The method enforces a strict "fail-fast" policy. If any row in the CSV
     * is malformed (e.g., incorrect column count, invalid timestamp format), an
     * exception is thrown, and the entire loading process is aborted. This ensures
     * that no partial or potentially corrupt data is returned.
     *
     * @param filePath The {@link Path} to the CSV file to be loaded.
     * @return An immutable {@link List} of {@link RawNewsArticle}s.
     * @throws IOException              if an I/O error occurs while reading the file.
     * @throws IllegalArgumentException if the CSV file contains a malformed record.
     */
    public List<RawNewsArticle> loadFromFile(Path filePath) throws IOException {
        log.info("Loading news articles from CSV file: {}", filePath);

        final CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader(CSV_HEADERS)
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

        final List<RawNewsArticle> articles = new ArrayList<>();

        try (
            BufferedReader reader = Files.newBufferedReader(filePath);
            CSVParser csvParser = CSVParser.builder().setReader(reader).setFormat(format).get()
        ) {
            for (CSVRecord csvRecord : csvParser) {
                try {
                    if (!csvRecord.isConsistent()) {
                        throw new IllegalArgumentException(String.format(
                            "CSV record #%d is inconsistent. Expected %d columns, but found %d.",
                            csvRecord.getRecordNumber(), CSV_HEADERS.length, csvRecord.size()));
                    }

                    String timestampStr = csvRecord.get("timestamp");
                    long timestamp = Long.parseLong(timestampStr);

                    String headline = csvRecord.get("content");
                    if (headline == null || headline.isBlank()) {
                        throw new IllegalArgumentException(String.format(
                            "CSV record #%d has a blank or missing content.", csvRecord.getRecordNumber()));
                    }

                    articles.add(new RawNewsArticle(timestamp, headline));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(String.format(
                        "Failed to parse timestamp on CSV record #%d. Value: '%s'. Expected a valid long.",
                        csvRecord.getRecordNumber(), csvRecord.get("timestamp")), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read news file: {}", filePath, e);
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse CSV file due to malformed record.", e);
            throw e;
        }

        log.info("Successfully loaded {} news articles from {}", articles.size(), filePath);
        return List.copyOf(articles);
    }
}