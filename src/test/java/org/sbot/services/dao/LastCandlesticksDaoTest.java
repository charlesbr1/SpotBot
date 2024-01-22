package org.sbot.services.dao;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.memory.LastCandlesticksMemory;
import org.sbot.services.dao.sql.LastCandlesticksSQLite;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.math.BigDecimal.*;
import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.*;

class LastCandlesticksDaoTest {

    static Stream<Arguments> provideDao() {
        return Stream.of(
//                Arguments.of("memory"),
                Arguments.of("sqlite"));
    }

    private static class MyError extends Error {}
    private void withDao(String daoName, Consumer<LastCandlesticksDao> test) {
        LastCandlesticksDao dao = "sqlite".equals(daoName) ?
                new LastCandlesticksSQLite(new JDBIRepository("jdbc:sqlite::memory:?DB_CLOSE_DELAY=-1")) :
                new LastCandlesticksMemory();
        try {
            dao.transactional(() -> {
                test.accept(dao);
                System.err.println("WILL ROLLBACK");
                throw new MyError();
            });
        } catch (MyError e) {
            System.err.println("ROLLBACK HERE");
            ;
        } finally {
            if(dao instanceof LastCandlesticksSQLite)
;//                dao.transactional(() -> ((LastCandlesticksSQLite) dao).getRepository()
   //                 .getHandle().execute("DROP TABLE last_candlesticks"));
        }
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLastCandlestickCloseTime(String dao) {
        withDao(dao, this::getLastCandlestickCloseTime);
    }

    void getLastCandlestickCloseTime(LastCandlesticksDao lastCandlesticks) {
        assertTrue(lastCandlesticks.getLastCandlestickCloseTime("ETH/BTC").isEmpty());
        ZonedDateTime closeTime = LocalDateTime.now().atZone(ZoneOffset.UTC).withNano(0) // clear the seconds as sqlite save milliseconds and not nanos
                .plusMinutes(23L);
        Candlestick candlestick = new Candlestick(now(), closeTime, ONE, ONE, ONE, ONE);
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        assertEquals(closeTime, lastCandlesticks.getLastCandlestickCloseTime("ETH/BTC").get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getLastCandlestick(String dao) {
        withDao(dao, this::getLastCandlestick);
    }

    void getLastCandlestick(LastCandlesticksDao lastCandlesticks) {
        ZonedDateTime now = LocalDateTime.now().atZone(ZoneOffset.UTC).withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isEmpty());
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isPresent());
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void setLastCandlestick(String dao) {
        withDao(dao, this::setLastCandlestick);
    }

    void setLastCandlestick(LastCandlesticksDao lastCandlesticks) {
        ZonedDateTime now = LocalDateTime.now().atZone(ZoneOffset.UTC).withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isEmpty());
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick("BTC/ETH").isEmpty());
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isPresent());
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void updateLastCandlestick(String dao) {
        withDao(dao, this::updateLastCandlestick);
    }

    void updateLastCandlestick(LastCandlesticksDao lastCandlesticks) {
        ZonedDateTime now = LocalDateTime.now().atZone(ZoneOffset.UTC).withNano(0); // clear the seconds as sqlite save milliseconds and not nanos
        Candlestick candlestick = new Candlestick(now, now, ONE, ONE, ONE, ONE);
        lastCandlesticks.setLastCandlestick("ETH/BTC", candlestick);
        lastCandlesticks.setLastCandlestick("DOT/BTC", candlestick);
        assertTrue(lastCandlesticks.getLastCandlestick("ETH/BTC").isPresent());
        assertTrue(lastCandlesticks.getLastCandlestick("DOT/BTC").isPresent());
        Candlestick newCandlestick = new Candlestick(now, now, TWO, TWO, TWO, TWO);
        assertNotEquals(candlestick, newCandlestick);
        assertEquals(candlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
        lastCandlesticks.updateLastCandlestick("ETH/BTC", newCandlestick);
        assertEquals(newCandlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
        newCandlestick = new Candlestick(now, now, TEN, TEN, TEN, TEN);
        lastCandlesticks.updateLastCandlestick("DOT/BTC", newCandlestick);
        assertNotEquals(newCandlestick, lastCandlesticks.getLastCandlestick("ETH/BTC").get());
        assertEquals(newCandlestick, lastCandlesticks.getLastCandlestick("DOT/BTC").get());
    }
}