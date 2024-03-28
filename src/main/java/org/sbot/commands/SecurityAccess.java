package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.discord.Discord;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.commands.CommandAdapter.isPrivateChannel;

interface SecurityAccess {

    static boolean notFound(@NotNull CommandContext context, @Nullable Alert alert) {
        return null == alert ||
                (isPrivateChannel(context) && !sameUser(context, alert.userId)) ||
                (!isPrivateChannel(context) && !sameServer(context, alert.serverId));
    }

    static boolean canUpdate(@NotNull CommandContext context, @NotNull Alert alert) {
        return sameUser(context, alert.userId) ||
                (sameServer(context, alert.serverId) && isSpotBotAdmin(context));
    }

    static boolean sameUserOrAdmin(@NotNull CommandContext context, long userId) {
        return sameUser(context, userId) || isSpotBotAdmin(context);
    }

    static boolean sameUser(@NotNull CommandContext context, long userId) {
        return context.userId == userId;
    }

    static boolean sameServer(@NotNull CommandContext context, long serverId) {
        return switch (context.clientType) {
            case DISCORD -> null != context.discordMember && context.discordMember.getGuild().getIdLong() == serverId;
        };
    }

    static boolean isSpotBotAdmin(@NotNull CommandContext context) {
        return switch (context.clientType) {
            case DISCORD -> null != context.discordMember &&
                    (context.discordMember.hasPermission(ADMINISTRATOR) ||
                            hasDiscordSpotBotAdminRole(context.discordMember, context.serverSettings));
        };
    }

    private static boolean hasDiscordSpotBotAdminRole(@NotNull Member member, @NotNull ServerSettings settings) {
        return Discord.spotBotRole(member.getGuild(), settings.spotBotAdminRole())
                .filter(member.getRoles()::contains)
                .isPresent();
    }

    static boolean isAdminMember(@NotNull CommandContext context) {
        return switch (context.clientType) {
            case DISCORD -> null != context.discordMember && context.discordMember.hasPermission(ADMINISTRATOR);
        };
    }
}
