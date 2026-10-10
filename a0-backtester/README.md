# A-Zero :: Backtester Library

An event-driven engine for backtesting trading strategies. It is a non-executable Java library: you give it candle data and a `Strategy`, and it simulates the account and returns the result. Most of the code is about modelling the account: spot vs. margin, fees, slippage, margin requirements and liquidation.

## Account Modes

The engine runs in one of two explicit modes:

-   **`AccountMode.SPOT_ONLY` (default):** A cash-based spot account.
    -   Buys are paid for in full from the USDT balance.
    -   Sells require the asset to be held.
    -   Short selling is not allowed: a sell with no position open is logged and ignored.

-   **`AccountMode.MARGIN`:** A portfolio margin account, loosely modelled on the Bybit Unified Trading Account.
    -   Supports leveraged LONG and SHORT positions.
    -   Keeps a multi-asset wallet where balances can be positive (owned) or negative (borrowed).
    -   Uses portfolio-level equity to decide whether new positions are allowed.

## The Margin Model (`MARGIN` Mode)

### 1. Portfolio Equity

Equity for margin purposes is a risk-adjusted sum over the wallet:
> `Equity = Sum(Value of Positive Assets * Collateral Ratio) - Sum(Value of Negative Assets)`

The collateral ratios are loaded from [`collateral-ratios.yaml`](src/main/resources/collateral-ratios.yaml) on the classpath; the engine fails to start in `MARGIN` mode without it. An asset missing from the file contributes nothing to equity. Final P/L is measured on net asset value instead, which uses no collateral ratios.

### 2. Initial Margin Rate (IMR)

Before a position is opened or increased, the engine computes an initial margin rate from the configured leverage:
> `IMR = (1 / leverage) * (1.02 ^ leverage)`

The order is accepted only if:
> `Equity - Margin Already Used >= New Position Value * IMR`

### 3. Maintenance Margin & Liquidation

The total maintenance margin is the total initial margin multiplied by `maintenanceMarginFactor` (default 0.5). At each timestamp, before the strategy is called, the engine checks equity against it. If equity is lower, all open positions are closed at the current price.

## How to Use

```java
// 1. Create a new strategy instance (strategies are stateful; use a fresh one per run)
Strategy myStrategy = new MyTradingStrategy(parameters);

// 2. Provide candles per symbol, in any order (the engine sorts by timestamp)
Map<String, List<Candle>> historicalData = new HashMap<>();
historicalData.put("BTCUSDT", btcCandles);
historicalData.put("ETHUSDT", ethCandles);

// 3. Configure the backtest
BacktestConfig config = BacktestConfig.builder()
    .historicalData(historicalData)
    .initialCapital(new BigDecimal("10000.00"))
    .strategy(myStrategy)
    .accountMode(AccountMode.MARGIN)
    .marginLeverage(10)
    .tradingFeePercentage(new BigDecimal("0.001"))
    .build();

// 4. Run
BacktestResult result = new BacktestEngine().run(config);

System.out.println("Final Net Asset Value: " + result.getFinalValue());
System.out.println("Total P/L: " + result.getPnl() + " (" + result.getPnlPercent() + "%)");
```

`a0-backtester-cli` contains a small `CsvDataLoader` that reads the CSV files produced by `a0-data-ingestor` into `List<Candle>`.

## Limitations

-   **USDT pairs only:** symbols are assumed to be quoted in USDT.
-   **Market orders only:** `submitOrder` fills immediately at the given price, adjusted for slippage.
-   **Timestamps:** `Trade` and `Position` timestamps are currently taken from the wall clock, not from the candle being processed.
