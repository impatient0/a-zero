# A-Zero :: Backtester CLI

This module provides the primary user-facing application for the A-Zero backtesting suite. It is a standalone command-line interface (CLI) that brings together the `a0-backtester` engine and the `a0-strategy-rules-engine` to run a complete trading simulation from start to finish.

The application reads a strategy definition from a YAML file, loads historical market data from a CSV file, runs the simulation, and prints a formatted performance summary to the console.

## Building the Application

The project is configured to produce a standalone, executable "uber-jar" that contains all necessary dependencies.

To build the application, navigate to the root of the `a-zero` project and run the standard Maven package command:

```bash
mvn clean package
```

This will create the executable JAR at the following path: `a0-backtester-cli/target/a0-backtester-cli-0.2.0-SNAPSHOT.jar`.

## Running a Backtest

Once built, you can run a backtest simulation using the `java -jar` command. You must provide paths to your strategy and data files, along with other required parameters.

### Example Command

```bash
java -jar a0-backtester-cli/target/a0-backtester-cli-*.jar \
  --strategy-file=./path/to/my-strategy.yaml \
  --data-file=./data/BTCUSDT-1h.csv \
  --symbol=BTCUSDT \
  --account-mode=MARGIN \
  --leverage=10 \
  --initial-capital=20000
```

### Command-Line Options

The application is configured using the following command-line flags. You can also run the application with the `--help` flag to display this information at any time.

| Short Flag | Long Flag             | Required | Description                                                                    | Default Value |
| :--------- | :-------------------- | :------- | :----------------------------------------------------------------------------- | :------------ |
| `-s`       | `--strategy-file`     | Yes      | The path to the strategy's `.yaml` configuration file.                           |               |
| `-d`       | `--data-file`         | Yes      | The path to the CSV file containing historical candle data.                      |               |
|            | `--symbol`            | Yes      | The trading symbol for the backtest (e.g., `BTCUSDT`).                           |               |
| `-a`       | `--account-mode`      | Yes      | The account mode to simulate. Valid values: `SPOT_ONLY`, `MARGIN`.             |               |
| `-c`       | `--initial-capital`   | No       | The starting capital for the simulation (in the quote currency, e.g., USDT).   | `10000.00`    |
| `-l`       | `--leverage`          | No       | The margin leverage to use (only applicable in `MARGIN` mode).                 | `5`           |

### Sample Output

A successful backtest run will produce a formatted summary similar to the following:

```
================== Backtest Results ==================
Final Portfolio Value: $10,388.22
Total P/L:             $388.22 (3.88%)
Total Trades Executed: 1
====================================================
