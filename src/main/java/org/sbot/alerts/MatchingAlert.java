package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.chart.Candlestick;

public record MatchingAlert(@NotNull Alert alert, @NotNull MatchingStatus status, @Nullable Candlestick matchingCandlestick) {

    public boolean hasMatch() {
        return !status.noTrigger();
    }

    public enum MatchingStatus {
        MATCHED,
        MARGIN,
        NO_TRIGGER;

        public boolean isMargin() {
            return this == MARGIN;
        }

        public boolean noTrigger() {
            return this == NO_TRIGGER;
        }
    }
}
