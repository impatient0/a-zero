package io.github.impatient0.azero.strategy.rules.indicator;

import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.strategy.rules.config.RsiIndicatorConfig;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * An {@link Indicator} implementation for the Relative Strength Index (RSI).
 * <p>
 * This class uses the ta4j library to perform the RSI calculation. It maintains
 * a time series of bars and checks if the most recent RSI value meets a

 * configured condition (e.g., is less than a certain threshold).
 */
public final class RsiIndicator implements Indicator {
    private final RsiIndicatorConfig config;
    private final Duration timePeriod;
    private final BarSeries series;
    private final RSIIndicator rsi;

    /**
     * Constructs a new RsiIndicator.
     *
     * @param config     The configuration object containing the period, condition, and value.
     * @param timePeriod The duration of each candle (e.g., 1 hour), which is required by TA4j.
     */
    public RsiIndicator(RsiIndicatorConfig config, Duration timePeriod) {
        this.config = config;
        this.timePeriod = timePeriod;
        this.series = new BaseBarSeriesBuilder().withName("rsi-series").build();

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        this.rsi = new RSIIndicator(closePrice, config.period());
    }

    @Override
    public void update(Candle candle) {
        ZonedDateTime endTime = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(candle.timestamp()),
            ZoneId.of("UTC")
        );

        series.addBar(series.barBuilder()
            .timePeriod(this.timePeriod)
            .endTime(endTime.toInstant())
            .openPrice(DecimalNum.valueOf(candle.open()))
            .highPrice(DecimalNum.valueOf(candle.high()))
            .lowPrice(DecimalNum.valueOf(candle.low()))
            .closePrice(DecimalNum.valueOf(candle.close()))
            .volume(DecimalNum.valueOf(candle.volume()))
            .build()
        );
    }

    @Override
    public boolean isSignalTriggered() {
        // Do not trigger a signal if the indicator has not yet collected enough
        // data to be stable.
        if (series.getBarCount() <= getLookbackPeriod()) {
            return false;
        }

        // Get the most recent RSI value from the indicator.
        DecimalNum currentValue = DecimalNum.valueOf(rsi.getValue(series.getEndIndex()));
        DecimalNum threshold = DecimalNum.valueOf(config.value());

        // Compare the current value to the threshold based on the configured condition.
        return switch (config.condition()) {
            case LESS_THAN -> currentValue.isLessThan(threshold);
            case GREATER_THAN -> currentValue.isGreaterThan(threshold);
        };
    }

    @Override
    public int getLookbackPeriod() {
        // The RSI indicator requires `period` data points to calculate its first value.
        return config.period();
    }
}