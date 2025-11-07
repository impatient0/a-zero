package io.github.impatient0.azero.strategy.rules;

import io.github.impatient0.azero.core.model.TradeDirection;
import io.github.impatient0.azero.strategy.rules.exit.StopLossRule;
import io.github.impatient0.azero.strategy.rules.exit.TakeProfitRule;
import io.github.impatient0.azero.strategy.rules.indicator.RsiIndicator;
import io.github.impatient0.azero.strategy.rules.loader.StrategyLoader;
import io.github.impatient0.azero.strategy.rules.sizing.FixedPercentageSizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.dataformat.yaml.JacksonYAMLParseException;

import static org.junit.jupiter.api.Assertions.*;

class StrategyLoaderTest {

    private StrategyLoader strategyLoader;

    @BeforeEach
    void setUp() {
        strategyLoader = new StrategyLoader();
    }

    private Path getResourcePath(String fileName) throws Exception {
        URL resource = getClass().getClassLoader().getResource(fileName);
        if (resource == null) {
            throw new IllegalStateException("Test resource '" + fileName + "' not found.");
        }
        return Paths.get(resource.toURI());
    }

    @Nested
    @DisplayName("GIVEN a valid YAML file")
    class ValidFileTests {
        @Test
        @DisplayName("WHEN loading, THEN a correctly configured RulesBasedStrategy should be created.")
        void loadFromYaml_withValidFile_shouldCreateCorrectlyConfiguredStrategy() throws Exception {
            // --- ARRANGE ---
            Path testStrategyPath = getResourcePath("test-strategy.yaml");

            // --- ACT ---
            RulesBasedStrategy strategy = strategyLoader.loadFromYaml(testStrategyPath, "BTCUSDT");

            // --- ASSERT ---
            assertEquals("Test RSI Strategy", strategy.getName());
            assertEquals("BTCUSDT", strategy.getSymbol());
            assertEquals(TradeDirection.LONG, strategy.getDirection());
            assertEquals(14, strategy.getMaxLookbackPeriod());
            assertEquals(1, strategy.getEntryIndicators().size());
            assertInstanceOf(RsiIndicator.class, strategy.getEntryIndicators().getFirst());
            assertEquals(2, strategy.getExitRules().size());
            assertTrue(strategy.getExitRules().stream().anyMatch(r -> r instanceof StopLossRule));
            assertTrue(strategy.getExitRules().stream().anyMatch(r -> r instanceof TakeProfitRule));
            assertInstanceOf(FixedPercentageSizer.class, strategy.getPositionSizer());
        }
    }

    @Nested
    @DisplayName("GIVEN an invalid YAML file")
    class InvalidFileTests {

        @Test
        @DisplayName("WHEN the file is malformed (bad syntax), THEN an IOException should be thrown.")
        void loadFromYaml_withMalformedFile_shouldThrowException() throws Exception {
            // --- ARRANGE ---
            String malformedYaml = "strategy_name: Bad Indent\n  direction: LONG";
            Path tempFile = Files.createTempFile("malformed-strategy", ".yaml");
            Files.writeString(tempFile, malformedYaml);

            // --- ACT & ASSERT ---
            assertThrows(JacksonYAMLParseException.class,
                () -> strategyLoader.loadFromYaml(tempFile, "BTCUSDT"),
                "Should throw a Jackson parsing exception for syntactically incorrect YAML.");

            Files.delete(tempFile);
        }

        @Test
        @DisplayName("WHEN the file contains an unknown indicator type, THEN an IllegalArgumentException should be thrown.")
        void loadFromYaml_withUnknownIndicator_shouldThrowException() throws Exception {
            // --- ARRANGE ---
            String unknownIndicatorYaml = """
                    strategy_name: "Unknown Indicator Strategy"
                    direction: LONG
                    timeframe: "1h"
                    position_sizing: { type: "Fixed_Percentage", percentage: 1.0 }
                    entry_rules:
                      - indicator: "MACD"
                        fast: 12
                        slow: 26
                    exit_rules: []
                    """;
            Path tempFile = Files.createTempFile("unknown-indicator", ".yaml");
            Files.writeString(tempFile, unknownIndicatorYaml);

            // --- ACT & ASSERT ---
            assertThrows(InvalidTypeIdException.class,
                () -> strategyLoader.loadFromYaml(tempFile, "BTCUSDT"),
                "Should throw an InvalidTypeIdException for an unsupported indicator type.");

            Files.delete(tempFile);
        }
    }
}