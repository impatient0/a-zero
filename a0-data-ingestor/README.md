# A-Zero :: Data Ingestor (`a0-data-ingestor`)

## Module Overview

The `a0-data-ingestor` is a standalone command-line utility responsible for fetching historical K-line (candle) data from the public Binance Spot REST API. It is designed to download data for a specified cryptocurrency pair, timeframe, and start date, saving the results to a local, standardized CSV file. This tool serves as the primary data source for offline analysis and backtesting within the A-Zero trading system.

## How to Build

This module is part of the parent `a-zero` Maven project. To build the executable JAR, run the standard Maven `package` command from the project's root directory.

```bash
# Run from the root a-zero/ directory
mvn clean package
```

This command will compile the code and package it into a single, self-contained executable "uber-jar". The resulting artifact will be located at:
`a0-data-ingestor/target/a0-data-ingestor-0.1.0-SNAPSHOT.jar`

## How to Run

Execute the JAR file using the `java -jar` command. The application requires three arguments to be provided at runtime. The output CSV file will be created in the directory from which the command is run.

**Example Command:**

```bash
java -jar a0-data-ingestor/target/a0-data-ingestor-0.1.0-SNAPSHOT.jar --symbol=BTCUSDT --timeframe=1d --start-date=2023-01-01
```

### Command-Line Arguments

| Argument       | Description                                                                                              | Example         |
| :------------- | :------------------------------------------------------------------------------------------------------- | :-------------- |
| `--symbol`     | **(Required)** The symbol pair for which to fetch data.                                                  | `BTCUSDT`       |
| `--timeframe`  | **(Required)** The K-line interval or timeframe. Common values include `1h`, `4h`, `1d`.                  | `1d`            |
| `--start-date` | **(Required)** The inclusive start date for the data download, formatted as `YYYY-MM-DD`.                  | `2023-01-01`    |

## Configuration

This tool interacts exclusively with public Binance API endpoints, which do not require authentication. Therefore, no API keys or external configuration files are needed to run the application.

## Core Dependencies

The module relies on a few key, high-quality libraries to ensure robustness and maintainability:

*   **`binance-connector-java`**: The official Binance library is used for all API interactions. This choice minimizes the risk of implementation errors and ensures compatibility with the target API.
*   **`picocli`**: Handles all command-line argument parsing. It provides robust validation, type conversion, and automatically generates user-friendly help messages.
*   **`Apache Commons CSV`**: Used for writing the output data. This library ensures the CSV file is correctly formatted and safely handles any required data escaping.
*   **`SLF4J` & `Logback`**: Provide a standard and powerful logging framework, which is essential for monitoring the application's execution and diagnosing any issues.