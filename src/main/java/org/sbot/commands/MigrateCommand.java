package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.entities.alerts.ClientType;
import org.sbot.entities.notifications.MigratedNotification;
import org.sbot.entities.notifications.MigratedNotification.Reason;
import org.sbot.services.context.TransactionalContext;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.discord.Discord;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_POSITIVE_NUMBER;
import static org.sbot.commands.SecurityAccess.sameUserOrAdmin;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.services.dao.AlertsDao.UpdateField.SERVER_ID;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class MigrateCommand extends CommandAdapter {

    static final String NAME = "migrate";
    static final String DESCRIPTION = "migrate an alert to your private channel or on another server that we have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final String SERVER_ID_ARGUMENT = "server_id";
    public static final String MIGRATE_ALL = "all";

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, SERVER_ID_ARGUMENT, PRIVATE_MESSAGES + " for private channel or id of a guild server whose alert owner is a member and this bot too", true)
            .setMaxValue((long) MAX_POSITIVE_NUMBER);

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "migrate an alert by id").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of the alert to migrate", true)
                                    .setMinValue(0).setMaxValue((long) MAX_POSITIVE_NUMBER),
                            SERVER_ID_OPTION),
                    new SubcommandData("filter", "migrate all your alerts or filtered by a ticker or pair").addOptions(
                            option(STRING, TICKER_PAIR_ARGUMENT, "a filter to select the alerts having a ticker or a pair (can be '" + MIGRATE_ALL + "')", true)
                                    .setMinLength(TICKER_MIN_LENGTH).setMaxLength(PAIR_MAX_LENGTH),
                            SERVER_ID_OPTION,
                            option(STRING, TYPE_ARGUMENT, "type of alert to migrate (range, trend or remainder)", false)
                                    .addChoices(Stream.of(Type.values()).map(t -> new Choice(t.name(), t.name())).toList()),
                            option(USER, OWNER_ARGUMENT, "for admin only, owner of the alerts to migrate", false)));

    record Arguments(Long alertId, Type type, Long serverId, String tickerOrPair, Long ownerId) {}

    public MigrateCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        LOGGER.debug("{} command - user {}, server {}, arguments {}", NAME, context.userId, context.serverId(), arguments);
        context.reply(migrate(context, arguments), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        var alertId = context.args.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive);
        if(alertId.isPresent()) {
            long serverId = context.args.getMandatoryLong(SERVER_ID_ARGUMENT);
            context.noMoreArgs();
            return new Arguments(alertId.get(), null, serverId, null, null);
        }
        Long serverId = context.args.getLong(SERVER_ID_ARGUMENT).orElse(null);
        String tickerOrPair = requireTickerPairLength(context.args.getMandatoryString(TICKER_PAIR_ARGUMENT));
        // command filtered
        serverId = null != serverId ? serverId : context.args.getLong(SERVER_ID_ARGUMENT).orElse(null);
        Type type = context.args.getType(TYPE_ARGUMENT).orElse(null);
        serverId = null != serverId ? serverId : context.args.getMandatoryLong(SERVER_ID_ARGUMENT);
        type = null != type ? type : context.args.getType(TYPE_ARGUMENT).orElse(null);
        Long ownerId = context.args.getUserId(OWNER_ARGUMENT).orElse(null);
        context.noMoreArgs();
        return new Arguments(null, type, serverId, tickerOrPair, ownerId);
    }

    private Message migrate(@NotNull CommandContext context, @NotNull Arguments arguments) {
        Object server = getServer(context, arguments.serverId);
        if (null != arguments.alertId) { // command id
            return migrateById(context, server, arguments.alertId);
        } else if (null == arguments.ownerId || // command filter
                sameUserOrAdmin(context, arguments.ownerId)) {
            return migrateByTypeOwnerOrTickerPair(context, server, arguments);
        } else {
            return Message.of(embedBuilder(":clown:" + ' ' + context.userName, DENIED_COLOR, "You are not allowed to migrate your mates alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    private Message migrateById(@NotNull CommandContext context, @Nullable Object server, long alertId) {
        if(null == server) {
            return migrateByIdToPrivate(context, alertId);
        }
        var toServer = switch (context.clientType) {
            case DISCORD -> guildName((Guild) server);
        };
        Long[] userId = new Long[1]; // retrieve alertId first to avoid performing blocking call requireGuildMember in a transaction
        var answer = securedAlertUpdate(alertId, context, (alert, alertsDao, notificationsDao) -> {
            if(sameId(context.clientType, server, alert.serverId)) {
                throw new IllegalArgumentException("Alert " + alert.id + " is already in server " + toServer);
            }
            userId[0] = alert.userId;
            return Message.of(embedBuilder(""));
        });
        if(null != userId[0]) { // alert exists and update rights are ok
            switch (context.clientType) {
                case DISCORD -> requireGuildMember((Guild) server, userId[0]); // possibly blocking call
            }
            answer = securedAlertUpdate(alertId, context, (alert, alertsDao, notificationsDao) -> {
                if(!sameId(context.clientType, server, alert.serverId)) { // this can have changed since above tx, unlikely but possible
                    var serverId = switch (context.clientType) {
                        case DISCORD -> ((Guild) server).getIdLong();
                    };
                    alertsDao.update(alert.withServerId(serverId), EnumSet.of(SERVER_ID));
                    if(null != context.member && sendNotification(context, alert.userId, 1)) { // send notification once transaction is successful
                        var fromServer = switch (context.clientType) {
                            case DISCORD -> guildName(context.member.getGuild());
                        };
                        notificationsDao.get().addNotification(MigratedNotification.of(context.clientType, Dates.nowUtc(context.clock()), context.locale, alert.userId, alert.id, alert.type, alert.pair, fromServer, toServer, Reason.ADMIN, 1L));
                    }
                }
                return Message.of(embedBuilder("Alert migrated to server " + toServer));
            });
        }
        return answer;
    }


    private Message migrateByIdToPrivate(@NotNull CommandContext context, long alertId) {
        return securedAlertUpdate(alertId, context, (alert, alertsDao, notificationsDao) -> {
            if (PRIVATE_MESSAGES == alert.serverId) {
                throw new IllegalArgumentException("Alert " + alertId + " is already in this private channel");
            }
            alertsDao.update(alert.withServerId(PRIVATE_MESSAGES), EnumSet.of(SERVER_ID));
            if(null != context.member && sendNotification(context, alert.userId, 1)) { // send notification once transaction is successful
                var serverName = switch (context.clientType) {
                    case DISCORD -> guildName(context.member.getGuild());
                };
                notificationsDao.get().addNotification(MigratedNotification.of(context.clientType, Dates.nowUtc(context.clock()), context.locale, alert.userId, alert.id, alert.type, alert.pair, serverName, null, Reason.ADMIN, 1L));
            }
            return Message.of(embedBuilder("Alert migrated to <@" + context.userId + "> private channel"));
        });
    }

    private Message migrateByTypeOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Object server, @NotNull Arguments arguments) {
        String toServer = null != server ? switch (context.clientType) {
            case DISCORD -> guildName((Guild) server);
        } : null;
        if(sameId(context.clientType, server, context.serverId())) {
            throw new IllegalArgumentException("Those alerts are already into " + (null == toServer ? "the private channel" : "server " + toServer));
        }

        long userId = null != arguments.ownerId ? arguments.ownerId : context.userId;
        switch (context.clientType) {
            case DISCORD -> requireGuildMember((Guild) server, userId);
        }
        String tickerOrPair = MIGRATE_ALL.equalsIgnoreCase(arguments.tickerOrPair) ? null : arguments.tickerOrPair.toUpperCase();
        long serverId = switch (context.clientType) {
            case DISCORD -> null != server ? ((Guild) server).getIdLong() : PRIVATE_MESSAGES;
        };
        var filter = (isPrivateChannel(context) ? SelectionFilter.ofUser(context.clientType, userId, arguments.type) :
                SelectionFilter.of(context.clientType, context.serverId(), userId, arguments.type))
                .withTickerOrPair(tickerOrPair);
        long migrated = context.transactional(txCtx -> {
            long count = txCtx.alertsDao().updateServerIdOf(filter, serverId);
            if(null != context.member && sendNotification(context, userId, count)) {
                var fromServer = switch (context.clientType) {
                    case DISCORD -> guildName(context.member.getGuild());
                };
                txCtx.notificationsDao().addNotification(MigratedNotification.of(context.clientType, Dates.nowUtc(context.clock()), context.locale, userId, null, arguments.type, arguments.tickerOrPair, fromServer, toServer, Reason.ADMIN, count));
            }
            return count;
        });
        if(sendNotification(context, userId, migrated)) {
            context.notificationService().sendNotifications();
        }
        return Message.of(embedBuilder(":+1:" + ' ' + context.userName, OK_COLOR, migrated + (migrated > 1 ? " alerts" : " alert") +
                " migrated to " + (null == toServer ? "user private channel" : "server " + toServer)));
    }

    static boolean sameId(@NotNull ClientType clientType, @Nullable Object server, long serverId) {
        return switch (clientType) {
            case DISCORD -> null != server ? ((Guild) server).getIdLong() == serverId : PRIVATE_MESSAGES == serverId;
        };
    }

    @Nullable
    private static Object getServer(@NotNull CommandContext context, long serverId) {
        return switch (context.clientType) {
            case DISCORD -> isPrivate(serverId) ? null :
                    context.discord().guildServer(serverId)
                            .orElseThrow(() -> new IllegalArgumentException("Bot is not supported on this guild : " + serverId));
        };
    }

    // used by discord EventAdapter onGuildLeave
    public static List<Long> migrateServerAlertsToPrivateChannel(@NotNull ClientType clientType, @NotNull TransactionalContext txCtx, long serverId, @Nullable Object server) {
        var alertsDao = txCtx.alertsDao();
        var userIds = alertsDao.getUserIdsByServerId(clientType, serverId);
        if(!userIds.isEmpty()) {
            long totalMigrated = alertsDao.updateServerIdOf(SelectionFilter.ofServer(clientType, serverId, null), PRIVATE_MESSAGES);
            LOGGER.debug("Migrated to private {} alerts on server {}, reason : {}", totalMigrated, serverId, Reason.SERVER_LEAVED);
            var userLocales = txCtx.usersDao().getLocales(userIds);
            var now = Dates.nowUtc(txCtx.clock());
            var serverName = switch (clientType) {
                case DISCORD -> Optional.ofNullable((Guild) server).map(Discord::guildName).orElseGet(() -> String.valueOf(serverId));
            };
            userIds.forEach(userId -> txCtx.notificationsDao().addNotification(MigratedNotification
                    .of(clientType, now, userLocales.getOrDefault(userId, DEFAULT_LOCALE), userId, null, null, MIGRATE_ALL, serverName, null, Reason.SERVER_LEAVED, 0L)));
        }
        return userIds;
    }

    // used by NotificationsService and discord EventAdapter onGuildBan, onGuildMemberRemove
    public static long migrateUserAlertsToPrivateChannel(@NotNull ClientType clientType, @NotNull TransactionalContext txCtx, @NotNull Long userId, @Nullable Locale locale, @NotNull Object server, @NotNull Reason reason) {
        var serverId = switch (clientType) {
            case DISCORD -> ((Guild) server).getIdLong();
        };
        long count = txCtx.alertsDao().updateServerIdOf(SelectionFilter.of(clientType, serverId, userId, null), PRIVATE_MESSAGES);
        LOGGER.debug("Migrated to private {} alerts of user {} on server {}, reason : {}", count, userId, serverId, reason);
        if(count > 0) {
            locale = Optional.ofNullable(locale).orElseGet(() -> txCtx.usersDao().getUser(userId).map(org.sbot.entities.User::locale).orElse(DEFAULT_LOCALE));
            var serverName = switch (clientType) {
                case DISCORD -> guildName((Guild) server);
            };
            txCtx.notificationsDao().addNotification(MigratedNotification.of(clientType, Dates.nowUtc(txCtx.clock()),
                    locale, userId, null, null, MIGRATE_ALL, serverName, null, reason, count));
        }
        return count;
    }
}
