package org.sbot.commands.reader;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

public final class SlashArgumentReader implements ArgumentReader {

    @NotNull
    private final SlashCommandInteractionEvent event;

    public SlashArgumentReader(@NotNull SlashCommandInteractionEvent event) {
        this.event = requireNonNull(event);
    }

    @Override
    public Optional<String> getString(@NotNull String fieldName) {
        return getValue(fieldName, OptionMapping::getAsString, identity());
    }

    @Override
    public Optional<BigDecimal> getNumber(@NotNull String fieldName) {
        return getValue(fieldName, OptionMapping::getAsString, BigDecimal::new);
    }

    @Override
    public Optional<Long> getLong(@NotNull String fieldName) {
        return getValue(fieldName, OptionMapping::getAsLong, identity());
    }

    @Override
    public Optional<ZonedDateTime> getDateTime(@NotNull String fieldName) {
        return getValue(fieldName, OptionMapping::getAsString, Dates::parseUTC);
    }

    @Override
    public Optional<Long> getUserId(@NotNull String fieldName) {
        return getValue(fieldName, OptionMapping::getAsUser, User::getIdLong);
    }

    private <T, U> Optional<T> getValue(@NotNull String fieldName, @NotNull Function<? super OptionMapping, ? extends U> optionMapping, @NotNull Function<U, T> mapper) {
        try {
            return Optional.ofNullable(event.getOption(fieldName, optionMapping))
                    .map(mapper);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getLastArgs(@NotNull String fieldName) {
        return getString(fieldName);
    }
}
