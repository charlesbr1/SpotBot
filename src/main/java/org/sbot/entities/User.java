package org.sbot.entities;

import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

public record User(long id, @NotNull Locale locale, @NotNull ZonedDateTime lastAccess) {

    public static final Locale DEFAULT_LOCALE = Locale.UK;

    public User(long id, @NotNull Locale locale, @NotNull ZonedDateTime lastAccess) {
        this.id = id;
        this.locale = requireNonNull(locale);
        this.lastAccess = requireNonNull(lastAccess);
    }

    public User withLocale(@NotNull Locale locale) {
        return new User(id, locale, lastAccess);
    }

    public User withLastAccess(@NotNull ZonedDateTime lastAccess) {
        return new User(id, locale, lastAccess);
    }
}
