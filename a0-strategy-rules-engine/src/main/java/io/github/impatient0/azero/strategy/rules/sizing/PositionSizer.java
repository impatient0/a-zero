package io.github.impatient0.azero.strategy.rules.sizing;

import java.math.BigDecimal;

/**
 * An interface for a rule that determines the quantity for a new position.
 */
public interface PositionSizer {
    /**
     * Calculates the quantity of the base asset to trade for a new position.
     *
     * @param netAssetValue The current Net Asset Value of the entire portfolio.
     * @param price         The current price of the asset to be traded.
     * @return The calculated quantity for the new order.
     */
    BigDecimal calculateQuantity(BigDecimal netAssetValue, BigDecimal price);
}