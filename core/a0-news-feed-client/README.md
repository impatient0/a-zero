# A-Zero :: News Feed Client

This library provides a client for reading raw news data from external sources. The initial implementation focuses on loading news articles from local CSV files.

## Core Components

### `CsvNewsClient`
A client class that reads a CSV file and parses it into a list of `RawNewsArticle` objects.

### `RawNewsArticle` Record
An immutable data record representing a single news item, containing:
- `timestamp` (long): The publication time in milliseconds since the Unix epoch.
- `content` (String): The headline or body of the news article.

## Data Format

The `CsvNewsClient` expects a UTF-8 encoded CSV file with a header row. The columns must be in the following order:
1.  `timestamp`: The Unix timestamp in milliseconds.
2.  `content`: The text of the news article.

### Example `news.csv`:
```csv
timestamp,content
1672531200000,"Bitcoin surges past $30,000 as institutional interest grows."
1672534800000,"Ethereum developers announce successful merge on testnet, price remains stable."
1672538400000,"Regulators express concerns over the volatility of the crypto market."
```

## Usage Example

```java
import io.github.impatient0.azero.newsfeedclient.CsvNewsClient;
import io.github.impatient0.azero.newsfeedclient.RawNewsArticle;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class NewsClientExample {
    public static void main(String[] args) {
        CsvNewsClient client = new CsvNewsClient();
        Path filePath = Paths.get("path/to/your/news.csv");

        try {
            List<RawNewsArticle> articles = client.loadFromFile(filePath);
            System.out.println("Successfully loaded " + articles.size() + " articles.");
            articles.forEach(System.out::println);
        } catch (IOException e) {
            System.err.println("Error reading the news file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Error parsing the news file: " + e.getMessage());
        }
    }
}
```

## Error Handling
The `CsvNewsClient` implements a **fail-fast** strategy. If any row is malformed (e.g., missing a column, unparseable timestamp), it will immediately throw an exception, aborting the entire process to prevent the ingestion of corrupt data.