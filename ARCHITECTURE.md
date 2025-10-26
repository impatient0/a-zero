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
    1.  `data-ingestor`: Fetches and stores historical market data.
    2.  `backtester`: Runs trading strategies against historical data to evaluate performance.
    3.  `core`: Defines the essential public interfaces (e.g., `Strategy`) and data models (e.g., `Candle`).
- **Communication:**
    - The `data-ingestor` produces CSV files.
    - The `backtester` consumes CSV files and Strategy Definition files (YAML), and executes logic defined in classes that implement the `Strategy` interface.

## 3. Module Specifications

### 3.1 Module: `data-ingestor`
- **Status:** Implemented in v0.1
- **Documentation:** See Ingestor's [README.md](a0-data-ingestor/README.md)
- **Responsibility:** To fetch historical K-line (candle) data for a given crypto spot pair from the Binance public API and save it to a local CSV file.
- **Inputs:**
    - `symbol` (e.g., "BTCUSDT")
    - `timeframe` (e.g., "1h")
    - `start_date` (e.g., "2022-01-01")
- **Outputs:** A CSV file named `<symbol>-<timeframe>.csv`.
- **Data Contract:** The output CSV must have the following header and columns:
  `timestamp_ms,open,high,low,close,volume`

### 3.2 Module: `backtester`
- **Responsibility:** To simulate a trading strategy against a set of historical data and report on its performance.
- **Inputs:**
    - Path to a data CSV file (produced by `data-ingestor`).
    - Path to a strategy definition YAML file.
- **Outputs:** A performance summary printed to the console.
- **Data Contract:** The strategy YAML format is defined in the section below.

## 4. Data Contracts

### 4.1 Strategy Definition YAML (`strategy.yaml`)
```yaml
strategy_name: "Unique name for this strategy run"
asset: "BTC/USDT"
timeframe: "1h"

# Defines the rules for entering a position
entry_rules:
  # ... (To be defined in the next step)

# Defines the rules for exiting a position
exit_rules:
  type: "Fixed_Risk_Reward"
  risk_reward_ratio: 1.5
  stop_loss_percentage: 2.0

# Defines the costs associated with trading
simulation_parameters:
  trading_fee_percentage: 0.1
  slippage_percentage: 0.05