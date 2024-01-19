package org.sbot.services.dao.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;

import java.time.ZonedDateTime;

import static java.math.BigDecimal.*;
import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.*;

class LastCandlesticksMemoryTest {

    private LastCandlesticksDao lastCandlesticks;

    @BeforeEach
    void init() {
        lastCandlesticks = new LastCandlesticksMemory();
    }

    @Test
    void getLastCandlestickCloseTime() {
        assertTrue(lastCandlesticks.getLastCandlestickCloseTime("ETH/BTC").isEmpty());
        ZonedDateTime closeTime = ZonedDateTime.now().plusMinutes(23L);
        Candlestick candlestick = new Candlestick(now(), closeTime, ONE, ONE, ONE, ONE);
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        assertEquals(closeTime, lastCandlesticks.getLastCandlestickCloseTime("ETH/BTC").get());

    }

    @Test
    void getLastCandlestick() {
        Candlestick candlestick = new Candlestick(now(), now(), ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isEmpty());
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isPresent());
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
    }

    @Test
    void setLastCandlestick() {
        Candlestick candlestick = new Candlestick(now(), now(), ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isEmpty());
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick("BTC/ETH").isEmpty());
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isPresent());
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
    }

    @Test
    void updateLastCandlestick() {
        Candlestick candlestick = new Candlestick(now(), now(), ONE, ONE, ONE, ONE);
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        lastCandlesticks.setLastCandlestick("DOT/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isPresent());
        assertTrue(lastCandlesticks.getLastCandlestick("DOT/BTC").isPresent());
        Candlestick newCandlestick = new Candlestick(now(), now(), TWO, TWO, TWO, TWO);
        assertNotEquals(candlestick, newCandlestick);
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
        lastCandlesticks.updateLastCandlestick("ETH/BTC", newCandlestick);
        assertEquals(newCandlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
        newCandlestick = new Candlestick(now(), now(), TEN, TEN, TEN, TEN);
        lastCandlesticks.updateLastCandlestick("DOT/BTC", newCandlestick);
        assertNotEquals(newCandlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
        assertEquals(newCandlestick, lastCandlesticks.getLastCandlestick("DOT/BTC").get());
    }
}