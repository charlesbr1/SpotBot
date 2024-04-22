package org.sbot.entities.chart;

import org.jetbrains.annotations.NotNull;
import org.sbot.utils.MutableDecimal;

import static org.sbot.utils.ArgumentValidator.*;

public record EpochMsPrice(long dateTime, long unscaledPrice, byte priceScale) {
    public EpochMsPrice {
        requireEpoch(dateTime);
        requirePositive(unscaledPrice);
    }

    @NotNull
    public MutableDecimal getPrice(@NotNull MutableDecimal priceBuffer) {
        return priceBuffer.set(unscaledPrice, priceScale);
    }
}
