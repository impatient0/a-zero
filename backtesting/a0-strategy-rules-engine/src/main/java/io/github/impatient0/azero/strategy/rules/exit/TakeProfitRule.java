package io.github.impatient0.azero.strategy.rules.exit;

import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class TakeProfitRule implements ExitRule {
    private final double percentage;

    @Override
    public boolean shouldExit(Position position, Candle currentCandle) {
        BigDecimal entryPrice = position.entryPrice();
        BigDecimal currentPrice = currentCandle.close();
        BigDecimal percentageDecimal = BigDecimal.valueOf(percentage / 100.0);

        if (position.direction() == TradeDirection.LONG) {
            BigDecimal targetPrice = entryPrice.multiply(BigDecimal.ONE.add(percentageDecimal));
            return currentPrice.compareTo(targetPrice) >= 0;
        } else { // SHORT
            BigDecimal targetPrice = entryPrice.multiply(BigDecimal.ONE.subtract(percentageDecimal));
            return currentPrice.compareTo(targetPrice) <= 0;
        }
    }
}