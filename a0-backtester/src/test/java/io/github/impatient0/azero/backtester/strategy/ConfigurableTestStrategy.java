package io.github.impatient0.azero.backtester.strategy;

import io.github.impatient0.azero.core.event.MarketEvent;
import io.github.impatient0.azero.core.model.TradeDirection;
import io.github.impatient0.azero.core.strategy.Strategy;
import io.github.impatient0.azero.core.strategy.TradingContext;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Queue;

/**
 * A configurable strategy for testing purposes. It executes a pre-defined queue
 * of actions on specific candle ticks.
 */
@RequiredArgsConstructor
public class ConfigurableTestStrategy implements Strategy {

    /**
     * A record representing a single trading action to be performed at a specific time.
     *
     * @param onCandleIndex The zero-based index of the candle on which to execute this action.
     * @param symbol        The symbol to trade.
     * @param direction     The direction of the trade.
     * @param quantity      The quantity to trade.
     */
    public record Action(int onCandleIndex, String symbol, TradeDirection direction, BigDecimal quantity) {
    }

    private final Queue<Action> actions;
    private int eventCount = 0;

    @Override
    public void onMarketEvent(MarketEvent event, TradingContext context) {
        Action nextAction = actions.peek();
        if (nextAction != null && eventCount == nextAction.onCandleIndex()) {
            Action actionToExecute = actions.poll();
            context.submitOrder(
                actionToExecute.symbol(),
                actionToExecute.direction(),
                actionToExecute.quantity(),
                event.candle().close()
            );
        }
        eventCount++;
    }
}