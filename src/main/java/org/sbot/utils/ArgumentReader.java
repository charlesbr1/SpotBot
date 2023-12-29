package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public final class ArgumentReader {
    private String remainingArguments;

    public ArgumentReader(@NotNull String arguments) {
        this.remainingArguments = requireNonNull(arguments);
    }

    @NotNull
    public String getMandatoryString(@NotNull String fieldName) {
        return getNextString().orElseThrow(() -> new IllegalArgumentException("Missing field " + fieldName));
    }

    @NotNull
    public BigDecimal getMandatoryNumber(@NotNull String fieldName) {
        return getNextNumber().orElseThrow(() -> new IllegalArgumentException("Missing figure " + fieldName));
    }

    public long getMandatoryLong(@NotNull String fieldName) {
        return getNextLong().orElseThrow(() -> new IllegalArgumentException("Missing number " + fieldName));
    }

    @NotNull
    public ZonedDateTime getMandatoryDateTime(@NotNull String fieldName) {
        return getNextDateTime().orElseThrow(() -> new IllegalArgumentException("Missing date " + fieldName));
    }

    public Optional<String> getNextString() {
        List<String> values = !remainingArguments.isBlank() ?
                // this split arguments into two parts : the first word without spaces, then the rest of the string
                Arrays.asList(remainingArguments.split("\\s+", 2))
                : Collections.emptyList();
        remainingArguments = values.size() > 1 ? values.get(1) : "";
        return values.stream().findFirst();
    }

    public Optional<BigDecimal> getNextNumber() {
        return getNext(BigDecimal::new);
    }

    public Optional<Long> getNextLong() {
        return getNext(Long::parseLong);
    }

    public Optional<ZonedDateTime> getNextDateTime() {
        return getNext(Dates::parseUTC);
    }

    private <U> Optional<U> getNext(@NotNull Function<? super String, ? extends U> mapper) {
        String arguments = this.remainingArguments;
        try {
            return getNextString().map(mapper);
        } catch (RuntimeException e) {
            this.remainingArguments = arguments; // restore previous state TODO doc
            return Optional.empty();
        }
    }

    @NotNull
    public String getRemaining() {
        return remainingArguments;
    }
}
