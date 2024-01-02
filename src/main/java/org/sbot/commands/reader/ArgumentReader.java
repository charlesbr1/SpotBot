package org.sbot.commands.reader;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface ArgumentReader {

    @NotNull
    String getMandatoryString(@NotNull String fieldName);

    @NotNull
    BigDecimal getMandatoryNumber(@NotNull String fieldName);

    long getMandatoryLong(@NotNull String fieldName);

    @NotNull
    ZonedDateTime getMandatoryDateTime(@NotNull String fieldName);

    long getMandatoryUserId(@NotNull String fieldName);

    Optional<String> getString(@NotNull String fieldName);

    Optional<BigDecimal> getNumber(@NotNull String fieldName);

    Optional<Long> getLong(@NotNull String fieldName);

    Optional<ZonedDateTime> getDateTime(@NotNull String fieldName);

    Optional<Long> getUserId(@NotNull String fieldName);

    Optional<String> getLastArgs(@NotNull String fieldName);
}
