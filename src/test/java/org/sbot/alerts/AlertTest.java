package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertTest {

    private Alert createTestAlert() {
        return new Alert(1L, Alert.Type.range, 123L, 456L, "binance", "BTC/USD", "Test Alert",
                BigDecimal.ZERO, BigDecimal.TEN, ZonedDateTime.now(), ZonedDateTime.now().plusHours(1),
                ZonedDateTime.now().minusHours(1), BigDecimal.ONE, (short) 5, (short) 2) {
            @Override
            protected Alert build(long id, long userId, long serverId, @NotNull String exchange, @NotNull String pair, @NotNull String message, BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate, ZonedDateTime lastTrigger, BigDecimal margin, short repeat, short snooze) {
                return null;
            }

            public MatchingAlert match(@NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
                return null;
            }
            protected String asMessage(MatchingAlert.MatchingStatus matchingStatus, Candlestick previousCandlestick) {
                return "Test Message";
            }
        };
    }

    @BeforeEach
    void setUp() {
    }

    @Test
    void isPrivate() {
        assertTrue(Alert.isPrivate(Alert.PRIVATE_ALERT));
        assertFalse(Alert.isPrivate(1234L));
    }

    @Test
    void hasRepeat() {
        assertTrue(Alert.hasRepeat(10L));
        assertTrue(Alert.hasRepeat(1L));
        assertFalse(Alert.hasRepeat(0L));
        assertFalse(Alert.hasRepeat(-1L));
    }

    @Test
    void isRepeatDelayOver() {
        Alert alert = createTestAlert();
        long epochSeconds = ZonedDateTime.now().toEpochSecond();
        assertTrue(alert.isSnoozeOver(epochSeconds));
    }

    @Test
    void hasMargin() {
    }

    @Test
    void isNewerCandleStick() {
    }

    @Test
    void triggeredMessage() {
    }

    @Test
    void descriptionMessage() {
    }

    @Test
    void asMessage() {
    }

    @Test
    void header() {
    }

    @Test
    void footer() {
    }

    @Test
    void testEquals() {
    }

    @Test
    void testHashCode() {
    }

    @Test
    void testToString() {
    }
}