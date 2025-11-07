# A-Zero :: Backtester Library

This module provides a high-fidelity, event-driven engine for backtesting trading strategies. It is a non-executable Java library designed to simulate strategy performance with a strong emphasis on realistic portfolio and risk management.

## Core Concepts: Account Modes

The engine operates in one of two distinct, explicit modes to ensure simulation correctness:

-   **`AccountMode.SPOT_ONLY` (Default):** Simulates a standard cash-based spot trading account.
    -   All buys are paid for in full from the cash balance.
    -   Sells require the asset to be present in the wallet.
    -   Short selling is disallowed and will result in the order being rejected.

-   **`AccountMode.MARGIN`:** Simulates a modern portfolio margin account, inspired by systems like the Bybit Unified Trading Account (UTA).
    -   Supports both leveraged LONG and SHORT positions.
    -   Manages a multi-asset wallet where assets can be owned (positive balance) or borrowed (negative balance).
    -   Uses a portfolio-level equity calculation to determine margin requirements.

## The Portfolio Margin Model (`MARGIN` Mode)

When running in `MARGIN` mode, the engine uses a sophisticated model to determine buying power and risk.

### 1. Portfolio Equity

The engine calculates `Total Equity` based on a risk-adjusted formula:
> `Equity = Sum(Value of Positive Assets * Collateral Ratio) - Sum(Value of Negative Assets)`

The **Collateral Ratios** are loaded from a mandatory `collateral-ratios.yaml` file that must be present in the `resources` directory. This file defines how much each asset contributes to the portfolio's margin collateral.

### 2. Initial Margin Rate (IMR)

Before a new position is opened, the engine calculates an `InitialMarginRate` based on the configured leverage using a conservative formula:
> `IMR = (1 / leverage) * (1.02 ^ leverage)`

A trade is only accepted if the available equity is sufficient to cover the required initial margin:
> `Available Equity (Equity - Used Margin) >= New Position Value * IMR`

### 3. Maintenance Margin & Liquidation

The engine also simulates margin calls. It calculates a `Total Maintenance Margin` (configurable, defaults to 50% of the Total Initial Margin). If at any point the `Total Equity` drops below this threshold, a **forced liquidation event** is triggered, and all open positions are closed at the current market price.

## How to Use

The engine now requires multi-asset data streams, even for a single-asset strategy.

```java
// 1. Instantiate your strategy
Strategy myStrategy = new MyTradingStrategy(parameters);

// 2. Load historical data for all assets to be traded or held.
Map<String, List<Candle>> historicalData = new HashMap<>();
historicalData.put("BTCUSDT", CsvDataLoader.load("BTCUSDT-1h.csv"));
historicalData.put("ETHUSDT", CsvDataLoader.load("ETHUSDT-1h.csv"));

// 3. Configure the backtest for MARGIN mode
BacktestConfig config = BacktestConfig.builder()
    .historicalData(historicalData)
    .initialCapital(new BigDecimal("10000.00"))
    .strategy(myStrategy)
    .accountMode(AccountMode.MARGIN) // Explicitly select MARGIN mode
    .marginLeverage(10) // Set 10x leverage
    .tradingFeePercentage(new BigDecimal("0.001"))
    .build();

// 4. Instantiate the engine and run the simulation
BacktestEngine engine = new BacktestEngine();
BacktestResult result = engine.run(config);

// 5. Analyze the results
System.out.println("Backtest Complete!");
System.out.println("Final Net Asset Value: " + result.getFinalValue());
System.out.println("Total P/L: " + result.getPnl() + " (" + result.getPnlPercent() + "%)");
```

> [!IMPORTANT]
> **A Note on Strategy State**
>
> Implementations of the `Strategy` interface are typically **stateful** (e.g., they track indicators or whether an order has been sent). The backtest engine will mutate the internal state of the provided strategy instance during a run.
>
> To ensure correct and repeatable results, **you must provide a new instance of your strategy for each call to `engine.run()`**. Reusing the same strategy instance across multiple backtests will lead to incorrect behavior.

## Future Work & Backlog

This library provides a strong foundation, but several features are planned for future development to enhance its capabilities.

-   **Multi-Quote Symbol Support**: The engine currently assumes all trading pairs are quoted in USDT. Future work will involve making the quote currency configurable to support pairs like BTC/ETH.
-   **Advanced Order Types**: The current `submitOrder` method simulates an immediate market order. The interface could be extended in the future to accept more complex order parameters (e.g., limit orders).
-   **Accurate Timestamps**: Timestamps for `Trade` and `Position` objects are currently placeholders. They will be updated to use the timestamp from the current `Candle` being processed to ensure point-in-time accuracy.