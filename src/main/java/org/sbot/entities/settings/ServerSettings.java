package org.sbot.entities.settings;

import org.jetbrains.annotations.NotNull;
import org.sbot.SpotBot;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;
import static org.sbot.entities.settings.UserSettings.DEFAULT_TIMEZONE;
import static org.sbot.entities.settings.UserSettings.NO_ID;
import static org.sbot.utils.ArgumentValidator.requireSettingsMaxLength;

public record ServerSettings(long discordServerId, // long telegramServerId, etc...
                             @NotNull ZoneId timezone, @NotNull String spotBotChannel,
                             @NotNull String spotBotRole, @NotNull String spotBotAdminRole,
                             @NotNull ZonedDateTime lastAccess) {


    public static final String DEFAULT_BOT_CHANNEL = SpotBot.appProperties.get("discord.bot.channel");
    public static final String DEFAULT_BOT_ROLE = SpotBot.appProperties.get("discord.bot.role");
    public static final String DEFAULT_BOT_ROLE_ADMIN = SpotBot.appProperties.get("discord.bot.role.admin");

    public static final ServerSettings PRIVATE_SERVER = new ServerSettings(NO_ID, DEFAULT_TIMEZONE, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, ZonedDateTime.now());

    public ServerSettings {
        requireNonNull(timezone);
        requireSettingsMaxLength(spotBotChannel);
        requireSettingsMaxLength(spotBotRole);
        requireSettingsMaxLength(spotBotAdminRole);
        requireNonNull(lastAccess);
    }

    @NotNull
    public static ServerSettings ofDiscordServer(long discordServerId, @NotNull ZonedDateTime lastAccess) {
        return ofDiscordServer(discordServerId, DEFAULT_TIMEZONE, DEFAULT_BOT_CHANNEL, DEFAULT_BOT_ROLE, DEFAULT_BOT_ROLE_ADMIN, lastAccess);
    }

    @NotNull
    public static ServerSettings ofDiscordServer(long discordServerId,
                                           @NotNull ZoneId timezone, @NotNull String spotBotChannel,
                                           @NotNull String spotBotRole, @NotNull String spotBotAdminRole,
                                           @NotNull ZonedDateTime lastAccess) {
        return new ServerSettings(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
    }

    @NotNull
    public ServerSettings withTimezone(@NotNull ZoneId timezone) {
        return new ServerSettings(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
    }

    @NotNull
    public ServerSettings withChannel(@NotNull String spotBotChannel) {
        return new ServerSettings(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
    }

    @NotNull
    public ServerSettings withRole(@NotNull String spotBotRole) {
        return new ServerSettings(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
    }

    @NotNull
    public ServerSettings withAdminRole(@NotNull String spotBotAdminRole) {
        return new ServerSettings(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
    }

    @NotNull
    public ServerSettings withLastAccess(@NotNull ZonedDateTime lastAccess) {
        return new ServerSettings(discordServerId, timezone, spotBotChannel, spotBotRole, spotBotAdminRole, lastAccess);
    }
}
