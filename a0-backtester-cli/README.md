# A-Zero :: Backtester CLI

A command-line application that runs one backtest: it loads a strategy from a YAML file (via `a0-strategy-rules-engine`), loads candles from a CSV file, runs the `a0-backtester` engine, and prints a short summary.

## Build

From the project root:

```bash
mvn clean package
```

This produces a self-contained JAR at `a0-backtester-cli/target/a0-backtester-cli-<version>.jar`.

## Run

```bash
java -jar a0-backtester-cli/target/a0-backtester-cli-*.jar \
  --strategy-file=./path/to/my-strategy.yaml \
  --data-file=./BTCUSDT-1h.csv \
  --symbol=BTCUSDT \
  --account-mode=MARGIN \
  --leverage=10 \
  --initial-capital=20000
```

The data file uses the format written by `a0-data-ingestor`: a header row, then `timestamp_ms,open,high,low,close,volume`.

### Options

Run with `--help` to print these.

| Short Flag | Long Flag             | Required | Description                                                                    | Default Value |
| :--------- | :-------------------- | :------- | :----------------------------------------------------------------------------- | :------------ |
| `-s`       | `--strategy-file`     | Yes      | The path to the strategy's `.yaml` configuration file.                           |               |
| `-d`       | `--data-file`         | Yes      | The path to the CSV file containing historical candle data.                      |               |
|            | `--symbol`            | Yes      | The trading symbol for the backtest (e.g., `BTCUSDT`).                           |               |
| `-a`       | `--account-mode`      | Yes      | The account mode to simulate. Valid values: `SPOT_ONLY`, `MARGIN`.             |               |
| `-c`       | `--initial-capital`   | No       | The starting capital for the simulation (in the quote currency, e.g., USDT).   | `10000.00`    |
| `-l`       | `--leverage`          | No       | The margin leverage to use (only applicable in `MARGIN` mode).                 | `5`           |

The trading fee (0.1%) and slippage (0.05%) are fixed in the CLI and are not exposed as options.

### Output

The summary has this format:

```
================== Backtest Results ==================
Final Portfolio Value: $10,388.22
Total P/L:             $388.22 (3.88%)
Total Trades Executed: 1
====================================================
```
