package io.github.impatient0.azero.strategy.rules.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeframeParserTest {

    @DisplayName("WHEN parsing valid timeframe strings, THEN the correct Duration should be returned.")
    @ParameterizedTest(name = "Input: {0} -> Expected: {1}")
    @MethodSource("validTimeframeProvider")
    void parse_withValidStrings_shouldReturnCorrectDuration(String input, Duration expected) {
        // --- ACT ---
        Duration actual = TimeframeParser.parse(input);

        // --- ASSERT ---
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> validTimeframeProvider() {
        return Stream.of(
            Arguments.of("1m", Duration.ofMinutes(1)),
            Arguments.of("15m", Duration.ofMinutes(15)),
            Arguments.of("1h", Duration.ofHours(1)),
            Arguments.of("4h", Duration.ofHours(4)),
            Arguments.of("1d", Duration.ofDays(1)),
            Arguments.of("1D", Duration.ofDays(1)), // Test case-insensitivity
            Arguments.of("12H", Duration.ofHours(12))  // Test multi-digit and case-insensitivity
        );
    }

    @DisplayName("WHEN parsing invalid or malformed strings, THEN an IllegalArgumentException should be thrown.")
    @ParameterizedTest(name = "Input: {0}")
    @ValueSource(strings = {"", "  ", "1y", "h1", "15 s", "1.5h", "m"})
    void parse_withInvalidStrings_shouldThrowException(String invalidInput) {
        // --- ACT & ASSERT ---
        assertThrows(IllegalArgumentException.class,
            () -> TimeframeParser.parse(invalidInput),
            "Parsing an invalid timeframe string should throw IllegalArgumentException.");
    }

    @Test
    @DisplayName("WHEN parsing a null string, THEN an IllegalArgumentException should be thrown.")
    void parse_withNullString_shouldThrowException() {
        // --- ACT & ASSERT ---
        assertThrows(IllegalArgumentException.class,
            () -> TimeframeParser.parse(null),
            "Parsing a null timeframe string should throw IllegalArgumentException.");
    }
}