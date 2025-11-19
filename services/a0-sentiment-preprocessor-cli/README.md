# A-Zero :: Sentiment Preprocessor CLI

A robust, command-line tool for enriching historical market data with AI-driven sentiment analysis.

This application reads raw news headlines from a CSV file, processes them concurrently using a pluggable sentiment provider (e.g., Google Gemini), and outputs a structured CSV containing sentiment signals.

## Features

*   **Concurrent Processing:** Uses a configurable thread pool to process multiple articles in parallel, maximizing throughput while handling I/O latency.
*   **Smart Rate Limiting:** Integrates with providers that implement internal throttling (e.g., the default Gemini provider), ensuring API limits are respected without manual tuning.
*   **Pluggable Architecture:** Uses Java's Service Provider Interface (SPI) to discover and load sentiment provider implementations dynamically. You can add your own provider by simply including its JAR on the classpath.
*   **Resilience:** Continues processing even if individual articles fail, generating a partial output and a summary report of failures.

## Prerequisites

*   Java 21 or higher.
*   Maven 3.9+ (for building).
*   A Google Gemini API Key (if using the default provider).

## Building

To build the executable "uber-jar", run the following command from the project root:

```bash
mvn clean package -pl services/a0-sentiment-preprocessor-cli -am
```

The resulting artifact will be located at:
`services/a0-sentiment-preprocessor-cli/target/a0-sentiment-preprocessor-cli-0.3.0-SNAPSHOT.jar`

## Configuration

### Environment Variables

The tool uses environment variables for sensitive credentials. The required variable depends on the chosen provider.

| Provider | Required Variable | Description |
| :--- | :--- | :--- |
| **GEMINI** (Default) | `GEMINI_API_KEY` | Your Google Gemini API Key. |
| **RANDOM** | N/A | No configuration required. |
| *Custom* | `[NAME]_API_KEY` | Custom providers should follow the convention `<NAME>_API_KEY`. |

You can also place a `.env` file in the directory where you run the JAR, and the tool will load variables from it automatically.

### Command-Line Options

| Option | Default | Description |
| :--- | :--- | :--- |
| `-i`, `--raw-news-file` | **Required** | Path to the input CSV containing raw news (`timestamp,content`). |
| `-o`, `--output-file` | **Required** | Path where the output CSV will be written. |
| `-p`, `--provider` | `GEMINI` | The name of the sentiment provider to use. Case-insensitive match against registered SPI implementations. |
| `-c`, `--max-concurrency`| `4` | The size of the thread pool. Controls how many API requests are *in flight* simultaneously. |

> **Note on Concurrency:** The `--max-concurrency` flag controls parallel connections. Throughput (requests per minute) is controlled internally by the Provider implementation to respect API limits (e.g., the Gemini provider defaults to 15 RPM). Increasing concurrency beyond the provider's internal limit will simply queue tasks efficiently.

## Usage Examples

### 1. Standard Run (Google Gemini)

```bash
export GEMINI_API_KEY="your_secret_key_here"

java -jar target/a0-sentiment-preprocessor-cli-*.jar \
  --raw-news-file data/btc-news-2023.csv \
  --output-file data/btc-sentiment-2023.csv \
  --max-concurrency 10
```

### 2. Testing Run (Random Provider)

Use the `RANDOM` provider to verify the pipeline without making network calls or using API credits.

```bash
java -jar target/a0-sentiment-preprocessor-cli-*.jar \
  --raw-news-file data/btc-news-2023.csv \
  --output-file data/test-output.csv \
  --provider RANDOM
```

## Input & Output Formats

### Input CSV
The input file must have a header row and the following columns:
```csv
timestamp,content
1672531200000,"Bitcoin hits new all-time high."
1672534800000,"Market uncertainty grows as Fed announces rate hike."
```

### Output CSV
The output file will contain the generated signals:
```csv
timestamp,symbol,sentiment,confidence
1672531200000,BTCUSDT,BULLISH,0.95
1672534800000,BTCUSDT,BEARISH,0.65
```

## Future Roadmap & Known Limitations

The following items are currently in the backlog for this tool:

*   **External Provider Configuration:** Currently, provider settings (like the Gemini model name `gemini-2.5-flash-lite` or specific rate limits) are either hardcoded or use defaults. Future versions should support loading detailed provider configuration from an external file (e.g., `providers.yaml`) passed via a CLI flag.