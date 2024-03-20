package org.sbot.services;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.entities.alerts.RemainderAlert;
import org.sbot.entities.alerts.TrendAlert;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.context.Context;

import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;

public final class MatchingService {

    public record MatchingAlert(@NotNull Alert alert, @NotNull MatchingStatus status, @Nullable Candlestick matchingCandlestick) {

        public boolean hasMatch() {
            return !status.notMatching();
        }

        public enum MatchingStatus {
            MATCHED,
            MARGIN,
            NOT_MATCHING;

            public boolean isMatched() {
                return this == MATCHED;
            }

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

    private final Context context;

    public MatchingService(@NotNull Context context) {
        this.context = requireNonNull(context);
    }

    public MatchingAlert match(@NotNull ZonedDateTime now, @NotNull Alert alert, @NotNull List<Candlestick> candlesticks, @Nullable Candlestick previousCandlestick) {
        return switch (alert) {
            case RangeAlert a -> a.match(candlesticks, previousCandlestick);
            case TrendAlert a -> a.match(candlesticks, previousCandlestick);
            case RemainderAlert a -> a.match(now, context.parameters().checkPeriodMin());
            default -> throw new IllegalArgumentException("Unexpected alert type : " + alert);
        };
    }
}
