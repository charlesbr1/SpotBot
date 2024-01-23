package org.sbot.chart;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.ArgumentValidator.requireInPast;
import static org.sbot.utils.Dates.nowUtc;

public record Candlestick(@NotNull ZonedDateTime openTime, @NotNull ZonedDateTime closeTime,
                          @NotNull BigDecimal open, @NotNull BigDecimal close,
                          @NotNull BigDecimal high, @NotNull BigDecimal low) {

    public Candlestick {
        requireNonNull(openTime, "missing Candlestick openTime");
        requireNonNull(closeTime, "missing Candlestick closeTime");
        if(openTime.compareTo(closeTime) > 0) {
            throw new IllegalArgumentException("openTime is after closeTime");
        }
        requireNonNull(open, "missing Candlestick open");
        requireNonNull(close, "missing Candlestick close");
        requireNonNull(high, "missing Candlestick high");
        requireNonNull(low, "missing Candlestick low");
        if(low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low is higher than high");
        }
    }


    public record CandlestickPeriod(int daily, int hourly, int minutes) {
        public static final CandlestickPeriod ONE_MINUTE = new CandlestickPeriod(0, 0, 1);
    }

    // returns the number of candlestick to retrieve to get quotes since last time
    // number of days
    // number of hours
    //
    @NotNull
    public static CandlestickPeriod periodSince(@NotNull ZonedDateTime lastTime) {
        Duration duration = Duration.between(requireInPast(lastTime), nowUtc());
        int days = (int) duration.toDaysPart();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        days = days > 0 ? days + 1 : days;
        hours = hours > 0 ? hours + 1 : hours;
        minutes = hours > 0 ? 60 : minutes + 1;
        return new CandlestickPeriod(days, hours, minutes);
    }
}
