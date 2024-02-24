package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.alerts.Alert;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.commands.CommandAdapter.isPrivateChannel;

interface SecurityAccess {

    static boolean notFound(@NotNull CommandContext context, @Nullable Alert alert) {
        return null == alert ||
                (isPrivateChannel(context) && !sameUser(context.user, alert.userId)) ||
                (!isPrivateChannel(context) && !sameServer(context.member, alert.serverId));
    }

    static boolean isDenied(@NotNull CommandContext context, @NotNull Alert alert) {
        return !(sameUser(context.user, alert.userId) ||
                (sameServer(context.member, alert.serverId) && isAdminMember(context.member)));
    }

    static boolean hasRightOnUser(@NotNull CommandContext context, long userId) {
        return sameUser(context.user, userId) || isAdminMember(context.member);
    }

    static boolean sameUser(@NotNull User user, long userId) {
        return user.getIdLong() == userId;
    }

    static boolean sameServer(@Nullable Member member, long serverId) {
        return null != member && member.getGuild().getIdLong() == serverId;
    }

    static boolean isAdminMember(@Nullable Member member) {
        return null != member && member.hasPermission(ADMINISTRATOR);
    }
}
