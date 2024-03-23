package org.sbot.entities.alerts;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

public enum ClientType {

    DISCORD("d");

    public static final Map<String, ClientType> SHORTNAMES = Arrays.stream(values()).collect(toUnmodifiableMap(t -> t.shortName, identity()));

    public final String shortName;

    ClientType(@NotNull String shortName) {
        this.shortName = requireNonNull(shortName);
    }
}
