package org.sbot.chart;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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

    @Test
    void formatPrice() {
        assertThrows(NullPointerException.class, () -> Ticker.formatPrice(null, "usd"));
        assertThrows(NullPointerException.class, () -> Ticker.formatPrice(new BigDecimal(10L), null));

        assertEquals("0 $", Ticker.formatPrice(new BigDecimal(0L), "USD"));
        assertEquals("0 $", Ticker.formatPrice(new BigDecimal(-0L), "USD"));
        assertEquals("1 €", Ticker.formatPrice(new BigDecimal(1L), "EUR"));
        assertEquals("1 test", Ticker.formatPrice(new BigDecimal(1L), "TEST"));
        assertEquals("0 $", Ticker.formatPrice(new BigDecimal("0.0"), "USD"));
        assertEquals("1 $", Ticker.formatPrice(new BigDecimal("1.0"), "USD"));
        assertEquals("-1 $", Ticker.formatPrice(new BigDecimal("-1.0"), "USD"));
        assertEquals("1.5 $", Ticker.formatPrice(new BigDecimal("1.5"), "USD"));
        assertEquals("-1.5 $", Ticker.formatPrice(new BigDecimal("-1.5"), "USD"));
        assertEquals("0.5 $", Ticker.formatPrice(new BigDecimal("0.5"), "USD"));
        assertEquals("-0.5 $", Ticker.formatPrice(new BigDecimal("-0.5"), "USD"));
        assertEquals("0.56 $", Ticker.formatPrice(new BigDecimal("0.56"), "USD"));
        assertEquals("3.56 $", Ticker.formatPrice(new BigDecimal("3.56"), "USD"));
        assertEquals("-3.56 $", Ticker.formatPrice(new BigDecimal("-3.56"), "USD"));
        assertEquals("2.57 $", Ticker.formatPrice(new BigDecimal("2.5678"), "USD"));
        assertEquals("2.56 $", Ticker.formatPrice(new BigDecimal("2.5648"), "USD"));
        assertEquals("1.567 $", Ticker.formatPrice(new BigDecimal("1.567"), "USD"));
        assertEquals("1.5678 $", Ticker.formatPrice(new BigDecimal("1.5678"), "USD"));
        assertEquals("0.5678 $", Ticker.formatPrice(new BigDecimal("0.5678"), "USD"));
        assertEquals("0.56789 $", Ticker.formatPrice(new BigDecimal("0.56789"), "USD"));
        assertEquals("0.56789 $", Ticker.formatPrice(new BigDecimal("0.567891"), "USD"));
        assertEquals("0.56789 $", Ticker.formatPrice(new BigDecimal("0.5678912345"), "USD"));
        assertEquals("4.57 $", Ticker.formatPrice(new BigDecimal("4.56789"), "USD"));
        assertEquals("0.000000000012346 $", Ticker.formatPrice(new BigDecimal("0.0000000000123456"), "USD"));
        assertEquals("-0.000000000012346 $", Ticker.formatPrice(new BigDecimal("-0.0000000000123456"), "USD"));
        assertEquals("0.000000012346 $", Ticker.formatPrice(new BigDecimal("0.0000000123456"), "USD"));
        assertEquals("0.0000012346 $", Ticker.formatPrice(new BigDecimal("0.00000123456"), "USD"));
        assertEquals("0.00012345 $", Ticker.formatPrice(new BigDecimal("0.000123454789"), "USD"));
        assertEquals("0.00012346 $", Ticker.formatPrice(new BigDecimal("0.000123456789"), "USD"));
        assertEquals("0.012346 $", Ticker.formatPrice(new BigDecimal("0.0123456789"), "USD"));
        assertEquals("0.12346 $", Ticker.formatPrice(new BigDecimal("0.123456789"), "USD"));
        assertEquals("-0.12346 $", Ticker.formatPrice(new BigDecimal("-0.123456789"), "USD"));
        assertEquals("1 $", Ticker.formatPrice(new BigDecimal("1.0000000000123456"), "USD"));
        assertEquals("-1 $", Ticker.formatPrice(new BigDecimal("-1.0000000000123456"), "USD"));
        assertEquals("2 $", Ticker.formatPrice(new BigDecimal("2.0000000000123456"), "USD"));
        assertEquals("-2 $", Ticker.formatPrice(new BigDecimal("-2.0000000000123456"), "USD"));
        assertEquals("6000123456.12 $", Ticker.formatPrice(new BigDecimal("6000123456.12345"), "USD"));
    }
}
