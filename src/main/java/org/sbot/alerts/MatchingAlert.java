package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.chart.Candlestick;

public record MatchingAlert(@NotNull Alert alert, @NotNull MatchingStatus matchingStatus, @Nullable Candlestick matchingCandlestick) {

    public enum MatchingStatus {
        MATCHED,
        MARGIN,
        NOTHING;

        public boolean isMargin() {
            return this == MARGIN;
        }

        public boolean isNothing() {
            return this == NOTHING;
        }
    }
}
