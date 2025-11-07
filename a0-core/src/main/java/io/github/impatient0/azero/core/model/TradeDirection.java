package io.github.impatient0.azero.core.model;

/**
 * Represents the direction of a trade or position.
 * <ul>
 *   <li>{@code LONG}: A position that profits from an increase in price.</li>
 *   <li>{@code SHORT}: A position that profits from a decrease in price.</li>
 * </ul>
 */
public enum TradeDirection {
    LONG,
    SHORT;

    /**
     * Returns the opposite trading direction.
     * @return {@code SHORT} if the current direction is {@code LONG}, and vice-versa.
     */
    public TradeDirection opposite() {
        return this == LONG ? SHORT : LONG;
    }
}