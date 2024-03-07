package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThrows(NullPointerException.class, () -> Tickers.formatPrice(new BigDecimal(10L), null));

        assertEquals("0 $", Tickers.formatPrice(new BigDecimal(0L), "USD"));
        assertEquals("0 $", Tickers.formatPrice(new BigDecimal(-0L), "USD"));
        assertEquals("1 €", Tickers.formatPrice(new BigDecimal(1L), "EUR"));
        assertEquals("1 test", Tickers.formatPrice(new BigDecimal(1L), "TEST"));
        assertEquals("0 $", Tickers.formatPrice(new BigDecimal("0.0"), "USD"));
        assertEquals("1 $", Tickers.formatPrice(new BigDecimal("1.0"), "USD"));
        assertEquals("-1 $", Tickers.formatPrice(new BigDecimal("-1.0"), "USD"));
        assertEquals("1.5 $", Tickers.formatPrice(new BigDecimal("1.5"), "USD"));
        assertEquals("-1.5 $", Tickers.formatPrice(new BigDecimal("-1.5"), "USD"));
        assertEquals("0.5 $", Tickers.formatPrice(new BigDecimal("0.5"), "USD"));
        assertEquals("-0.5 $", Tickers.formatPrice(new BigDecimal("-0.5"), "USD"));
        assertEquals("0.56 $", Tickers.formatPrice(new BigDecimal("0.56"), "USD"));
        assertEquals("3.56 $", Tickers.formatPrice(new BigDecimal("3.56"), "USD"));
        assertEquals("-3.56 $", Tickers.formatPrice(new BigDecimal("-3.56"), "USD"));
        assertEquals("2.57 $", Tickers.formatPrice(new BigDecimal("2.5678"), "USD"));
        assertEquals("2.56 $", Tickers.formatPrice(new BigDecimal("2.5648"), "USD"));
        assertEquals("1.567 $", Tickers.formatPrice(new BigDecimal("1.567"), "USD"));
        assertEquals("1.5678 $", Tickers.formatPrice(new BigDecimal("1.5678"), "USD"));
        assertEquals("0.5678 $", Tickers.formatPrice(new BigDecimal("0.5678"), "USD"));
        assertEquals("0.56789 $", Tickers.formatPrice(new BigDecimal("0.56789"), "USD"));
        assertEquals("0.56789 $", Tickers.formatPrice(new BigDecimal("0.567891"), "USD"));
        assertEquals("0.56789 $", Tickers.formatPrice(new BigDecimal("0.5678912345"), "USD"));
        assertEquals("4.57 $", Tickers.formatPrice(new BigDecimal("4.56789"), "USD"));
        assertEquals("0.000000000012346 $", Tickers.formatPrice(new BigDecimal("0.0000000000123456"), "USD"));
        assertEquals("-0.000000000012346 $", Tickers.formatPrice(new BigDecimal("-0.0000000000123456"), "USD"));
        assertEquals("0.000000012346 $", Tickers.formatPrice(new BigDecimal("0.0000000123456"), "USD"));
        assertEquals("0.0000012346 $", Tickers.formatPrice(new BigDecimal("0.00000123456"), "USD"));
        assertEquals("0.00012345 $", Tickers.formatPrice(new BigDecimal("0.000123454789"), "USD"));
        assertEquals("0.00012346 $", Tickers.formatPrice(new BigDecimal("0.000123456789"), "USD"));
        assertEquals("0.012346 $", Tickers.formatPrice(new BigDecimal("0.0123456789"), "USD"));
        assertEquals("0.12346 $", Tickers.formatPrice(new BigDecimal("0.123456789"), "USD"));
        assertEquals("-0.12346 $", Tickers.formatPrice(new BigDecimal("-0.123456789"), "USD"));
        assertEquals("1 $", Tickers.formatPrice(new BigDecimal("1.0000000000123456"), "USD"));
        assertEquals("-1 $", Tickers.formatPrice(new BigDecimal("-1.0000000000123456"), "USD"));
        assertEquals("2 $", Tickers.formatPrice(new BigDecimal("2.0000000000123456"), "USD"));
        assertEquals("-2 $", Tickers.formatPrice(new BigDecimal("-2.0000000000123456"), "USD"));
        assertEquals("6000123456.12 $", Tickers.formatPrice(new BigDecimal("6000123456.12345"), "USD"));
    }
}
