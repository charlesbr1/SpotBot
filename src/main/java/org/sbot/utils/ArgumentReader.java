package org.sbot.utils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class ArgumentReader {
    private String remainingArguments;

    public ArgumentReader(String arguments) {
        this.remainingArguments = arguments;
    }

    public String getMandatoryString(String fieldName) {
        return getNextString().orElseThrow(() -> new IllegalArgumentException("Missing field " + fieldName));
    }

    public BigDecimal getMandatoryNumber(String fieldName) {
        return getNextNumber().orElseThrow(() -> new IllegalArgumentException("Missing figure " + fieldName));
    }

    public long getMandatoryLong(String fieldName) {
        return getNextLong().orElseThrow(() -> new IllegalArgumentException("Missing number " + fieldName));
    }

    public ZonedDateTime getMandatoryDateTime(String fieldName) {
        return getNextDateTime().orElseThrow(() -> new IllegalArgumentException("Missing date " + fieldName));
    }

    public Optional<String> getNextString() {
        List<String> values = null != remainingArguments ?
                // this split arguments into two parts : the first word without spaces, then the rest of the string
                Arrays.asList(remainingArguments.split("\\s+", 2))
                : Collections.emptyList();
        remainingArguments = values.size() > 1 ? values.get(1) : null;
        return values.stream().findFirst();
    }

    public Optional<BigDecimal> getNextNumber() {
        return getNext(BigDecimal::new);
    }

    public Optional<Long> getNextLong() {
        return getNext(Long::parseLong);
    }

    public Optional<ZonedDateTime> getNextDateTime() {
        return getNext(ZonedDateTime::parse);
    }

    private <U> Optional<U> getNext(Function<? super String, ? extends U> mapper) {
        String arguments = this.remainingArguments;
        try {
            return getNextString().map(mapper);
        } catch (RuntimeException e) {
            this.remainingArguments = arguments; // restore previous state TODO doc
            return Optional.empty();
        }
    }

    public Optional<String> getRemaining() {
        return Optional.ofNullable(remainingArguments);
    }
}
