package org.sbot.commands.reader;

import org.jetbrains.annotations.NotNull;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.sbot.utils.ArgumentValidator.DISCORD_USER_ID_PATTERN;
import static org.sbot.utils.ArgumentValidator.requireUser;

public final class StringArgumentReader implements ArgumentReader {


    @NotNull
    private String remainingArguments;

    public StringArgumentReader(@NotNull String arguments) {
        this.remainingArguments = requireNonNull(arguments, "missing StringArgumentReader arguments");
    }

    @Override
    public Optional<String> getString(@NotNull String unused) {
        List<String> values = !remainingArguments.isBlank() ?
                // this split arguments into two parts : the first word without spaces, then the rest of the string
                Arrays.asList(remainingArguments.split("\\s+", 2))
                : Collections.emptyList();
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
    public Optional<ZonedDateTime> getDateTime(@NotNull String unused) {
        return getNext(Dates::parseUTC);
    }

    @Override
    public Optional<LocalDateTime> getLocalDateTime(@NotNull String unused) {
        return getNext(Dates::parse);
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
            this.remainingArguments = arguments; // restore previous state TODO doc
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getLastArgs(@NotNull String unused) {
        return Optional.of(remainingArguments).filter(not(String::isBlank));
    }
}
