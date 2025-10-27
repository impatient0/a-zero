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

*   **Pluggable Strategy Interface:** Define your trading logic by implementing a simple `Strategy` interface.
*   **CLI Backtesting Engine:** A powerful and fast command-line tool to run your strategies against historical data and generate performance reports.
*   **Data Ingestion Utility:** A simple tool for downloading historical K-line data from exchanges like Binance.
*   **Clean, Modern Java:** Built with modern Java and a minimal set of dependencies, ready for integration with frameworks like Spring.

## Project Roadmap

This project is in its early stages. Our planned development path is:

*   [x] **v0.1:** Core backtesting engine and data ingestion utility.
*   [ ] **v0.2:** Integration with exchange Testnet APIs for paper trading.
*   [ ] **v0.3:** Initial hooks and interfaces for sentiment analysis modules.
*   [ ] **v1.0:** A stable, production-ready framework for live spot trading.

## Getting Started

*(This section will be filled out as we build the library. It will eventually look something like this.)*

To use A-Zero as a framework, add it as a dependency to your Maven project:

```xml
<dependency>
    <groupId>io.github.impatient0.azero</groupId>
    <artifactId>a0-backtester</artifactId>
    <version>0.1.0</version>
</dependency>
```

Then, create your own strategy by implementing the `Strategy` interface:

```java
public class MyFirstStrategy implements Strategy {
    // Your logic here
}
```

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.