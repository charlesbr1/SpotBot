package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao.UserIdServerIdType;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.alerts.Alert.isPrivate;

interface SecurityAccess {

    static boolean notFound(@NotNull CommandContext context, @Nullable UserIdServerIdType alert) {
        return null == alert ||
                (isPrivate(alert.serverId()) && !alertBelongToUser(context.user, alert.userId())) ||
                (!isPrivate(alert.serverId()) && !alertIsOnMemberServer(context.member, alert.serverId()));
    }

    static boolean isDenied(@NotNull CommandContext context, @NotNull UserIdServerIdType alert) {
        return !(alertBelongToUser(context.user, alert.userId()) ||
                (alertIsOnMemberServer(context.member, alert.serverId()) && isAdminMember(context.member)));
    }

    static boolean isManageableUser(@NotNull CommandContext context, long userId) {
        boolean alertBelongToUser = alertBelongToUser(context.user, userId);
        return (isPrivate(context.serverId()) && alertBelongToUser) ||
                (!isPrivate(context.serverId()) && (alertBelongToUser || isAdminMember(context.member)));
    }

    private static boolean alertBelongToUser(@NotNull User user, long userId) {
        return user.getIdLong() == userId;
    }

    private static boolean alertIsOnMemberServer(@Nullable Member member, long serverId) {
        return !isPrivate(serverId) && null != member &&
                member.getGuild().getIdLong() == serverId;
    }

    private static boolean isAdminMember(@Nullable Member member) {
        return null != member && member.hasPermission(ADMINISTRATOR);
    }
}
