# A-Zero :: Strategy Rules Engine

This library builds executable trading strategies from declarative YAML files. It contains all of the project's technical-analysis and rule-evaluation logic; RSI values are computed with [ta4j](https://github.com/ta4j/ta4j).

The core purpose of this engine is to separate the *definition* of a strategy (the human-readable YAML file) from its *execution* (by the `a0-backtester` engine).

## Core Components

-   **`StrategyLoader`**: The primary public entry point for the library. This class reads and parses a YAML strategy configuration file and acts as a factory, constructing a fully configured object that implements the `a0-core` `Strategy` interface.

-   **`RulesBasedStrategy`**: The concrete `Strategy` implementation produced by the loader. This class is composed of a set of rules and makes decisions based on them:
    -   **Entry Logic**: Enters a position only when **all** of its configured entry indicator rules are simultaneously true (an "AND" gate).
    -   **Exit Logic**: Exits an open position as soon as **any** of its configured exit rules are true (an "OR" gate).

-   **Indicators & Rules**: Each part is behind an interface: `Indicator`, `ExitRule` and `PositionSizer`. There is currently one indicator (RSI), two exit rules (StopLoss, TakeProfit) and one sizer (Fixed_Percentage).

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

Load a strategy instance from a file with `StrategyLoader`:

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

## Adding rule types

A new indicator, exit rule or sizer needs a config record added to the matching sealed interface (`IndicatorConfig`, `ExitRuleConfig`, `PositionSizingConfig`) with a Jackson type name, an implementation class, and a case in `StrategyLoader`. Obvious candidates that don't exist yet: a moving-average crossover, MACD, a trailing stop, and risk-based sizing.