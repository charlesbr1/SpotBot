package org.sbot.chart;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record Candlestick(ZonedDateTime openTime, ZonedDateTime closeTime, BigDecimal open, BigDecimal close, BigDecimal high, BigDecimal low) {
}
