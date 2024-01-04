package org.sbot.commands.reader;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface ArgumentReader {

    @NotNull
    default String getMandatoryString(@NotNull String fieldName) {
        return getString(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing field '" + fieldName + '\''));
    }

    @NotNull
    default BigDecimal getMandatoryNumber(@NotNull String fieldName) {
        return getNumber(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing figure '" + fieldName + '\''));
    }

    default long getMandatoryLong(@NotNull String fieldName) {
        return getLong(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing number '" + fieldName + '\''));
    }

    @NotNull
    default ZonedDateTime getMandatoryDateTime(@NotNull String fieldName) {
        return getDateTime(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing date '" + fieldName + '\''));
    }

    default long getMandatoryUserId(@NotNull String fieldName) {
        return getUserId(fieldName).orElseThrow(() -> new IllegalArgumentException("Missing user mention '" + fieldName + '\''));
    }


    Optional<String> getString(@NotNull String fieldName);

    Optional<BigDecimal> getNumber(@NotNull String fieldName);

    Optional<Long> getLong(@NotNull String fieldName);

    Optional<ZonedDateTime> getDateTime(@NotNull String fieldName);

    Optional<Long> getUserId(@NotNull String fieldName);

    Optional<String> getLastArgs(@NotNull String fieldName);
}
