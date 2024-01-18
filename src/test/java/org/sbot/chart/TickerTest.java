package org.sbot.chart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickerTest {

    @Test
    void getSymbol() {
        assertThrows(NullPointerException.class, () -> Ticker.getSymbol(null));

        assertEquals("eth", Ticker.getSymbol("ETH"));
        assertEquals("dot", Ticker.getSymbol("DOT"));

        assertEquals("$", Ticker.getSymbol("USD"));
        assertEquals("$", Ticker.getSymbol("USDT"));
        assertEquals("$", Ticker.getSymbol("BUSD"));

        assertEquals("usdtt", Ticker.getSymbol("USDTT"));
        assertEquals("bbusd", Ticker.getSymbol("BBUSD"));
        assertEquals("busdt", Ticker.getSymbol("BUSDT"));
        assertEquals("us", Ticker.getSymbol("US"));

        assertEquals("€", Ticker.getSymbol("EUR"));
        assertEquals("eurt", Ticker.getSymbol("EURT"));
        assertEquals("teur", Ticker.getSymbol("TEUR"));

        assertEquals("¥", Ticker.getSymbol("YEN"));
        assertEquals("yent", Ticker.getSymbol("YENT"));
        assertEquals("tyen", Ticker.getSymbol("TYEN"));

        assertEquals("£", Ticker.getSymbol("GBP"));
        assertEquals("gbpt", Ticker.getSymbol("GBPT"));
        assertEquals("tgbp", Ticker.getSymbol("TGBP"));

        assertEquals("₿", Ticker.getSymbol("BTC"));
        assertEquals("btcc", Ticker.getSymbol("BTCC"));
        assertEquals("cbtc", Ticker.getSymbol("CBTC"));
    }
}
