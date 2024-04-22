package org.sbot.entities.chart;

import org.jetbrains.annotations.NotNull;
import org.sbot.utils.MutableDecimal;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.ArgumentValidator.requireEpoch;

public record DatedPrice(@NotNull MutableDecimal price, long dateTime) {
    public DatedPrice {
        requireNonNull(price);
        requireEpoch(dateTime);
    }
}
