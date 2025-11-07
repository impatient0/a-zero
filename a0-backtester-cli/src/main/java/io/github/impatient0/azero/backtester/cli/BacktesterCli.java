package io.github.impatient0.azero.backtester.cli;

import io.github.impatient0.azero.core.model.AccountMode;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.backtester.cli.util.CsvDataLoader;
import io.github.impatient0.azero.backtester.engine.BacktestEngine;
import io.github.impatient0.azero.backtester.model.BacktestConfig;
import io.github.impatient0.azero.backtester.model.BacktestResult;
import io.github.impatient0.azero.strategy.rules.RulesBasedStrategy;
import io.github.impatient0.azero.strategy.rules.loader.StrategyLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The main command-line interface for the A-Zero backtesting application.
 * <p>
 * This class uses picocli to parse command-line arguments and orchestrates the
 * loading of data, strategy configuration, and the execution of the backtest engine.
 */
@Slf4j
@Command(
    name = "a0-backtester-cli",
    mixinStandardHelpOptions = true,
    version = "A-Zero Backtester CLI 0.2.0",
    description = "Runs a trading strategy backtest against historical market data."
)
public class BacktesterCli implements Callable<Integer> {

    @Option(names = {"-s", "--strategy-file"}, required = true, description = "The path to the strategy's .yaml configuration file.")
    private Path strategyFile;

    @Option(names = {"-d", "--data-file"}, required = true, description = "The path to the CSV file containing historical candle data.")
    private Path dataFile;

    @Option(names = {"--symbol"}, required = true, description = "The trading symbol for the backtest (e.g., BTCUSDT).")
    private String symbol;

    @Option(names = {"-a", "--account-mode"}, required = true, description = "The account mode to simulate. Valid values: ${COMPLETION-CANDIDATES}.")
    private AccountMode accountMode;

    @Option(names = {"-c", "--initial-capital"}, description = "The starting capital for the simulation.", defaultValue = "10000.00")
    private BigDecimal initialCapital;

    @Option(names = {"-l", "--leverage"}, description = "The margin leverage to use (only in MARGIN mode).", defaultValue = "5")
    private int leverage;

    /**
     * The main application logic. This method is executed when the command is run.
     *
     * @return 0 on success, 1 on failure.
     * @throws Exception if an error occurs during the backtest.
     */

    @Override
    public Integer call() throws Exception {
        log.info("--- A-Zero Backtester Initializing ---");
        log.info("Strategy: {}, Symbol: {}, Mode: {}", strategyFile, symbol, accountMode);
        log.info("Data File: {}", dataFile);

        // 1. Load Strategy
        StrategyLoader strategyLoader = new StrategyLoader();
        RulesBasedStrategy strategy = strategyLoader.loadFromYaml(strategyFile, symbol);
        log.info("Successfully loaded strategy '{}'", strategy.getName());

        // 2. Load Data
        List<Candle> historicalData = CsvDataLoader.load(dataFile);

        // 3. Configure Engine
        BacktestConfig config = BacktestConfig.builder()
            .historicalData(Map.of(symbol, historicalData)) // Create the required Map
            .initialCapital(initialCapital)
            .strategy(strategy)
            .accountMode(accountMode)
            .marginLeverage(leverage)
            // Using hardcoded defaults for now, can be exposed as CLI options in the future
            .tradingFeePercentage(new BigDecimal("0.001")) // 0.1%
            .slippagePercentage(new BigDecimal("0.0005")) // 0.05%
            .build();
        log.info("Backtest configured. Initial capital: ${}, Leverage: {}x", initialCapital, leverage);

        // 4. Run Simulation
        log.info("--- Starting Simulation ---");
        BacktestEngine engine = new BacktestEngine();
        BacktestResult result = engine.run(config);
        log.info("--- Simulation Complete ---");

        // 5. Report Results
        printResults(result);

        return 0; // Success
    }

    /**
     * Formats and prints the backtest result summary to the console.
     * @param result The result object from the backtest engine.
     */
    private void printResults(BacktestResult result) {
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        NumberFormat percentFormatter = NumberFormat.getPercentInstance(Locale.US);
        percentFormatter.setMaximumFractionDigits(2);

        System.out.println("\n================== Backtest Results ==================");
        System.out.printf("Final Portfolio Value: %s\n", currencyFormatter.format(result.getFinalValue()));
        System.out.printf("Total P/L:             %s (%s)\n",
            currencyFormatter.format(result.getPnl()),
            percentFormatter.format(result.getPnlPercent() / 100.0));
        System.out.printf("Total Trades Executed: %d\n", result.getTotalTrades());
        System.out.println("====================================================\n");
    }

    /**
     * The main entry point for the executable JAR.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new BacktesterCli()).execute(args);
        System.exit(exitCode);
    }
}