package org.sbot.commands.reader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.function.Predicate.not;

public final class StringArgumentReader implements ArgumentReader {

    private static final Pattern SPLIT_WORD = Pattern.compile("\\s+");
    private static final Pattern REVERSE_SPLIT_WORD = Pattern.compile("\\s+(?=\\S+$)");

    @NotNull
    private String remainingArguments;

    private final boolean reverse; // reversed reader reads the arguments from the end

    public StringArgumentReader(@NotNull String arguments) {
        this(arguments, false);
    }

    private StringArgumentReader(@NotNull String arguments, boolean reverse) {
        this.remainingArguments = arguments.strip();
        this.reverse = reverse;
    }

    @Override
    @NotNull
    public StringArgumentReader reversed() {
        return new StringArgumentReader(remainingArguments, true);
    }

    @Override
    public Optional<String> getString(@NotNull String unused) {
        List<String> values = !remainingArguments.isBlank() ?
                // this split arguments into two parts : the first word without spaces, and the rest of the string
                Arrays.asList((reverse ? REVERSE_SPLIT_WORD : SPLIT_WORD).split(remainingArguments, 2))
                : Collections.emptyList();
        values = reverse ? values.reversed() : values;
        remainingArguments = values.size() > 1 ? values.get(1) : "";
        return values.stream().findFirst();
    }

    @Override
    public Optional<BigDecimal> getNumber(@NotNull String unused) {
        return getNext(number -> new BigDecimal(number.replaceFirst(",", ".")));
    }

    @Override
    public Optional<Long> getLong(@NotNull String unused) {
        return getNext(Long::parseLong);
    }

    @Override
    public Optional<ZonedDateTime> getDateTime(@NotNull Locale locale, @Nullable ZoneId timezone, @NotNull Clock clock, @NotNull String unused) {
        return getNext(date -> Dates.parse(locale, timezone, clock, date));
    }

    @Override
    public Optional<LocalDateTime> getLocalDateTime(@NotNull Locale locale, @NotNull String unused) {
        return getNext(date -> Dates.parseLocalDateTime(locale, date));
    }

    @Override
    public Optional<Long> getUserId(@NotNull String unused) {
        return getNext(ArgumentValidator::requireUser);
    }

    private <U> Optional<U> getNext(@NotNull Function<? super String, ? extends U> mapper) {
        String arguments = this.remainingArguments;
        try {
            return getString("").map(mapper);
        } catch (RuntimeException e) {
            this.remainingArguments = arguments; // restore previous state
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getLastArgs(@NotNull String unused) {
        return Optional.of(remainingArguments).filter(not(String::isBlank));
    }
}
