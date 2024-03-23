package org.sbot.entities.notifications;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

public enum RecipientType {

    DISCORD_USER("du"),
    DISCORD_SERVER("ds");

    public static final Map<String, RecipientType> SHORTNAMES = Arrays.stream(values()).collect(toUnmodifiableMap(t -> t.shortName, identity()));

    public final String shortName;

    RecipientType(@NotNull String shortName) {
        this.shortName = requireNonNull(shortName);
    }
}
