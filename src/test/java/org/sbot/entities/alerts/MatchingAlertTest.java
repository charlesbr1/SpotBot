package org.sbot.entities.alerts;

import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.MatchingService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.AlertTest.createTestAlert;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.DatesTest.nowUtc;

class MatchingAlertTest {

    @Test
    void hasMatch() {
        assertTrue(new MatchingService.MatchingAlert(createTestAlert(), MATCHED, null).hasMatch());
        assertTrue(new MatchingService.MatchingAlert(createTestAlert(), MARGIN, null).hasMatch());
        assertFalse(new MatchingService.MatchingAlert(createTestAlert(), NOT_MATCHING, null).hasMatch());
    }

    @Test
    void withAlert() {
        Alert alert = createTestAlert();
        Alert otherAlert = alert.withId(() -> 1L);
        MatchingService.MatchingAlert matchingAlert = new MatchingService.MatchingAlert(alert, MATCHED, null);
        assertNotEquals(alert, matchingAlert.withAlert(otherAlert).alert());
        assertEquals(otherAlert, matchingAlert.withAlert(otherAlert).alert());
    }

    @Test
    void status() {
        assertEquals(MATCHED, new MatchingService.MatchingAlert(createTestAlert(), MATCHED, null).status());
        assertEquals(MARGIN, new MatchingService.MatchingAlert(createTestAlert(), MARGIN, null).status());
        assertEquals(NOT_MATCHING, new MatchingService.MatchingAlert(createTestAlert(), NOT_MATCHING, null).status());
    }

    @Test
    void matchingCandlestick() {
        Candlestick candlestick = new Candlestick(nowUtc().minusMinutes(1L), nowUtc(),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, BigDecimal.ONE);
        assertEquals(candlestick, new MatchingService.MatchingAlert(createTestAlert(), MATCHED, candlestick).matchingCandlestick());
    }

    @Test
    void matchingStatus() {
        assertTrue(MATCHED.isMatched());
        assertTrue(MARGIN.isMargin());
        assertTrue(NOT_MATCHING.notMatching());
    }
}