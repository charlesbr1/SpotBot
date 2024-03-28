package org.sbot.entities.settings;

import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.Dates.UTC;

public record UserSettings(long discordUserId, // long telegramUserId, email, etc...
                           @NotNull Locale locale, @NotNull ZoneId timezone, @NotNull ZonedDateTime lastAccess) {

    public static final long NO_ID = 0L;
    public static final Locale DEFAULT_LOCALE = Locale.UK;
    public static final ZoneId DEFAULT_TIMEZONE = UTC;

    public static final UserSettings NO_USER = new UserSettings(NO_ID, DEFAULT_LOCALE, DEFAULT_TIMEZONE, ZonedDateTime.now());

    public UserSettings {
        requireNonNull(locale);
        requireNonNull(timezone);
        requireNonNull(lastAccess);
    }

    @NotNull
    public static UserSettings ofDiscordUser(long discordUserId, @NotNull Locale locale,
                                         @NotNull ZoneId timezone, @NotNull ZonedDateTime lastAccess) {
        return new UserSettings(discordUserId, locale, timezone, lastAccess);
    }

    @NotNull
    public UserSettings withLocale(@NotNull Locale locale) {
        return new UserSettings(discordUserId, locale, timezone, lastAccess);
    }

    @NotNull
    public UserSettings withTimezone(@NotNull ZoneId timezone) {
        return new UserSettings(discordUserId, locale, timezone, lastAccess);
    }

    @NotNull
    public UserSettings withLastAccess(@NotNull ZonedDateTime lastAccess) {
        return new UserSettings(discordUserId, locale, timezone, lastAccess);
    }
}
