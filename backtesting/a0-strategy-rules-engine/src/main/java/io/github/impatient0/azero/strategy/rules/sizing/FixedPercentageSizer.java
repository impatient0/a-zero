package io.github.impatient0.azero.strategy.rules.sizing;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A {@link PositionSizer} that calculates position size as a fixed percentage
 * of the total account Net Asset Value (NAV).
 */
@RequiredArgsConstructor
public class FixedPercentageSizer implements PositionSizer {
    private static final int QUANTITY_SCALE = 8;
    private final double percentage;

    @Override
    public BigDecimal calculateQuantity(BigDecimal netAssetValue, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO; // Prevent division by zero
        }

        BigDecimal percentageDecimal = BigDecimal.valueOf(percentage / 100.0);
        BigDecimal riskAmount = netAssetValue.multiply(percentageDecimal);

        return riskAmount.divide(price, QUANTITY_SCALE, RoundingMode.DOWN);
    }
}