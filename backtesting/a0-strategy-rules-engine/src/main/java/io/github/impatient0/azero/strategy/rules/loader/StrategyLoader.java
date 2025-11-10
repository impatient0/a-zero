package io.github.impatient0.azero.strategy.rules.loader;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import io.github.impatient0.azero.strategy.rules.RulesBasedStrategy;
import io.github.impatient0.azero.strategy.rules.config.*;
import io.github.impatient0.azero.strategy.rules.exit.ExitRule;
import io.github.impatient0.azero.strategy.rules.exit.StopLossRule;
import io.github.impatient0.azero.strategy.rules.exit.TakeProfitRule;
import io.github.impatient0.azero.strategy.rules.indicator.Indicator;
import io.github.impatient0.azero.strategy.rules.indicator.RsiIndicator;
import io.github.impatient0.azero.strategy.rules.sizing.FixedPercentageSizer;
import io.github.impatient0.azero.strategy.rules.sizing.PositionSizer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A factory class responsible for loading a declarative YAML configuration file
 * and constructing an executable {@link RulesBasedStrategy} instance from it.
 */
public class StrategyLoader {
    private final ObjectMapper mapper;

    public StrategyLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Loads a strategy from the specified YAML file path.
     *
     * @param filePath The path to the strategy YAML configuration file.
     * @return A fully configured, executable {@link RulesBasedStrategy}.
     * @throws IOException if the file cannot be read or parsed.
     */
    public RulesBasedStrategy loadFromYaml(Path filePath, String symbol) throws IOException {
        StrategyConfig config = mapper.readValue(filePath.toFile(), StrategyConfig.class);

        Duration timeframe = TimeframeParser.parse(config.timeframe());

        List<Indicator> entryIndicators = config.entryRules().stream()
            .map(indicatorConfig -> createIndicator(indicatorConfig, timeframe))
            .collect(Collectors.toList());

        List<ExitRule> exitRules = config.exitRules().stream()
            .map(this::createExitRule)
            .collect(Collectors.toList());

        PositionSizer sizer = createPositionSizer(config.positionSizing());

        int maxLookback = entryIndicators.stream()
            .mapToInt(Indicator::getLookbackPeriod)
            .max()
            .orElse(0);

        return new RulesBasedStrategy(
            config.strategyName(),
            symbol,
            config.direction(),
            entryIndicators,
            exitRules,
            sizer,
            maxLookback
        );
    }

    private Indicator createIndicator(IndicatorConfig config, Duration timeframe) {
        return switch (config) {
            case RsiIndicatorConfig rsiConfig -> new RsiIndicator(rsiConfig, timeframe);
            // TODO: add other cases when more indicators are implemented
            default -> throw new IllegalArgumentException("Unsupported indicator type: " + config.getClass().getSimpleName());
        };
    }

    private ExitRule createExitRule(ExitRuleConfig config) {
        return switch (config) {
            case StopLossConfig slConfig -> new StopLossRule(slConfig.percentage());
            case TakeProfitConfig tpConfig -> new TakeProfitRule(tpConfig.percentage());
            default -> throw new IllegalArgumentException("Unsupported exit rule type: " + config.getClass().getSimpleName());
        };
    }

    private PositionSizer createPositionSizer(PositionSizingConfig config) {
        return switch (config) {
            case FixedPercentageSizingConfig fpConfig -> new FixedPercentageSizer(fpConfig.percentage());
            // TODO: add other cases when more sizers are implemented
            default -> throw new IllegalArgumentException("Unsupported position sizing type: " + config.getClass().getSimpleName());
        };
    }
}