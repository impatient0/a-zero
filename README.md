# A-Zero

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/impatient0/a-zero/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/impatient0/a-zero/actions)

A-Zero is a set of Java 21 modules for backtesting crypto trading strategies on historical candle data: a Binance data downloader, a backtesting engine that simulates spot and margin accounts, and a small YAML format for rule-based strategies. It is a personal learning project. It is early-stage and unfinished: the path from downloaded data to a backtest result works end to end, but the strategy language is minimal and development is currently paused.

## What's interesting here

- **Margin accounting on a signed multi-asset wallet.** In `MARGIN` mode the account is a map of asset balances that can go negative: opening a long adds BTC and pushes USDT below zero (borrowed), a short does the opposite. Margin equity is `Σ(positive balances × collateral ratio) − Σ(negative balances)`, with per-asset ratios loaded from [`collateral-ratios.yaml`](a0-backtester/src/main/resources/collateral-ratios.yaml) (BTC 0.95, JASMY 0.5, …). Net asset value, which is what P/L is measured against, is computed separately without the haircut. The model is loosely based on Bybit's Unified Trading Account.
- **Pre-trade margin checks and forced liquidation.** An order is rejected if equity minus the margin already in use can't cover `position value × IMR`, where `IMR = (1 / leverage) × 1.02^leverage` (so 10x needs 12.2%, not 10%). Before the strategy sees each timestamp, the engine compares equity with the maintenance margin (50% of initial margin by default); if equity is below it, every position is closed through the normal close path, so fees, wallet balances and trade records stay consistent.
- **A time-merged, multi-symbol event loop.** Candle series for several symbols are merged into one `TreeMap` keyed by timestamp. At each step the engine forward-fills the last known price of every symbol, so the portfolio can be valued even when series have gaps or start at different times.
- **A typed YAML strategy loader.** Strategy files are deserialized into records behind sealed interfaces using Jackson's polymorphic type ids (`indicator: RSI`, `type: StopLoss`), then turned into runtime objects with pattern-matching `switch`es. An unknown rule type fails at load time with a message listing the known ones.

Money arithmetic uses `BigDecimal` throughout. `mvn clean package` runs 70 unit tests.

## Modules

| Module | Kind | What it does |
|---|---|---|
| [`a0-core`](a0-core) | library | Shared records (`Candle`, `Position`, `Trade`, `MarketEvent`) and the `Strategy` / `TradingContext` interfaces. |
| [`a0-data-ingestor`](a0-data-ingestor) | CLI | Downloads Binance spot K-lines to a CSV file. |
| [`a0-backtester`](a0-backtester) | library | The simulation engine: `SPOT_ONLY` and `MARGIN` accounts, fees, slippage, liquidation. |
| [`a0-strategy-rules-engine`](a0-strategy-rules-engine) | library | Builds a `Strategy` from a YAML file. Currently one of each: an RSI entry rule, stop-loss and take-profit exits, fixed-percentage position sizing. |
| [`a0-backtester-cli`](a0-backtester-cli) | CLI | Runs one strategy against one CSV file and prints the final value, P/L and number of trades. |

## Build and run

Requires JDK 21 and Maven.

```bash
mvn clean package
```

Download hourly BTC/USDT candles. This writes `BTCUSDT-1h.csv` to the current directory. (Binance's public API is not reachable from some regions.)

```bash
java -jar a0-data-ingestor/target/a0-data-ingestor-*.jar \
  --symbol=BTCUSDT --timeframe=1h --start-date=2023-01-01
```

Save a strategy as `my-strategy.yaml` (the example in the [rules engine README](a0-strategy-rules-engine/README.md) works as-is) and run a backtest:

```bash
java -jar a0-backtester-cli/target/a0-backtester-cli-*.jar \
  --strategy-file=my-strategy.yaml \
  --data-file=BTCUSDT-1h.csv \
  --symbol=BTCUSDT \
  --account-mode=MARGIN \
  --leverage=10
```

## Known limitations

- Only USDT-quoted pairs are supported.
- The engine accepts several symbols, but the CLI loads one.
- The CLI uses a fixed 0.1% fee and 0.05% slippage.
- Trade and position timestamps come from the wall clock at simulation time, not from the candle.
- Position size is a percentage of NAV, so `--leverage` changes the margin requirement but not the position size.
- The result is a three-line summary: no equity curve, drawdown or per-trade report.

## Unfinished work

The [`feat/sentiment-analysis`](https://github.com/impatient0/a-zero/tree/feat/sentiment-analysis) branch (not merged; moves the build to Java 25) adds an LLM-based sentiment pipeline:

- a `SentimentProvider` interface discovered through `ServiceLoader`, with a Gemini implementation (schema-constrained JSON output, client-side rate limiting) and a random one for tests;
- a CLI that sends news headlines from a CSV file to a provider on a thread pool and writes timestamped sentiment signals to another CSV;
- engine support for loading those signals, forward-filling them like prices, and exposing them to strategies via `getCurrentSentiment(symbol)`.

What's missing is a sentiment rule in the YAML format, so strategies can't act on the signals yet.

## Ideas I didn't get to

Not a plan, just the obvious next steps:

- More indicators (e.g. moving-average crossover) and exit rules (trailing stop).
- Multi-symbol backtests from the CLI.
- A fuller report: equity curve, maximum drawdown, win rate.
- Paper trading against an exchange testnet.

## License

Apache License 2.0. See [LICENSE](LICENSE).
