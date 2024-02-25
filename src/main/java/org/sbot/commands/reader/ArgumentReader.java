package org.sbot.commands.reader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

public interface ArgumentReader {

    @NotNull
    default String getMandatoryString(@NotNull String fieldName) {
        return getString(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing text for argument '" + fieldName + '\''));
    }

    @NotNull
    default BigDecimal getMandatoryNumber(@NotNull String fieldName) {
        return getNumber(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing number for argument '" + fieldName + '\''));
    }

    default long getMandatoryLong(@NotNull String fieldName) {
        return getLong(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing integer for argument '" + fieldName + '\''));
    }

    @NotNull
    default ZonedDateTime getMandatoryDateTime(@NotNull Locale locale, @Nullable ZoneId timezone, @NotNull Clock clock, @NotNull String fieldName) {
        return getDateTime(locale, timezone, clock, fieldName).orElseThrow(() -> new IllegalArgumentException("Missing date time for argument '" + fieldName + "'\nexpected format : " + Dates.DATE_TIME_FORMAT + " UTC"));
    }

    default long getMandatoryUserId(@NotNull String fieldName) {
        return getUserId(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing user mention for argument '" + fieldName + '\''));
    }

    @NotNull
    default ArgumentReader reversed() {
        return this;
    }


    Optional<String> getString(@NotNull String fieldName);

    Optional<BigDecimal> getNumber(@NotNull String fieldName);

    Optional<Long> getLong(@NotNull String fieldName);

    Optional<ZonedDateTime> getDateTime(@NotNull Locale locale, @Nullable ZoneId timezone, @NotNull Clock clock, @NotNull String fieldName);

    Optional<LocalDateTime> getLocalDateTime(@NotNull Locale locale, @NotNull String fieldName);

    Optional<Long> getUserId(@NotNull String fieldName);

    Optional<Type> getType(@NotNull String fieldName);

    Optional<String> getLastArgs(@NotNull String fieldName);
}
