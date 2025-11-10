# A-Zero :: Strategy Rules Engine

This module provides a powerful library for creating executable trading strategies from simple, declarative YAML configuration files. It acts as the "brain" of a strategy, encapsulating all logic related to technical analysis and rule-based decision-making.

The core purpose of this engine is to separate the *definition* of a strategy (the human-readable YAML file) from its *execution* (the `a0-backtester` or a live trading module).

## Core Components

-   **`StrategyLoader`**: The primary public entry point for the library. This class reads and parses a YAML strategy configuration file and acts as a factory, constructing a fully configured object that implements the `a0-core` `Strategy` interface.

-   **`RulesBasedStrategy`**: The concrete `Strategy` implementation produced by the loader. This class is composed of a set of rules and makes decisions based on them:
    -   **Entry Logic**: Enters a position only when **all** of its configured entry indicator rules are simultaneously true (an "AND" gate).
    -   **Exit Logic**: Exits an open position as soon as **any** of its configured exit rules are true (an "OR" gate).

-   **Indicators & Rules**: The engine is built on an extensible system of indicators (`Indicator`), exit rules (`ExitRule`), and position sizers (`PositionSizer`). This module provides initial implementations for common use cases.

## The YAML Strategy Configuration File

The behavior of a `RulesBasedStrategy` is defined entirely by its YAML configuration. Below is a detailed example of the expected structure.

```yaml
# A unique, human-readable name for this strategy configuration.
strategy_name: "RSI Oversold Entry with Fixed P/L"

# The default direction for opening positions. Can be LONG or SHORT.
direction: LONG

# The timeframe the strategy operates on. Used by indicators for calculations.
# Supported units: m (minutes), h (hours), d (days).
timeframe: "4h"

# Defines how the size of a new position is calculated.
position_sizing:
  # Type of the sizing rule.
  type: "Fixed_Percentage"
  # The percentage of the total account Net Asset Value (NAV) to allocate.
  percentage: 2.5

# A list of indicator rules for entering a position. All must be true.
entry_rules:
  - # The type of indicator.
    indicator: "RSI"
    # The lookback period for the RSI calculation.
    period: 14
    # The comparison to perform. Can be less_than or greater_than.
    condition: "less_than"
    # The threshold value to compare the RSI against.
    value: 30

# A list of rules for exiting a position. The first one to become true will trigger an exit.
exit_rules:
  - # The type of exit rule.
    type: "StopLoss"
    # The percentage drop from the entry price that triggers an exit.
    percentage: 2.0
  - # The type of exit rule.
    type: "TakeProfit"
    # The percentage gain from the entry price that triggers an exit.
    percentage: 5.0
```

## How to Use

The `StrategyLoader` makes it simple to create a strategy instance from a file.

```java
import io.github.impatient0.azero.strategy.rules.loader.StrategyLoader;
import io.github.impatient0.azero.strategy.rules.RulesBasedStrategy;
import java.nio.file.Path;
import java.nio.file.Paths;

// ...

// 1. Create an instance of the loader.
StrategyLoader loader = new StrategyLoader();

// 2. Define the path to your configuration file and the symbol to trade.
Path strategyPath = Paths.get("path/to/my-strategy.yaml");
String symbol = "BTCUSDT";

try {
    // 3. Load the strategy.
    RulesBasedStrategy myStrategy = loader.loadFromYaml(strategyPath, symbol);

    // 4. The 'myStrategy' object is now a fully configured, executable instance
    //    of the Strategy interface, ready to be passed to the BacktestEngine.
    //
    //    BacktestConfig config = BacktestConfig.builder()
    //        .strategy(myStrategy)
    //        // ... other config ...
    //        .build();
    //
} catch (Exception e) {
    System.err.println("Failed to load strategy: " + e.getMessage());
}
```

## Future Work & Backlog

The rules engine is designed for extensibility. The following are planned enhancements:

-   **Implement More Indicators**: Add support for other common indicators, such as `EMA_Cross`, `MACD`, etc., by adding new records to the `IndicatorConfig` hierarchy.
-   **Implement More Exit Rules**: Add new exit types, such as `TrailingStopLoss`.
-   **Implement More Position Sizers**: Add new sizing methods, such as sizing based on a fixed dollar amount or based on risk (stop-loss distance).