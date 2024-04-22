package org.sbot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.utils.MutableDecimal.ImmutableDecimal.ZERO;
import static org.sbot.utils.MutableDecimalTest.TEN;

class TickersTest {

    @Test
    void getSymbol() {
        assertThrows(NullPointerException.class, () -> Tickers.getSymbol(null));

        assertEquals("eth", Tickers.getSymbol("ETH"));
        assertEquals("dot", Tickers.getSymbol("DOT"));

        assertEquals("$", Tickers.getSymbol("USD"));
        assertEquals("$", Tickers.getSymbol("USDT"));
        assertEquals("$", Tickers.getSymbol("BUSD"));

        assertEquals("usdtt", Tickers.getSymbol("USDTT"));
        assertEquals("bbusd", Tickers.getSymbol("BBUSD"));
        assertEquals("busdt", Tickers.getSymbol("BUSDT"));
        assertEquals("us", Tickers.getSymbol("US"));

        assertEquals("€", Tickers.getSymbol("EUR"));
        assertEquals("eurt", Tickers.getSymbol("EURT"));
        assertEquals("teur", Tickers.getSymbol("TEUR"));

        assertEquals("¥", Tickers.getSymbol("YEN"));
        assertEquals("yent", Tickers.getSymbol("YENT"));
        assertEquals("tyen", Tickers.getSymbol("TYEN"));

        assertEquals("£", Tickers.getSymbol("GBP"));
        assertEquals("gbpt", Tickers.getSymbol("GBPT"));
        assertEquals("tgbp", Tickers.getSymbol("TGBP"));

        assertEquals("₿", Tickers.getSymbol("BTC"));
        assertEquals("btcc", Tickers.getSymbol("BTCC"));
        assertEquals("cbtc", Tickers.getSymbol("CBTC"));
    }

    @Test
    void formatPrice() {
        assertThrows(NullPointerException.class, () -> Tickers.formatPrice(null, "usd"));
        assertThrows(NullPointerException.class, () -> Tickers.formatPrice(TEN, null));

        assertEquals("0 $", Tickers.formatPrice(ZERO, "USD"));
        assertEquals("0 $", Tickers.formatPrice(MutableDecimal.of(-0L, (byte) 0), "USD"));
        assertEquals("1 €", Tickers.formatPrice(MutableDecimal.of(1L, (byte) 0), "EUR"));
        assertEquals("1 test", Tickers.formatPrice(MutableDecimal.of(1L, (byte) 0), "TEST"));
        assertEquals("0 $", Tickers.formatPrice(MutableDecimal.of(0L, (byte) 1), "USD"));
        assertEquals("1 $", Tickers.formatPrice(MutableDecimal.of(10L, (byte) 1), "USD"));
        assertEquals("-1 $", Tickers.formatPrice(MutableDecimal.of(-10L, (byte) 1), "USD"));
        assertEquals("1.5 $", Tickers.formatPrice(MutableDecimal.of(15L, (byte) 1), "USD"));
        assertEquals("-1.5 $", Tickers.formatPrice(MutableDecimal.of(-15L, (byte) 1), "USD"));
        assertEquals("0.5 $", Tickers.formatPrice(MutableDecimal.of(5L, (byte) 1), "USD"));
        assertEquals("-0.5 $", Tickers.formatPrice(MutableDecimal.of(-5L, (byte) 1), "USD"));
        assertEquals("0.56 $", Tickers.formatPrice(MutableDecimal.of(56L, (byte) 2), "USD"));
        assertEquals("3.56 $", Tickers.formatPrice(MutableDecimal.of(356L, (byte) 2), "USD"));
        assertEquals("-3.56 $", Tickers.formatPrice(MutableDecimal.of(-356L, (byte) 2), "USD"));
        assertEquals("2.57 $", Tickers.formatPrice(MutableDecimal.of(25678L, (byte) 4), "USD"));
        assertEquals("2.56 $", Tickers.formatPrice(MutableDecimal.of(25648L, (byte) 4), "USD"));
        assertEquals("1.567 $", Tickers.formatPrice(MutableDecimal.of(1567L, (byte) 3), "USD"));
        assertEquals("1.5678 $", Tickers.formatPrice(MutableDecimal.of(15678L, (byte) 4), "USD"));
        assertEquals("0.5678 $", Tickers.formatPrice(MutableDecimal.of(5678L, (byte) 4), "USD"));
        assertEquals("0.56789 $", Tickers.formatPrice(MutableDecimal.of(56789L, (byte) 5), "USD"));
        assertEquals("0.56789 $", Tickers.formatPrice(MutableDecimal.of(567891L, (byte) 6), "USD"));
        assertEquals("0.56789 $", Tickers.formatPrice(MutableDecimal.of(5678912345L, (byte) 10), "USD"));
        assertEquals("4.57 $", Tickers.formatPrice(MutableDecimal.of(456789L, (byte) 5), "USD"));
        assertEquals("0.000000000012346 $", Tickers.formatPrice(MutableDecimal.of(123456L, (byte) 16), "USD"));
        assertEquals("-0.000000000012346 $", Tickers.formatPrice(MutableDecimal.of(-123456L, (byte) 16), "USD"));
        assertEquals("0.000000012346 $", Tickers.formatPrice(MutableDecimal.of(123456L, (byte) 13), "USD"));
        assertEquals("0.0000012346 $", Tickers.formatPrice(MutableDecimal.of(123456L, (byte) 11), "USD"));
        assertEquals("0.00012345 $", Tickers.formatPrice(MutableDecimal.of(123454789L, (byte) 12), "USD"));
        assertEquals("0.00012346 $", Tickers.formatPrice(MutableDecimal.of(123456789L, (byte) 12), "USD"));
        assertEquals("0.012346 $", Tickers.formatPrice(MutableDecimal.of(123456789L, (byte) 10), "USD"));
        assertEquals("0.12346 $", Tickers.formatPrice(MutableDecimal.of(123456789L, (byte) 9), "USD"));
        assertEquals("-0.12346 $", Tickers.formatPrice(MutableDecimal.of(-123456789L, (byte) 9), "USD"));
        assertEquals("1 $", Tickers.formatPrice(MutableDecimal.of(10000000000123456L, (byte) 16), "USD"));
        assertEquals("-1 $", Tickers.formatPrice(MutableDecimal.of(-10000000000123456L, (byte) 16), "USD"));
        assertEquals("2 $", Tickers.formatPrice(MutableDecimal.of(20000000000123456L, (byte) 16), "USD"));
        assertEquals("-2 $", Tickers.formatPrice(MutableDecimal.of(-20000000000123456L, (byte) 16), "USD"));
        assertEquals("6000123456.12 $", Tickers.formatPrice(MutableDecimal.of(600012345612345L, (byte) 5), "USD"));
    }
}
