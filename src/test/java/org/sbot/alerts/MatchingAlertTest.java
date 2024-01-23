package org.sbot.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.alerts.AlertTest.createTestAlert;
import static org.sbot.alerts.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.Dates.nowUtc;

class MatchingAlertTest {

    @Test
    void hasMatch() {
        assertTrue(new MatchingAlert(createTestAlert(), MATCHED, null).hasMatch());
        assertTrue(new MatchingAlert(createTestAlert(), MARGIN, null).hasMatch());
        assertFalse(new MatchingAlert(createTestAlert(), NOT_MATCHING, null).hasMatch());
    }

    @Test
    void withAlert() {
        Alert alert = createTestAlert();
        Alert otherAlert = alert.withId(() -> 1L);
        MatchingAlert matchingAlert = new MatchingAlert(alert, MATCHED, null);
        assertNotEquals(alert, matchingAlert.withAlert(otherAlert).alert());
        assertEquals(otherAlert, matchingAlert.withAlert(otherAlert).alert());
    }

    @Test
    void status() {
        assertEquals(MATCHED, new MatchingAlert(createTestAlert(), MATCHED, null).status());
        assertEquals(MARGIN, new MatchingAlert(createTestAlert(), MARGIN, null).status());
        assertEquals(NOT_MATCHING, new MatchingAlert(createTestAlert(), NOT_MATCHING, null).status());
    }

    @Test
    void matchingCandlestick() {
        Candlestick candlestick = new Candlestick(nowUtc().minusMinutes(1L), nowUtc(),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, BigDecimal.ONE);
        assertEquals(candlestick, new MatchingAlert(createTestAlert(), MATCHED, candlestick).matchingCandlestick());
    }

    @Test
    void matchingStatus() {
        assertTrue(MATCHED.isMatched());
        assertTrue(MARGIN.isMargin());
        assertTrue(NOT_MATCHING.notMatching());
    }
}