# "A-Zero" Crypto Trading System: Architectural Specification (v0.1)

## 1. Project Vision & Guiding Principles
- **Vision:** To build a robust, modular, and data-driven system for developing, backtesting, and executing automated crypto trading strategies.
- **Principles:**
    - **Safety First:** The system must be designed to be fail-safe and to strictly control financial risk.
    - **Systematic & Data-Driven:** All strategies must be rigorously tested on historical data before any live deployment.
    - **Iterative Complexity:** Start with simple, understandable components and add complexity incrementally and measurably.
    - **Phased Rollout:** All strategies must pass through Backtesting -> Paper Trading -> Limited Live Trading phases.

## 2. System Architecture Overview (Open Core Model)

This project follows an "Open Core" model, separating the reusable framework from proprietary trading strategies.

- **The Public Framework (`a-zero`):** This repository contains all the core modules, interfaces, and engines. Its purpose is to provide a robust, testable, and reliable foundation for any trading strategy. It is fully open-source.
- **Private Strategies (`a0-strategies`):** Proprietary strategy implementations are built in a separate, private project. This private project consumes the `a-zero` framework as a library dependency, allowing it to leverage the backtester and other tools to run its secret logic.

### MVP Architecture
- **Core Modules (Public):**
    1.  `a0-core`: Defines the essential public interfaces (`Strategy`, `TradingContext`) and data models (`Candle`). A lightweight library with minimal dependencies.
    2.  `a0-data-ingestor`: A CLI tool that fetches and stores historical market data.
    3.  `a0-backtester`: A library containing the core backtesting engine for simulating `Strategy` instances. This module has no knowledge of how strategies are defined (e.g., via YAML or code).
    4.  `a0-strategy-rules-engine`: A library that can parse declarative strategy definitions (from YAML files) and construct an executable `Strategy` object. This module contains all technical analysis library dependencies.
    5.  `a0-backtester-cli`: A command-line tool that consumes all the above libraries to provide a user-facing backtesting application.
- **Data Flow:**
    - The `data-ingestor` produces CSV data files.
    - The `strategy-rules-engine` consumes a strategy YAML file and produces a `Strategy` object.
    - The `backtester-cli` consumes the CSV files, uses the `strategy-rules-engine` to load a strategy, and then passes the data and the `Strategy` object to the `a0-backtester` library to run the simulation.

## 3. Module Specifications

### 3.1 Module: `a0-core` (Library)
- **Status:** Implemented in v0.1
- **Responsibility:** To provide the foundational data models and interfaces shared across the entire system. It is a non-executable library with near-zero dependencies to ensure stability.
- **Key Contents:**
    - **Models:** `Candle`, `Position`, `Trade`, `TradeDirection`.
    - **Interfaces:** `Strategy`, `TradingContext`.

### 3.2 Module: `a0-data-ingestor` (Application)
- **Status:** Implemented in v0.1
- **Documentation:** See Ingestor's [README.md](services/a0-data-ingestor/README.md)
- **Responsibility:** To fetch historical K-line (candle) data for a given crypto spot pair from the Binance public API and save it to a local CSV file.
- **Inputs:**
    - `symbol` (e.g., "BTCUSDT")
    - `timeframe` (e.g., "1h")
    - `start_date` (e.g., "2022-01-01")
- **Outputs:** A CSV file named `<symbol>-<timeframe>.csv`.

### 3.3 Module: `a0-backtester` (Library)
- **Status:** Implemented in v0.1
- **Documentation:** See Backtester's [README.md](backtesting/a0-backtester/README.md)
- **Responsibility:** To provide a robust, event-driven engine for simulating a trading strategy against historical data. It manages portfolio state, simulates realistic trading costs, and produces a detailed performance summary. This is a non-executable library.
- **Inputs:**
    - Path to a data CSV file (produced by `data-ingestor`).
    - Path to a strategy definition YAML file.
- **Outputs:** A performance summary printed to the console.

### 3.4 Module: `a0-strategy-rules-engine` (Library)
- **Status:** Implemented in v0.2
- **Documentation:** See Strategy Rules Engine's [README.md](backtesting/a0-strategy-rules-engine/README.md)
- **Responsibility:** To parse declarative, rule-based strategy definitions (from YAML files) and construct an executable `Strategy` object. It encapsulates all logic related to technical indicators and their calculation.
- **Inputs:** A path to a configuration file (e.g., `strategy.yaml`) and the target symbol.
- **Outputs:** An instantiated Java object that implements the `io.github.impatient0.azero.core.strategy.Strategy` interface.

### 3.5 Module: `a0-backtester-cli` (Application)
- **Status:** Implemented in v0.2
- **Documentation:** See Backtester CLI's [README.md](backtesting/a0-backtester-cli/README.md)
- **Responsibility:** To provide a user-facing command-line interface for the backtester. It wires together the data, the backtesting engine, and the strategy rules engine to run a full simulation.
- **Inputs:** Command-line arguments specifying paths to data files and strategy definition files.
- **Outputs:** A formatted performance summary printed to the console.

### 3.6 Module: `a0-sentiment-provider` (Library)
- **Status:** Implemented in v0.3
- **Responsibility:** To provide a standardized interface (`SentimentProvider`) for querying sentiment analysis services (e.g., external LLMs) and the data models (`SentimentSignal`) for representing their output.
- **Inputs:** A string of text.
- **Outputs:** A list of `SentimentSignal` objects, each containing a symbol, sentiment, and confidence score.

### 3.7 Module: `a0-news-feed-client` (Library)
- **Status:** Implemented in v0.3
- **Responsibility:** To provide clients for reading raw news data from various sources. The initial implementation (`CsvNewsClient`) handles loading news articles from structured CSV files.
- **Inputs:** A path to a data file (e.g., a CSV file).
- **Outputs:** A list of `RawNewsArticle` objects.

## 4. Data Contracts & Core Interfaces

### 4.1 Strategy Definition YAML (`strategy.yaml`)
```yaml
strategy_name: "EMA Cross with RSI Filter"

entry_rules:
  - indicator: "EMA_Cross"
    short_period: 50
    long_period: 200
    condition: "crosses_above"
  - indicator: "RSI"
    period: 14
    condition: "less_than"
    value: 50
    
exit_rules:
  - type: "StopLoss"
    percentage: 2.0
  - type: "TakeProfit"
    percentage: 4.0
```

### 4.2 Core Java Interfaces

#### `Strategy.java`
This is the core interface that all trading strategies must implement. The engine interacts with a strategy primarily through this contract.
```java
public interface Strategy {
    void onCandle(Candle candle, TradingContext context);
}
```
The strategy implementation is expected to be stateful, maintaining its own history of recent candles to perform technical analysis calculations.

#### `TradingContext.java`
This interface is the strategy's gateway to the trading environment. It abstracts away the difference between a backtest and live trading. The interaction follows a "Fire-and-Reconcile" pattern:
1.  **Fire:** The strategy states its intent by calling `submitOrder(...)`. This method returns `void` and does not block.
2.  **Reconcile:** On the next `onCandle` event, the strategy calls `getOpenPosition(...)` to see the actual state of its portfolio, thus reconciling its internal state with the "ground truth" of the execution environment.

```java
public interface TradingContext {
    Optional<Position> getOpenPosition(String symbol);
    void submitOrder(String symbol, TradeDirection direction, BigDecimal quantity, BigDecimal price);
}
```


## 5. Continuous Integration Process

The project's quality and stability are enforced by an automated Continuous Integration (CI) workflow defined in [`.github/workflows/build.yml`](.github/workflows/build.yml).

The CI process is triggered on all pushes and pull requests to the `main` and `develop` branches. On each trigger, the workflow executes the `mvn -B package` command within a clean Ubuntu environment using JDK 21. This single command serves as our primary quality gate by performing three critical actions:

1.  **Compile:** Verifies that all source code is syntactically correct and compiles successfully.
2.  **Test:** Executes the complete suite of automated tests across all project modules.
3.  **Package:** Ensures the project can be correctly packaged into its distributable JAR artifacts.

A failure in any of these stages will result in a failed workflow run, providing immediate feedback and preventing defective code from being merged into the main branches. The build status is publicly visible via the badge in the project's [`README.md`](README.md).