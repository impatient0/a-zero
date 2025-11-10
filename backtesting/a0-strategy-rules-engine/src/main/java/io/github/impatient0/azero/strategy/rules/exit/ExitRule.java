package io.github.impatient0.azero.strategy.rules.exit;

import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;

/**
 * An interface for a rule that determines when to exit an open position.
 */
public interface ExitRule {
    /**
     * Checks if this rule's exit condition is met for the given position and candle.
     *
     * @param position      The currently open position.
     * @param currentCandle The latest candle data.
     * @return {@code true} if the position should be exited, otherwise {@code false}.
     */
    boolean shouldExit(Position position, Candle currentCandle);
}