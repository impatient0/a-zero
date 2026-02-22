# A-Zero: Current Project State

## Current Phase/Epic
**v0.3 - AI-Enabled Backtesting Framework**
*Goal:* Integrate LLM-derived sentiment analysis into the backtesting engine and declarative YAML strategies.

## Recently Completed
*   **Stack Upgrade:** Successfully migrated to Java 25, Spring Boot 4.0, and Oracle GraalVM. Maven Wrapper configured.
*   **Data Pipeline:** Built `a0-sentiment-provider` (with `GeminiSentimentProvider`) and `a0-sentiment-preprocessor-cli` for offline LLM processing.
*   **Engine Refactor:** `MarketContext` interface created. `TradingContext` now successfully exposes `Optional<SentimentSignal> getCurrentSentiment(String symbol)`. Engine correctly indexes and forward-fills sentiment data during the simulation loop.

## Active Blockers/Debt
*   *Tech Debt (v0.3.1/v0.4):* The `a0-backtester-cli` currently only supports loading a single asset CSV. The engine supports multi-asset, but the CLI is a bottleneck.
*   *Tech Debt (v0.3.1/v0.4):* `Strategy` loading in the CLI is currently limited to YAML via `StrategyLoader`. Needs to be refactored to use SPI for custom Java strategies.

## Imminent Next Steps
**Target:** Complete Step 2 & 3 of the "Sentiment Rules Integration" task.
1.  **Implement `SentimentIndicator`:** In `a0-strategy-rules-engine`, create the indicator. It must accept a target `Sentiment` and a `minConfidence` double. Its `isSignalTriggered(MarketContext)` method must retrieve the `SentimentSignal` and return true ONLY IF the sentiment matches the target AND the confidence is >= `minConfidence`.
2.  **Update `StrategyLoader`:** Update the YAML parsing logic to recognize the "Sentiment" indicator and parse the optional `min_confidence` field (defaulting to 0.0).
3.  **Testing:** Write strict, Gherkin-style unit tests for the indicator and loader, adhering to `testing-sanctity.md`.