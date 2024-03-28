package org.sbot.services.dao;

import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.chart.Candlestick;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import static java.math.BigDecimal.*;
import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.utils.DatesTest.nowUtc;

public abstract class LastCandlesticksDaoTest {

    @ParameterizedTest
    @MethodSource("provideDao")
    void getPairsByExchanges(LastCandlesticksDao lastCandlesticks) {
        ZonedDateTime now = nowUtc();
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getPairsByExchanges().isEmpty());
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        assertFalse(lastCandlesticks.getPairsByExchanges().isEmpty());
        assertEquals(1, lastCandlesticks.getPairsByExchanges().size());
        assertEquals(1, lastCandlesticks.getPairsByExchanges().get(BinanceClient.NAME).size());
        assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC")), lastCandlesticks.getPairsByExchanges());

        var exClass = lastCandlesticks instanceof LastCandlesticksSQLite ?
                UnableToExecuteStatementException.class : IllegalArgumentException.class;
        assertThrows(exClass, () -> lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick));

        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "DOT/BTC", candlestick);
        assertEquals(1, lastCandlesticks.getPairsByExchanges().size());
        assertEquals(2, lastCandlesticks.getPairsByExchanges().get(BinanceClient.NAME).size());
        assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC", "DOT/BTC")), lastCandlesticks.getPairsByExchanges());
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "BNB/USDT", candlestick);
        assertEquals(1, lastCandlesticks.getPairsByExchanges().size());
        assertEquals(3, lastCandlesticks.getPairsByExchanges().get(BinanceClient.NAME).size());
        assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC", "DOT/BTC", "BNB/USDT")), lastCandlesticks.getPairsByExchanges());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLastCandlestickCloseTime(LastCandlesticksDao lastCandlesticks) {
        assertThrows(NullPointerException.class, () -> lastCandlesticks.getLastCandlestickCloseTime(null, "ETH/BTC"));
        assertThrows(NullPointerException.class, () -> lastCandlesticks.getLastCandlestickCloseTime(BinanceClient.NAME, null));

        assertTrue(lastCandlesticks.getLastCandlestickCloseTime(BinanceClient.NAME, "ETH/BTC").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> lastCandlesticks.getLastCandlestickCloseTime("bad exchange", "ETH/BTC"));
        ZonedDateTime closeTime = nowUtc().withNano(0) // clear the seconds as sqlite save milliseconds and not nanos
                .plusMinutes(23L);
        Candlestick candlestick = new Candlestick(now(), closeTime, ONE, ONE, ONE, ONE);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        assertEquals(closeTime, lastCandlesticks.getLastCandlestickCloseTime(BinanceClient.NAME, "ETH/BTC").get());
        assertTrue(lastCandlesticks.getLastCandlestickCloseTime(BinanceClient.NAME, "DOT/BTC").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> lastCandlesticks.getLastCandlestickCloseTime("bad exchange", "ETH/BTC"));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLastCandlestick(LastCandlesticksDao lastCandlesticks) {
        assertThrows(NullPointerException.class, () -> lastCandlesticks.getLastCandlestick(null, "ETH/BTC"));
        assertThrows(NullPointerException.class, () -> lastCandlesticks.getLastCandlestick(BinanceClient.NAME, null));
        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> lastCandlesticks.getLastCandlestick("bad exchange", "ETH/BTC"));
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").isPresent());
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").get());
        assertThrows(IllegalArgumentException.class, () -> lastCandlesticks.getLastCandlestick("bad exchange", "ETH/BTC"));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void setLastCandlestick(LastCandlesticksDao lastCandlesticks) {
        assertThrows(NullPointerException.class, () -> lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", null));
        assertThrows(NullPointerException.class, () -> lastCandlesticks.setLastCandlestick(BinanceClient.NAME, null, mock()));
        assertThrows(NullPointerException.class, () -> lastCandlesticks.setLastCandlestick(null, "ETH/BTC", mock()));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").isEmpty());
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "BTC/ETH").isEmpty());
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").isPresent());
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLastCandlestick(LastCandlesticksDao lastCandlesticks) {
        assertThrows(NullPointerException.class, () -> lastCandlesticks.updateLastCandlestick(BinanceClient.NAME, "ETH/BTC", null));
        assertThrows(NullPointerException.class, () -> lastCandlesticks.updateLastCandlestick(BinanceClient.NAME, null, mock()));
        assertThrows(NullPointerException.class, () -> lastCandlesticks.updateLastCandlestick(null, "ETH/BTC", mock()));

        ZonedDateTime now = nowUtc().withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "DOT/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").isPresent());
        assertTrue(lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "DOT/BTC").isPresent());
        Candlestick newCandlestick = new Candlestick(now, now, TWO, TWO, TWO, TWO);
        assertNotEquals(candlestick, newCandlestick);
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").get());
        lastCandlesticks.updateLastCandlestick(BinanceClient.NAME, "ETH/BTC", newCandlestick);
        assertEquals(newCandlestick, lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").get());
        newCandlestick = new Candlestick(now, now, TEN, TEN, TEN, TEN);
        lastCandlesticks.updateLastCandlestick(BinanceClient.NAME, "DOT/BTC", newCandlestick);
        assertNotEquals(newCandlestick, lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "ETH/BTC").get());
        assertEquals(newCandlestick, lastCandlesticks.getLastCandlestick(BinanceClient.NAME, "DOT/BTC").get());
    }


    @ParameterizedTest
    @MethodSource("provideDao")
    void delete(LastCandlesticksDao lastCandlesticks) {
        assertThrows(NullPointerException.class, () -> lastCandlesticks.delete(null));

        ZonedDateTime now = nowUtc();
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getPairsByExchanges().isEmpty());
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        assertFalse(lastCandlesticks.getPairsByExchanges().isEmpty());
        assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC")), lastCandlesticks.getPairsByExchanges());
        lastCandlesticks.delete(deleter -> deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "ETH/DOT")));
        assertFalse(lastCandlesticks.getPairsByExchanges().isEmpty());
        assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC")), lastCandlesticks.getPairsByExchanges());
        if(lastCandlesticks instanceof LastCandlesticksMemory) {
            assertThrows(IllegalArgumentException.class, () -> lastCandlesticks.delete(deleter -> deleter.batch(Map.of("exchange", "bad exchange", "pair", "ETH/BTC"))));
            assertThrows(IllegalArgumentException.class, () -> lastCandlesticks.delete(deleter -> deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "bad pairETH/BTC"))));
        } else { // LastCandlesticksSQLite batch delete does not perform checks on arguments, this difference is solved into service layer LastCandlesticksService
            lastCandlesticks.delete(deleter -> deleter.batch(Map.of("exchange", "bad exchange", "pair", "ETH/BTC")));
            assertFalse(lastCandlesticks.getPairsByExchanges().isEmpty());
            assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC")), lastCandlesticks.getPairsByExchanges());
        }
        lastCandlesticks.delete(deleter -> deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "ETH/BTC")));
        assertTrue(lastCandlesticks.getPairsByExchanges().isEmpty());

        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "ETH/BTC", candlestick);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "DOT/BTC", candlestick);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "BNB/USDT", candlestick);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "EUR/USD", candlestick);
        lastCandlesticks.setLastCandlestick(BinanceClient.NAME, "CAN/USD", candlestick);

        assertEquals(Map.of(BinanceClient.NAME, Set.of("ETH/BTC", "DOT/BTC", "BNB/USDT", "EUR/USD", "CAN/USD")), lastCandlesticks.getPairsByExchanges());
        lastCandlesticks.delete(deleter -> {
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "ETH/BTC"));
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "EUR/USD"));
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "SOT/ABS"));
        });
        assertEquals(Map.of(BinanceClient.NAME, Set.of("DOT/BTC", "BNB/USDT", "CAN/USD")), lastCandlesticks.getPairsByExchanges());
        assertFalse(lastCandlesticks.getPairsByExchanges().isEmpty());
        lastCandlesticks.delete(deleter -> {
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "ETH/BTC"));
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "DOT/BTC"));
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "BNB/USDT"));
            deleter.batch(Map.of("exchange", BinanceClient.NAME, "pair", "CAN/USD"));
        });
        assertTrue(lastCandlesticks.getPairsByExchanges().isEmpty());
    }
}