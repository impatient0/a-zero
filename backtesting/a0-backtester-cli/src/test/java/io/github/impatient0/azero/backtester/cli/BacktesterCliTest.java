package io.github.impatient0.azero.backtester.cli;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktesterCliTest {

    private Path getResourcePath(String fileName) throws Exception {
        URL resource = getClass().getClassLoader().getResource(fileName);
        if (resource == null) {
            throw new IllegalStateException("Test resource '" + fileName + "' not found.");
        }
        return Paths.get(resource.toURI());
    }

    @Nested
    @DisplayName("GIVEN valid inputs")
    class HappyPathTests {

        @Test
        @DisplayName("GIVEN valid strategy and data files, WHEN the CLI is executed, THEN it should succeed and print results.")
        void cli_happyPath_withValidInputs_shouldSucceedAndPrintResults() throws Exception {
            // --- ARRANGE ---
            Path strategyFile = getResourcePath("happy-path-strategy.yaml");
            Path dataFile = getResourcePath("happy-path-data.csv");

            String[] args = {
                "--strategy-file", strategyFile.toString(),
                "--data-file", dataFile.toString(),
                "--symbol", "BTCUSDT",
                "--account-mode", "SPOT_ONLY",
                "--initial-capital", "10000"
            };

            // --- ACT ---
            AtomicInteger exitCode = new AtomicInteger(-1);
            String stdOut = SystemLambda.tapSystemOut(() -> exitCode.set(new CommandLine(new BacktesterCli()).execute(args)));

            // --- ASSERT ---
            assertEquals(0, exitCode.get(), "The process should exit with code 0 for success.");

            // Assert that the output contains the expected report format
            assertTrue(stdOut.contains("================== Backtest Results =================="), "Output should contain the results header.");
            assertTrue(stdOut.contains("Final Portfolio Value:"), "Output should contain the final value.");
            assertTrue(stdOut.contains("Total P/L:"), "Output should contain the total P/L.");
            assertTrue(stdOut.contains("Total Trades Executed: 1"), "Output should show one trade was executed.");
        }
    }

    @Nested
    @DisplayName("GIVEN invalid command-line arguments")
    class CliArgumentValidationTests {

        @Test
        @DisplayName("WHEN a required option is missing, THEN it should fail with a helpful error message.")
        void cli_missingRequiredOption_shouldFailWithHelpfulMessage() throws Exception {
            // --- ARRANGE ---
            // Intentionally omit the required --strategy-file
            String[] args = {
                "--data-file", "dummy.csv",
                "--symbol", "BTCUSDT",
                "--account-mode", "SPOT_ONLY"
            };

            // --- ACT ---
            AtomicInteger exitCode = new AtomicInteger(0);
            String stdErr = SystemLambda.tapSystemErr(() -> exitCode.set(new CommandLine(new BacktesterCli()).execute(args)));

            // --- ASSERT ---
            // 1. Assert that the process exits with a non-zero (error) code.
            assertEquals(2, exitCode.get(), "The process should exit with an error code for invalid input.");

            // 2. Assert that the standard error stream contains the helpful message from picocli.
            assertTrue(stdErr.contains("Missing required option:"),
                "Standard error should contain a message about the missing option.");
        }

        @Test
        @DisplayName("WHEN an invalid enum value is provided, THEN it should fail with a helpful error message.")
        void cli_invalidEnumValue_shouldFailWithHelpfulMessage() throws Exception {
            // --- ARRANGE ---
            Path strategyFile = getResourcePath("happy-path-strategy.yaml");
            Path dataFile = getResourcePath("happy-path-data.csv");

            // Provide an account mode that does not exist in our enum.
            String[] args = {
                "--strategy-file", strategyFile.toString(),
                "--data-file", dataFile.toString(),
                "--symbol", "BTCUSDT",
                "--account-mode", "INVALID_MODE"
            };

            // --- ACT ---
            AtomicInteger exitCode = new AtomicInteger(0);
            String stdErr = SystemLambda.tapSystemErr(() -> exitCode.set(new CommandLine(new BacktesterCli()).execute(args)));

            // --- ASSERT ---
            // 1. Assert the process exits with picocli's standard error code.
            assertEquals(2, exitCode.get(), "The process should exit with an error code for invalid input.");

            // 2. Assert that standard error contains the specific validation message for the enum.
            assertTrue(stdErr.contains("Invalid value for option"),
                "Error message should identify the invalid value and option.");

            // 3. Assert that the message helpfully lists the valid candidates.
            assertTrue(stdErr.contains("expected one of"),
                "Error message should list the valid enum values.");
        }
    }

    @Nested
    @DisplayName("GIVEN invalid input files")
    class InputFileIntegrityTests {

        @Test
        @DisplayName("WHEN the data file does not exist, THEN it should fail gracefully with a non-zero exit code.")
        void cli_nonexistentFile_shouldFailGracefully() throws Exception {
            // --- ARRANGE ---
            Path strategyFile = getResourcePath("happy-path-strategy.yaml");
            // A path to a file that is guaranteed not to exist.
            Path nonexistentDataFile = Paths.get("path", "to", "nonexistent.csv");

            String[] args = {
                "--strategy-file", strategyFile.toString(),
                "--data-file", nonexistentDataFile.toString(),
                "--symbol", "BTCUSDT",
                "--account-mode", "SPOT_ONLY"
            };

            // --- ACT ---
            AtomicInteger exitCode = new AtomicInteger(0);
            String stdOut = SystemLambda.tapSystemOut(() -> exitCode.set(new CommandLine(new BacktesterCli()).execute(args)));

            // --- ASSERT ---
            // 1. Assert the process exits with a generic failure code (1).
            assertEquals(1, exitCode.get(), "The process should exit with a failure code.");

            // 2. Assert that a relevant error was logged to the console.
            assertTrue(stdOut.contains("Data file not found:"),
                "The log output should indicate that the data file was not found.");
            // 3. Assert that the "success" message is NOT present.
            assertFalse(stdOut.contains("Backtest Complete"),
                "The simulation should not complete successfully.");
        }

        @Test
        @DisplayName("WHEN the strategy YAML file is malformed, THEN it should fail gracefully.")
        void cli_malformedYaml_shouldFailGracefully(@TempDir Path tempDir) throws Exception {
            // --- ARRANGE ---
            // Create a temporary, syntactically incorrect YAML file.
            Path malformedStrategyFile = tempDir.resolve("malformed.yaml");
            String malformedYaml = "strategy_name: Bad Indent\n  direction: LONG";
            Files.writeString(malformedStrategyFile, malformedYaml);

            Path dataFile = getResourcePath("happy-path-data.csv");

            String[] args = {
                "--strategy-file", malformedStrategyFile.toString(),
                "--data-file", dataFile.toString(),
                "--symbol", "BTCUSDT",
                "--account-mode", "SPOT_ONLY"
            };

            // --- ACT ---
            AtomicInteger exitCode = new AtomicInteger(0);
            String stdErr = SystemLambda.tapSystemErr(() -> exitCode.set(new CommandLine(new BacktesterCli()).execute(args)));

            // --- ASSERT ---
            // 1. Assert the process exits with our generic failure code.
            assertEquals(1, exitCode.get(), "The process should exit with a failure code.");

            // 2. Assert that our user-friendly error message was printed to stderr.
            assertTrue(stdErr.contains("ERROR:"), "A user-friendly error message should be printed to stderr.");

            // 3: Assert for the more specific and helpful error message from Jackson.
            assertTrue(stdErr.contains("mapping values are not allowed here"),
                "The error message should contain the specific parsing error from Jackson.");
        }
    }

    @Nested
    @DisplayName("GIVEN different valid inputs")
    class BehavioralVariationTests {

        @Test
        @DisplayName("WHEN run in MARGIN vs SPOT mode, THEN the final P/L should be different.")
        void cli_runInMarginMode_shouldProduceDifferentResult() throws Exception {
            // --- ARRANGE ---
            Path strategyFile = getResourcePath("happy-path-strategy.yaml");
            Path dataFile = getResourcePath("happy-path-data.csv");

            // Define args for SPOT mode
            String[] spotArgs = {
                "--strategy-file", strategyFile.toString(),
                "--data-file", dataFile.toString(),
                "--symbol", "BTCUSDT",
                "--account-mode", "SPOT_ONLY",
                "--initial-capital", "10000"
            };

            // Define args for MARGIN mode with the same setup
            String[] marginArgs = {
                "--strategy-file", strategyFile.toString(),
                "--data-file", dataFile.toString(),
                "--symbol", "BTCUSDT",
                "--account-mode", "MARGIN",
                "--initial-capital", "10000",
                "--leverage", "5"
            };

            // --- ACT ---
            // Run the SPOT backtest
            AtomicInteger spotExitCode = new AtomicInteger(-1);
            String spotStdOut = SystemLambda.tapSystemOut(() -> spotExitCode.set(new CommandLine(new BacktesterCli()).execute(spotArgs)));

            // Run the MARGIN backtest
            AtomicInteger marginExitCode = new AtomicInteger(-1);
            String marginStdOut = SystemLambda.tapSystemOut(() -> marginExitCode.set(new CommandLine(new BacktesterCli()).execute(marginArgs)));

            // --- ASSERT ---
            // 1. Assert both runs were successful.
            assertEquals(0, spotExitCode.get(), "Spot run should succeed.");
            assertEquals(0, marginExitCode.get(), "Margin run should succeed.");

            // 2. The critical assertion: The results must be different.
            assertNotEquals(spotStdOut, marginStdOut, "The console output for SPOT and MARGIN modes should not be identical.");
        }
    }
}