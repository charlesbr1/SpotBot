package org.sbot.services.dao.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.services.dao.LastCandlesticksDaoTest;
import org.sbot.services.dao.memory.LastCandlesticksMemory.LastCandlestickId;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LastCandlesticksMemoryTest extends LastCandlesticksDaoTest {

    public static Stream<Arguments> provideDao() {
        return Stream.of(Arguments.of(new LastCandlesticksMemory()));
    }

    @Test
    void lastCandlestickId() {
        assertDoesNotThrow(() -> LastCandlestickId.id(BinanceClient.NAME, "ETH/USD"));
        assertThrows(NullPointerException.class, () -> LastCandlestickId.id(BinanceClient.NAME, null));
        assertThrows(NullPointerException.class, () -> LastCandlestickId.id(null, "ETH/USD"));
        assertThrows(IllegalArgumentException.class, () -> LastCandlestickId.id("bad exchange", "ETH/USD"));
        assertThrows(IllegalArgumentException.class, () -> LastCandlestickId.id(BinanceClient.NAME, "pair"));
    }
}