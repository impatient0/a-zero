package io.github.impatient0.azero.backtester.cli;

import io.github.impatient0.azero.core.model.AccountMode;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The main command-line interface for the A-Zero backtesting application.
 * <p>
 * This class uses picocli to parse command-line arguments and orchestrates the
 * loading of data, strategy configuration, and the execution of the backtest engine.
 */
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
        // TODO: implement actual logic here
        System.out.println("Backtester CLI Initialized (Logic to be implemented)");
        System.out.println("Strategy File: " + strategyFile);
        System.out.println("Data File: " + dataFile);
        System.out.println("Symbol: " + symbol);
        System.out.println("Account Mode: " + accountMode);
        System.out.println("Initial Capital: " + initialCapital);
        System.out.println("Leverage: " + leverage);
        return 0; // Success
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