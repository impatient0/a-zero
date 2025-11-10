# A-Zero

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/impatient0/a-zero/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/impatient0/a-zero/actions)

A-Zero is a modular, data-driven, and safety-oriented framework written in Java for building, backtesting, and deploying automated crypto trading strategies.

## What is A-Zero?

This project provides the core infrastructure needed to apply a systematic and rigorous approach to algorithmic trading. It is built with an "Open Core" philosophy: the framework itself is open-source, allowing you to build your own proprietary trading strategies on a robust and well-tested foundation.

Our core guiding principles are:
*   **Safety First:** Emphasizing risk management, robust error handling, and a phased approach to deployment.
*   **Modularity:** A clean, microservice-ready architecture that separates concerns like data ingestion, signal generation, and execution.
*   **Testability:** A powerful backtesting engine is at the heart of the project, enabling data-driven validation of all strategies before they ever touch a live market.

## Core Features

*   **Command-Line Backtester:** A powerful and fast CLI tool to run your strategies against historical data and generate performance reports.
*   **Declarative Strategy Definition:** Define complex trading strategies in simple, human-readable YAML files using a rules-based approach.
*   **Realistic Simulation Engine:** A backtesting library with a portfolio margin model, trading costs (fees, slippage), and forced liquidation for margin calls.
*   **Data Ingestion Utility:** A simple CLI tool for downloading historical K-line data from exchanges like Binance.
*   **Pluggable Strategy Interface:** Define your trading logic by implementing a simple `Strategy` interface.
*   **Clean, Modern Java:** Built with modern Java and a minimal set of dependencies, ready for integration with frameworks like Spring.

## Build Status & CI

This project maintains a high standard of code quality through a Continuous Integration (CI) pipeline powered by GitHub Actions. Every push and pull request to our main development branches is automatically built and verified by running a comprehensive test suite.

For detailed logs and a history of all recent builds, please visit the [Actions tab](https://github.com/impatient0/a-zero/actions).

## Project Roadmap

This project is in its early stages. Our planned development path is:

*   [x] **v0.1:** Foundational libraries and data ingestion utility.
    *   [x] `a0-core`: Shared data models and interfaces.
    *   [x] `a0-data-ingestor`: CLI tool for downloading historical data.
    *   [x] `a0-backtester`: Core library for strategy simulation.
*   [ ] **v0.2:** Command-Line Backtester and Strategy Integration.
    *   [x] `a0-backtester`: Implement strategy loading and execution within the `BacktestEngine`.
    *   [x] `a0-strategy-rules-engine`: Build the engine to parse YAML-based strategies.
    *   [x] `a0-backtester-cli`: Build the command-line application to run backtests.
*   [ ] **v0.3:** Initial hooks and interfaces for sentiment analysis modules.
*   [ ] **v0.4:** Integration with exchange Testnet APIs for paper trading.
*   [ ] **v1.0:** A stable, production-ready framework for live spot trading.

## Getting Started

The A-Zero framework is under active development. It currently provides two primary command-line tools: a data ingestor and a backtester.

### Downloading Historical Data

1. Clone the repository: `git clone https://github.com/impatient0/a-zero.git`
2. Navigate to the project directory: `cd a-zero`
3. Build the project using Maven: `mvn clean package`
4. Run the data ingestor tool:
   ```bash
   java -jar a0-data-ingestor/target/a0-data-ingestor-0.1.0-SNAPSHOT.jar --symbol=BTCUSDT --timeframe=1h --start-date=2023-01-01
   ```

This will create a `BTCUSDT-1h.csv` file in your project root, ready for use with the backtesting engine.

### 2. Running a Backtest

Once you have data, you can run a simulation using the `backtester-cli`.

1.  **Define a Strategy:** Create a strategy configuration file (e.g., `my-strategy.yaml`). You can use the example in the [`a0-strategy-rules-engine` README](backtesting/a0-strategy-rules-engine/README.md) as a template.

2.  **Run the Backtester:** Execute the backtester JAR, pointing it to your strategy and data files.

    ```bash
    java -jar a0-backtester-cli/target/a0-backtester-cli-*.jar \
      --strategy-file=my-strategy.yaml \
      --data-file=data/BTCUSDT-1h.csv \
      --symbol=BTCUSDT \
      --account-mode=MARGIN \
      --leverage=10
    ```

    This will run the simulation and print a performance summary to your console.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.