# A-Zero :: Backtester Library

This module provides the core engine for running event-driven backtest simulations of trading strategies. It is a non-executable Java library designed to be consumed by other modules, such as a command-line interface or a graphical user interface.

## Core Components

The library's primary entry point is the `BacktestEngine`, which orchestrates the simulation. It is configured via a `BacktestConfig` object and returns a `BacktestResult` object.

-   **`BacktestEngine`**: The main class that runs the simulation. Its `run()` method takes a configuration and executes the backtest loop, returning the results.
-   **`BacktestConfig`**: A flexible builder-style configuration object. It requires `initialCapital` and `historicalData` (`List<Candle>`) and allows for optional, realistic cost parameters:
    -   `tradingFeePercentage`: The fee per trade (e.g., `0.001` for 0.1%).
    -   `slippagePercentage`: The simulated price slippage per trade (e.g., `0.0005` for 0.05%).
-   **`BacktestResult`**: An immutable data object containing the summary of the backtest performance, including final portfolio value, total P/L (absolute and percentage), and a complete list of executed trades.

## Key Features

The engine is designed with the "safety and correctness" principle in mind, providing a robust and realistic simulation environment.

-   **Realistic Cost Simulation**: The engine applies both slippage and trading fees to every transaction, providing a more accurate picture of a strategy's real-world performance.
-   **Flexible Position Management**: Strategies can scale positions in and out. The engine correctly handles:
    -   **Scaling In**: Increasing a position's size, with automatic calculation of the new volume-weighted average price (VWAP).
    -   **Scaling Out**: Decreasing a position's size (partial profit-taking), which correctly generates a `Trade` record for the realized portion.
-   **Accurate Mark-to-Market Valuation**: If a position remains open at the end of a simulation, its value is calculated based on the last available price, including the simulated costs of liquidation (fees and slippage).

## How to Use

The following is a conceptual example of how to use the library:

```java
// 1. Load historical data (e.g., from a CSV file)
List<Candle> candles = CsvDataLoader.load("BTCUSDT-1h.csv");

// 2. Configure the backtest
BacktestConfig config = BacktestConfig.builder()
    .historicalData(candles)
    .initialCapital(new BigDecimal("10000.00"))
    .strategyDefinition(new MyStrategyParameters()) // Placeholder
    .tradingFeePercentage(new BigDecimal("0.001")) // 0.1% fee
    .slippagePercentage(new BigDecimal("0.0005")) // 0.05% slippage
    .build();

// 3. Instantiate the engine and run the simulation
BacktestEngine engine = new BacktestEngine();
BacktestResult result = engine.run(config);

// 4. Analyze the results
System.out.println("Backtest Complete!");
System.out.println("Final Portfolio Value: " + result.getFinalValue());
System.out.println("Total P/L: " + result.getPnl() + " (" + result.getPnlPercent() + "%)");
System.out.println("Total Trades: " + result.getTotalTrades());
```

## Future Work & Backlog

This library provides a strong foundation, but several features are planned for future development to enhance its capabilities.

-   **Strategy Loading & Execution**: The current engine contains the simulation loop, but the logic for instantiating and calling a `Strategy` implementation on each candle is the immediate next step.
-   **Multi-Asset Portfolio Support**: The current portfolio management logic is simplified to handle one asset at a time. The `TradingContext` interface is designed for multi-asset support, but the implementation will be evolved to manage a `Map<String, Position>` to allow strategies to trade multiple symbols concurrently within a single portfolio.
-   **Advanced Order Types**: The current `submitOrder` method simulates an immediate market order. The interface could be extended in the future to accept more complex order parameters (e.g., limit orders, time-in-force policies) to allow for more sophisticated strategy execution models.
-   **Accurate Timestamps**: Timestamps for `Trade` and `Position` objects are currently placeholders. They will be updated to use the timestamp from the current `Candle` being processed to ensure point-in-time accuracy.
