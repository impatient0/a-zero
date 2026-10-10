# A-Zero :: Data Ingestor

A command-line tool that downloads historical K-line (candle) data from the public Binance Spot REST API and saves it to a CSV file. Its output is the input format expected by `a0-backtester-cli`.

## Build

From the project root:

```bash
mvn clean package
```

This produces a self-contained JAR at `a0-data-ingestor/target/a0-data-ingestor-<version>.jar`.

## Run

```bash
java -jar a0-data-ingestor/target/a0-data-ingestor-*.jar --symbol=BTCUSDT --timeframe=1d --start-date=2023-01-01
```

The CSV is written to the current directory as `<SYMBOL>-<timeframe>.csv` (here, `BTCUSDT-1d.csv`), with the columns `timestamp_ms,open,high,low,close,volume`.

| Argument       | Description                                                          | Example      |
| :------------- | :------------------------------------------------------------------- | :----------- |
| `--symbol`     | **(Required)** The symbol pair to fetch.                             | `BTCUSDT`    |
| `--timeframe`  | **(Required)** The K-line interval, as Binance names it (`1h`, `4h`, `1d`, …). | `1d` |
| `--start-date` | **(Required)** The inclusive start date, `YYYY-MM-DD` (UTC).          | `2023-01-01` |

No API key is needed: only public endpoints are used. Binance blocks API access from some regions, in which case the tool exits with an error.

## How it works

- Requests up to 1,000 candles at a time, starting each request just after the last candle received, and stops when a page comes back short or empty. It waits 500 ms between requests.
- Writes to `<name>.csv.tmp` and renames it to the final file only when the download completes. On failure the temporary file is deleted, so a partial download never replaces an existing file.

## Dependencies

- `binance-connector-java` for API calls.
- `picocli` for argument parsing and `--help`.
- Apache Commons CSV for writing the output.
- SLF4J with Logback for logging.
