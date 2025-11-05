package io.github.impatient0.azero.strategy.rules;

import io.github.impatient0.azero.core.event.MarketEvent;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;
import io.github.impatient0.azero.core.strategy.Strategy;
import io.github.impatient0.azero.core.strategy.TradingContext;
import io.github.impatient0.azero.strategy.rules.exit.ExitRule;
import io.github.impatient0.azero.strategy.rules.indicator.Indicator;
import io.github.impatient0.azero.strategy.rules.sizing.PositionSizer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class RulesBasedStrategy implements Strategy {
    @Getter
    private final String name;
    private final String symbol;
    private final TradeDirection direction;
    private final List<Indicator> entryIndicators;
    private final List<ExitRule> exitRules;
    private final PositionSizer positionSizer; // NEW: Position Sizer
    private final int maxLookbackPeriod;
    private int barCount = 0;

    @Override
    public void onMarketEvent(MarketEvent event, TradingContext context) {
        if (!event.symbol().equals(this.symbol)) {
            return;
        }
        barCount++;
        Candle candle = event.candle();
        entryIndicators.forEach(indicator -> indicator.update(candle));

        var openPositionOpt = context.getOpenPosition(this.symbol);

        if (openPositionOpt.isPresent()) {
            Position openPosition = openPositionOpt.get();
            for (ExitRule exitRule : exitRules) {
                if (exitRule.shouldExit(openPosition, candle)) {
                    log.info("[{}] Exit rule triggered for position {}. Closing position at price {}.",
                        name, symbol, candle.close());
                    context.submitOrder(symbol, openPosition.direction().opposite(), openPosition.quantity(), candle.close());
                    return;
                }
            }
        } else {
            if (shouldEnter()) {
                BigDecimal nav = context.getNetAssetValue();
                BigDecimal price = candle.close();
                BigDecimal quantity = positionSizer.calculateQuantity(nav, price);

                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("[{}] All entry indicators triggered for {}. Opening position with quantity {} at price {}.",
                        name, symbol, quantity.toPlainString(), price);
                    context.submitOrder(symbol, direction, quantity, price);
                }
            }
        }
    }

    private boolean shouldEnter() {
        if (barCount <= maxLookbackPeriod) {
            return false;
        }
        return entryIndicators.stream().allMatch(Indicator::isSignalTriggered);
    }
}