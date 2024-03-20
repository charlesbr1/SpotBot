package org.sbot.entities.chart;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

public record DatedPrice(@NotNull BigDecimal price, @NotNull ZonedDateTime dateTime) {
    public DatedPrice {
        requireNonNull(price);
        requireNonNull(dateTime);
    }
}
