package org.sbot.entities.settings;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public record Settings(@NotNull UserSettings userSettings, @NotNull ServerSettings serverSettings) {

    public Settings {
        requireNonNull(userSettings);
        requireNonNull(serverSettings);
    }
}
