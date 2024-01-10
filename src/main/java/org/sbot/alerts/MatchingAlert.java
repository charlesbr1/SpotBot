package org.sbot.alerts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.chart.Candlestick;

public record MatchingAlert(@NotNull Alert alert, @NotNull MatchingStatus status, @Nullable Candlestick matchingCandlestick) {

    public boolean hasMatch() {
        return !status.notMatching();
    }

    public enum MatchingStatus {
        MATCHED,
        MARGIN,
        NOT_MATCHING;

        public boolean isMargin() {
            return this == MARGIN;
        }

        public boolean notMatching() {
            return this == NOT_MATCHING;
        }
    }

    public MatchingAlert withAlert(@NotNull Alert alert) {
        return new MatchingAlert(alert, status, matchingCandlestick);
    }
}
