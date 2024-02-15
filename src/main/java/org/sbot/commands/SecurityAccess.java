package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.commands.context.CommandContext;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.commands.CommandAdapter.isPrivateChannel;
import static org.sbot.entities.alerts.Alert.isPrivate;

interface SecurityAccess {

    static boolean notFound(@NotNull CommandContext context, @Nullable Alert alert) {
        return null == alert || //TODO bug si sur private channel, doit pouvoir trouver ses alerts sur autres guild
                (isPrivate(alert.serverId) && !alertBelongToUser(context.user, alert.userId)) ||
                (!isPrivate(alert.serverId) && !alertIsOnMemberServer(context.member, alert.serverId));
    }

    static boolean isDenied(@NotNull CommandContext context, @NotNull Alert alert) {
        return !(alertBelongToUser(context.user, alert.userId) ||
                (alertIsOnMemberServer(context.member, alert.serverId) && isAdminMember(context.member)));
    }

    static boolean hasRightOnUser(@NotNull CommandContext context, long userId) {
        boolean alertBelongToUser = alertBelongToUser(context.user, userId);
        return (isPrivateChannel(context) && alertBelongToUser) ||
                (!isPrivateChannel(context) && (alertBelongToUser || isAdminMember(context.member)));
    }

    static boolean alertBelongToUser(@NotNull User user, long userId) {
        return user.getIdLong() == userId;
    }

    private static boolean alertIsOnMemberServer(@Nullable Member member, long serverId) {
        return !isPrivate(serverId) && null != member &&
                member.getGuild().getIdLong() == serverId;
    }

    static boolean isAdminMember(@Nullable Member member) {
        return null != member && member.hasPermission(ADMINISTRATOR);
    }
}
