package io.github.impatient0.azero.strategy.rules;

import io.github.impatient0.azero.core.event.MarketEvent;
import io.github.impatient0.azero.core.model.Candle;
import io.github.impatient0.azero.core.model.Position;
import io.github.impatient0.azero.core.model.TradeDirection;
import io.github.impatient0.azero.core.strategy.TradingContext;
import io.github.impatient0.azero.strategy.rules.exit.ExitRule;
import io.github.impatient0.azero.strategy.rules.indicator.Indicator;
import io.github.impatient0.azero.strategy.rules.sizing.PositionSizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulesBasedStrategyTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final Candle DUMMY_CANDLE = new Candle(1L, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
    private static final MarketEvent DUMMY_EVENT = new MarketEvent(SYMBOL, DUMMY_CANDLE);

    @Mock
    private Indicator indicator1;
    @Mock
    private Indicator indicator2;
    @Mock
    private ExitRule exitRule;
    @Mock
    private PositionSizer positionSizer;
    @Mock
    private TradingContext tradingContext;

    @Nested
    @DisplayName("GIVEN no open position")
    class NoPositionOpen {

        @BeforeEach
        void givenNoOpenPosition() {
            when(tradingContext.getOpenPosition(SYMBOL)).thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("WHEN all entry indicators are triggered, THEN an order should be submitted.")
        void whenAllIndicatorsTrigger_thenOrderSubmitted() {
            // --- ARRANGE ---
            RulesBasedStrategy strategy = new RulesBasedStrategy(
                "TestStrategy",
                SYMBOL,
                TradeDirection.LONG,
                List.of(indicator1, indicator2),
                List.of(exitRule),
                positionSizer,
                0 // Lookback period is 0 for this test
            );
            when(indicator1.isSignalTriggered()).thenReturn(true);
            when(indicator2.isSignalTriggered()).thenReturn(true);
            when(positionSizer.calculateQuantity(any(), any())).thenReturn(BigDecimal.ONE);
            when(tradingContext.getNetAssetValue()).thenReturn(new BigDecimal("1000"));

            // --- ACT ---
            strategy.onMarketEvent(DUMMY_EVENT, tradingContext);

            // --- ASSERT ---
            verify(tradingContext, times(1)).submitOrder(SYMBOL, TradeDirection.LONG, BigDecimal.ONE, DUMMY_CANDLE.close());
        }

        @Test
        @DisplayName("WHEN only some entry indicators are triggered, THEN no order should be submitted.")
        void whenSomeIndicatorsTrigger_thenNoOrderSubmitted() {
            // --- ARRANGE ---
            RulesBasedStrategy strategy = new RulesBasedStrategy(
                "TestStrategy",
                SYMBOL,
                TradeDirection.LONG,
                List.of(indicator1, indicator2),
                List.of(exitRule),
                positionSizer,
                0 // Lookback period is 0 for this test
            );
            when(indicator1.isSignalTriggered()).thenReturn(true);
            when(indicator2.isSignalTriggered()).thenReturn(false); // One indicator is false

            // --- ACT ---
            strategy.onMarketEvent(DUMMY_EVENT, tradingContext);

            // --- ASSERT ---
            verify(tradingContext, never()).submitOrder(any(), any(), any(), any());
        }

        @Test
        @DisplayName("WHEN the lookback period has not passed, THEN entry indicators should not be checked and no order submitted.")
        void whenDuringLookbackPeriod_thenEntryLogicIsSkipped() {
            // --- ARRANGE ---
            RulesBasedStrategy strategy = new RulesBasedStrategy(
                "TestStrategy", SYMBOL, TradeDirection.LONG, List.of(indicator1), List.of(),
                positionSizer, 10 // maxLookbackPeriod = 10
            );

            // --- ACT ---
            for (int i = 0; i < 5; i++) {
                strategy.onMarketEvent(DUMMY_EVENT, tradingContext);
            }

            // --- ASSERT ---
            verify(indicator1, never()).isSignalTriggered();
            verify(tradingContext, never()).submitOrder(any(), any(), any(), any());
        }

        @Test
        @DisplayName("WHEN indicators trigger but the position sizer returns zero, THEN no order should be submitted.")
        void whenIndicatorsTrigger_butSizerReturnsZero_thenNoOrderSubmitted() {
            // --- ARRANGE ---
            RulesBasedStrategy strategy = new RulesBasedStrategy(
                "TestStrategy", SYMBOL, TradeDirection.LONG, List.of(indicator1), List.of(),
                positionSizer, 0 // No lookback period for this test
            );

            when(indicator1.isSignalTriggered()).thenReturn(true);
            when(tradingContext.getNetAssetValue()).thenReturn(new BigDecimal("1000"));
            when(positionSizer.calculateQuantity(any(), any())).thenReturn(BigDecimal.ZERO);

            // --- ACT ---
            strategy.onMarketEvent(DUMMY_EVENT, tradingContext);

            // --- ASSERT ---
            verify(tradingContext, never()).submitOrder(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GIVEN an open position")
    class PositionOpen {

        @Mock
        private Position openPosition;

        @BeforeEach
        void givenOpenPosition() {
            lenient().when(tradingContext.getOpenPosition(SYMBOL)).thenReturn(Optional.of(openPosition));
            lenient().when(openPosition.direction()).thenReturn(TradeDirection.LONG);
            lenient().when(openPosition.quantity()).thenReturn(BigDecimal.ONE);
        }

        @Test
        @DisplayName("WHEN an exit rule is triggered, THEN a closing order should be submitted.")
        void whenExitRuleTriggers_thenClosingOrderSubmitted() {
            // --- ARRANGE ---
            RulesBasedStrategy strategy = new RulesBasedStrategy(
                "TestStrategy",
                SYMBOL,
                TradeDirection.LONG,
                List.of(indicator1, indicator2),
                List.of(exitRule),
                positionSizer,
                0
            );
            when(exitRule.shouldExit(openPosition, DUMMY_CANDLE)).thenReturn(true);

            // --- ACT ---
            strategy.onMarketEvent(DUMMY_EVENT, tradingContext);

            // --- ASSERT ---
            verify(tradingContext, times(1)).submitOrder(SYMBOL, TradeDirection.SHORT, BigDecimal.ONE, DUMMY_CANDLE.close());
        }

        @Test
        @DisplayName("WHEN an exit rule is not triggered, THEN entry indicators should not be checked and no new order submitted.")
        void whenExitRuleIsNotTriggered_thenEntryLogicIsSkipped() {
            // --- ARRANGE ---
            RulesBasedStrategy strategy = new RulesBasedStrategy(
                "TestStrategy",
                SYMBOL,
                TradeDirection.LONG,
                List.of(indicator1, indicator2),
                List.of(exitRule),
                positionSizer,
                0
            );
            when(exitRule.shouldExit(openPosition, DUMMY_CANDLE)).thenReturn(false);

            // --- ACT ---
            strategy.onMarketEvent(DUMMY_EVENT, tradingContext);

            // --- ASSERT ---
            verify(indicator1, never()).isSignalTriggered();
            verify(indicator2, never()).isSignalTriggered();
            verify(tradingContext, never()).submitOrder(any(), any(), any(), any());
        }

        @Test
        @DisplayName("WHEN the first of two exit rules triggers, THEN a closing order is submitted and the second rule is not checked.")
        void whenFirstOfTwoExitRulesTriggers_thenOrderIsSubmittedAndLoopExits() {
            // --- ARRANGE ---
            ExitRule exitRule2 = mock(ExitRule.class);

            RulesBasedStrategy strategyWithTwoExits = new RulesBasedStrategy(
                "TestStrategy", SYMBOL, TradeDirection.LONG, List.of(),
                List.of(exitRule, exitRule2), // exitRule is the first in the list
                positionSizer, 0
            );

            when(exitRule.shouldExit(openPosition, DUMMY_CANDLE)).thenReturn(true);

            // --- ACT ---
            strategyWithTwoExits.onMarketEvent(DUMMY_EVENT, tradingContext);

            // --- ASSERT ---
            // 1. Assert that the closing order was submitted.
            verify(tradingContext, times(1)).submitOrder(SYMBOL, TradeDirection.SHORT, BigDecimal.ONE, DUMMY_CANDLE.close());

            // 2. The critical assertion: Verify that the second exit rule was never even consulted.
            verify(exitRule2, never()).shouldExit(any(), any());
        }
    }

    @Test
    @DisplayName("WHEN a market event is for a different symbol, THEN the strategy should ignore it completely.")
    void whenMarketEventIsForDifferentSymbol_thenStrategyDoesNothing() {
        // --- ARRANGE ---
        RulesBasedStrategy strategy = new RulesBasedStrategy(
            "BTCOnlyStrategy",
            "BTCUSDT", // This is the symbol the strategy listens for
            TradeDirection.LONG,
            List.of(indicator1),
            List.of(exitRule),
            positionSizer,
            0
        );

        // Create a market event for a DIFFERENT symbol.
        MarketEvent otherSymbolEvent = new MarketEvent("ETHUSDT", DUMMY_CANDLE);

        // --- ACT ---
        strategy.onMarketEvent(otherSymbolEvent, tradingContext);

        // --- ASSERT ---
        verifyNoInteractions(indicator1, exitRule, positionSizer, tradingContext);
    }
}