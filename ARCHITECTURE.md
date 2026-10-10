# A-Zero: Architecture

## 1. Overview

A-Zero is a personal project for backtesting crypto trading strategies on historical data. It is split into small modules so that the backtesting engine does not depend on how strategies are defined or where data comes from.

### Modules
1.  `a0-core`: Shared interfaces (`Strategy`, `TradingContext`) and data models (`Candle`, `MarketEvent`, …). A lightweight library with minimal dependencies.
2.  `a0-data-ingestor`: A CLI tool that fetches and stores historical market data.
3.  `a0-backtester`: A library containing the backtesting engine for simulating `Strategy` instances. This module has no knowledge of how strategies are defined (e.g., via YAML or code).
4.  `a0-strategy-rules-engine`: A library that parses declarative strategy definitions (from YAML files) and constructs an executable `Strategy` object. This module contains all technical analysis library dependencies.
5.  `a0-backtester-cli`: A command-line tool that combines the libraries above into a backtesting application.

### Data Flow
- The `data-ingestor` produces CSV data files.
- The `strategy-rules-engine` consumes a strategy YAML file and produces a `Strategy` object.
- The `backtester-cli` reads the CSV file, uses the `strategy-rules-engine` to load a strategy, and passes the data and the `Strategy` object to the `a0-backtester` library to run the simulation.

## 2. Module Specifications

### 2.1 Module: `a0-core` (Library)
- **Responsibility:** Foundational data models and interfaces shared across the system. A non-executable library depending only on the SLF4J API (and Lombok at compile time).
- **Key Contents:**
    - **Models:** `Candle`, `Position`, `Trade`, `TradeDirection`, `AccountMode`.
    - **Events:** `MarketEvent` (a symbol plus a `Candle`).
    - **Interfaces:** `Strategy`, `TradingContext`.

### 2.2 Module: `a0-data-ingestor` (Application)
- **Documentation:** See the ingestor's [README.md](a0-data-ingestor/README.md)
- **Responsibility:** Fetch historical K-line (candle) data for a crypto spot pair from the Binance public API and save it to a local CSV file.
- **Inputs:** `--symbol` (e.g., `BTCUSDT`), `--timeframe` (e.g., `1h`), `--start-date` (e.g., `2022-01-01`).
- **Outputs:** A CSV file named `<SYMBOL>-<timeframe>.csv` in the current directory.

### 2.3 Module: `a0-backtester` (Library)
- **Documentation:** See the backtester's [README.md](a0-backtester/README.md)
- **Responsibility:** An event-driven engine that simulates a trading strategy against historical data. It manages portfolio state in `SPOT_ONLY` or `MARGIN` mode, applies fees and slippage, and liquidates positions on a margin call. This is a non-executable library.
- **Inputs:** A `BacktestConfig`: historical candles per symbol (`Map<String, List<Candle>>`), a `Strategy` instance, initial capital, account mode, leverage, maintenance margin factor, fee and slippage.
- **Outputs:** A `BacktestResult`: final net asset value, P/L, and the list of completed trades.

### 2.4 Module: `a0-strategy-rules-engine` (Library)
- **Documentation:** See the rules engine's [README.md](a0-strategy-rules-engine/README.md)
- **Responsibility:** Parse rule-based strategy definitions (from YAML files) and construct an executable `Strategy` object. It contains all logic related to technical indicators and their calculation.
- **Inputs:** A path to a configuration file (e.g., `strategy.yaml`) and the target symbol.
- **Outputs:** A `RulesBasedStrategy`, which implements the `io.github.impatient0.azero.core.strategy.Strategy` interface.

### 2.5 Module: `a0-backtester-cli` (Application)
- **Documentation:** See the backtester CLI's [README.md](a0-backtester-cli/README.md)
- **Responsibility:** A command-line interface for the backtester. It wires together the data, the backtesting engine, and the strategy rules engine to run a full simulation.
- **Inputs:** Command-line arguments specifying the strategy file, data file, symbol, account mode, initial capital and leverage.
- **Outputs:** A formatted performance summary printed to the console.

## 3. Data Contracts & Core Interfaces

### 3.1 Strategy Definition YAML (`strategy.yaml`)
```yaml
strategy_name: "RSI Oversold Entry with Fixed P/L"
direction: LONG
timeframe: "4h"

position_sizing:
  type: "Fixed_Percentage"
  percentage: 2.5

entry_rules:
  - indicator: "RSI"
    period: 14
    condition: "less_than"
    value: 30

exit_rules:
  - type: "StopLoss"
    percentage: 2.0
  - type: "TakeProfit"
    percentage: 5.0
```
Supported values: `indicator: RSI` with `less_than` / `greater_than`; exit `type: StopLoss` / `TakeProfit`; sizing `type: Fixed_Percentage`.

### 3.2 Core Java Interfaces

#### `Strategy.java`
The interface all trading strategies implement. The engine calls it once per incoming market event.
```java
public interface Strategy {
    void onMarketEvent(MarketEvent event, TradingContext context);
}
```
Implementations are expected to be stateful (e.g., keeping their own indicator history), so a new instance is needed for each backtest run.

#### `TradingContext.java`
The strategy's gateway to the trading environment. It hides whether the strategy is running in a backtest or elsewhere. The interaction follows a "fire-and-reconcile" pattern:
1.  **Fire:** The strategy states its intent by calling `submitOrder(...)`. This method returns `void` and does not guarantee execution.
2.  **Reconcile:** On a later event, the strategy calls `getOpenPosition(...)` to see the actual state of its portfolio.

```java
public interface TradingContext {
    Optional<Position> getOpenPosition(String symbol);
    Map<String, Position> getOpenPositions();
    BigDecimal getNetAssetValue();
    BigDecimal getMarginEquity();
    Map<String, BigDecimal> getWalletBalances();
    BigDecimal getAssetBalance(String asset);
    void submitOrder(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price);
}
```

## 4. Continuous Integration

The CI workflow is defined in [`.github/workflows/build.yml`](.github/workflows/build.yml).

It runs on pushes and pull requests to the `main` and `develop` branches. Each run executes `mvn -B package` on Ubuntu with JDK 21 (Temurin), which compiles all modules, runs the unit tests, and packages the JARs. The build status is shown by the badge in [`README.md`](README.md).
