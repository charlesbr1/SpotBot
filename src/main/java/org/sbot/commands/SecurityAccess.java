package org.sbot.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.alerts.Alert;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.commands.CommandAdapter.isPrivateChannel;

interface SecurityAccess {

    static boolean notFound(@NotNull CommandContext context, @Nullable Alert alert) {
        return null == alert ||
                (isPrivateChannel(context) && !sameUser(context, alert.userId)) ||
                (!isPrivateChannel(context) && !sameServer(context, alert.serverId));
    }

    static boolean isDenied(@NotNull CommandContext context, @NotNull Alert alert) {
        return !(sameUser(context, alert.userId) ||
                (sameServer(context, alert.serverId) && isAdminMember(context)));
    }

    static boolean sameUserOrAdmin(@NotNull CommandContext context, long userId) {
        return sameUser(context, userId) || isAdminMember(context);
    }

    static boolean sameUser(@NotNull CommandContext context, long userId) {
        return context.userId == userId;
    }

    static boolean sameServer(@NotNull CommandContext context, long serverId) {
        return switch (context.clientType) {
            case DISCORD -> null != context.discordMember && context.discordMember.getGuild().getIdLong() == serverId;
        };
    }

    static boolean isAdminMember(@NotNull CommandContext context) {
        return switch (context.clientType) {
            case DISCORD -> null != context.discordMember && context.discordMember.hasPermission(ADMINISTRATOR);
        };
    }
}
