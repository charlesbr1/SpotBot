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
    static final String DESCRIPTION = "migrate an alert to your private channel or on another guild that we have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final String GUILD_ARGUMENT = "guild_id";
    public static final String MIGRATE_ALL = "all";

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, GUILD_ARGUMENT, PRIVATE_MESSAGES + " for private channel or id of a guild server whose alert owner is a member and this bot too", true)
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
        LOGGER.debug("{} command - user {}, server {}, arguments {}", NAME, context.user.getIdLong(), context.serverId(), arguments);
        context.reply(migrate(context, arguments), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        var alertId = context.args.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive);
        if(alertId.isPresent()) {
            long serverId = context.args.getMandatoryLong(GUILD_ARGUMENT);
            context.noMoreArgs();
            return new Arguments(alertId.get(), null, serverId, null, null);
        }
        Long serverId = context.args.getLong(GUILD_ARGUMENT).orElse(null);
        String tickerOrPair = requireTickerPairLength(context.args.getMandatoryString(TICKER_PAIR_ARGUMENT));
        // command filtered
        serverId = null != serverId ? serverId : context.args.getLong(GUILD_ARGUMENT).orElse(null);
        Type type = context.args.getType(TYPE_ARGUMENT).orElse(null);
        serverId = null != serverId ? serverId : context.args.getMandatoryLong(GUILD_ARGUMENT);
        type = null != type ? type : context.args.getType(TYPE_ARGUMENT).orElse(null);
        Long ownerId = context.args.getUserId(OWNER_ARGUMENT).orElse(null);
        context.noMoreArgs();
        return new Arguments(null, type, serverId, tickerOrPair, ownerId);
    }

    private Message migrate(@NotNull CommandContext context, @NotNull Arguments arguments) {
        Guild server = getGuild(context, arguments.serverId);
        if (null != arguments.alertId) { // command id
            return migrateById(context, server, arguments.alertId);
        } else if (null == arguments.ownerId || // command filter
                sameUserOrAdmin(context, arguments.ownerId)) {
            return migrateByTypeOwnerOrTickerPair(context, server, arguments);
        } else {
            return Message.of(embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), DENIED_COLOR, "You are not allowed to migrate your mates alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    private Message migrateById(@NotNull CommandContext context, @Nullable Guild guild, long alertId) {
        if(null == guild) {
            return migrateByIdToPrivate(context, alertId);
        }
        Long[] userId = new Long[1]; // retrieve alertId first to avoid performing blocking call isGuildMember in a transaction
        var answer = securedAlertUpdate(alertId, context, (alert, alertsDao, notificationsDao) -> {
            if(sameId(guild, alert.serverId)) {
                throw new IllegalArgumentException("Alert " + alert.id + " is already in guild " + guildName(guild));
            }
            userId[0] = alert.userId;
            return Message.of(embedBuilder(""));
        });
        if(null != userId[0]) { // alert exists and update rights are ok
            requireGuildMember(guild, userId[0]); // possibly blocking call
            answer = securedAlertUpdate(alertId, context, (alert, alertsDao, notificationsDao) -> {
                if(!sameId(guild, alert.serverId)) { // this can have changed since above tx, unlikely but possible
                    alertsDao.update(alert.withServerId(guild.getIdLong()), EnumSet.of(SERVER_ID));
                    if(null != context.member && sendNotification(context, alert.userId, 1)) { // send notification once transaction is successful
                        notificationsDao.get().addNotification(MigratedNotification.of(Dates.nowUtc(context.clock()), context.locale, alert.userId, alert.id, alert.type, alert.pair, guildName(context.member.getGuild()), guildName(guild), Reason.ADMIN, 1L));
                    }
                }
                return Message.of(embedBuilder("Alert migrated to guild " + guildName(guild)));
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
                notificationsDao.get().addNotification(MigratedNotification.of(Dates.nowUtc(context.clock()), context.locale, alert.userId, alert.id, alert.type, alert.pair, guildName(context.member.getGuild()), null, Reason.ADMIN, 1L));
            }
            return Message.of(embedBuilder("Alert migrated to <@" + context.user.getIdLong() + "> private channel"));
        });
    }

    private Message migrateByTypeOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Guild guild, @NotNull Arguments arguments) {
        if(sameId(guild, context.serverId())) {
            throw new IllegalArgumentException("Those alerts are already into " + (null == guild ? "the private channel" : "guild " + guildName(guild)));
        }

        long userId = null != arguments.ownerId ? arguments.ownerId : context.user.getIdLong();
        requireGuildMember(guild, userId);
        String tickerOrPair = MIGRATE_ALL.equalsIgnoreCase(arguments.tickerOrPair) ? null : arguments.tickerOrPair.toUpperCase();
        long serverId = null != guild ? guild.getIdLong() : PRIVATE_MESSAGES;
        var filter = (isPrivateChannel(context) ? SelectionFilter.ofUser(userId, arguments.type) :
                SelectionFilter.of(context.serverId(), userId, arguments.type))
                .withTickerOrPair(tickerOrPair);
        long migrated = context.transactional(txCtx -> {
            long count = txCtx.alertsDao().updateServerIdOf(filter, serverId);
            if(null != context.member && sendNotification(context, userId, count)) {
                String toGuild = null != guild ? guildName(guild) : null;
                txCtx.notificationsDao().addNotification(MigratedNotification.of(Dates.nowUtc(context.clock()), context.locale, userId, null, arguments.type, arguments.tickerOrPair, guildName(context.member.getGuild()), toGuild, Reason.ADMIN, count));
            }
            return count;
        });
        if(sendNotification(context, userId, migrated)) {
            context.notificationService().sendNotifications();
        }
        return Message.of(embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), OK_COLOR, migrated + (migrated > 1 ? " alerts" : " alert") +
                " migrated to " + (null == guild ? "user private channel" : "guild " + guildName(guild))));
    }

    static boolean sameId(@Nullable Guild guild, long serverId) {
        return null != guild ? guild.getIdLong() == serverId : PRIVATE_MESSAGES == serverId;
    }

    @Nullable
    private static Guild getGuild(@NotNull CommandContext context, long serverId) {
        return isPrivate(serverId) ? null :
                context.discord().guildServer(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("Bot is not supported on this guild : " + serverId));
    }

    // used by discord EventAdapter onGuildLeave
    public static List<Long> migrateServerAlertsToPrivateChannel(@NotNull TransactionalContext txCtx, long guildId, @Nullable Guild guild) {
        var alertsDao = txCtx.alertsDao();
        var userIds = alertsDao.getUserIdsByServerId(guildId);
        if(!userIds.isEmpty()) {
            long totalMigrated = alertsDao.updateServerIdOf(SelectionFilter.ofServer(guildId, null), PRIVATE_MESSAGES);
            LOGGER.debug("Migrated to private {} alerts on server {}, reason : {}", totalMigrated, guildId, Reason.SERVER_LEAVED);
            var userLocales = txCtx.usersDao().getLocales(userIds);
            var now = Dates.nowUtc(txCtx.clock());
            var guildName = Optional.ofNullable(guild).map(Discord::guildName).orElseGet(() -> String.valueOf(guildId));
            userIds.forEach(userId -> txCtx.notificationsDao().addNotification(MigratedNotification
                    .of(now, userLocales.getOrDefault(userId, DEFAULT_LOCALE), userId, null, null, MIGRATE_ALL, guildName, null, Reason.SERVER_LEAVED, 0L)));
        }
        return userIds;
    }

    // used by NotificationsService and discord EventAdapter onGuildBan, onGuildMemberRemove
    public static long migrateUserAlertsToPrivateChannel(@NotNull TransactionalContext txCtx, @NotNull Long userId, @Nullable Locale locale, @NotNull Guild guild, @NotNull Reason reason) {
        long count = txCtx.alertsDao().updateServerIdOf(SelectionFilter.of(guild.getIdLong(), userId, null), PRIVATE_MESSAGES);
        LOGGER.debug("Migrated to private {} alerts of user {} on server {}, reason : {}", count, userId, guild.getIdLong(), reason);
        if(count > 0) {
            locale = Optional.ofNullable(locale).orElseGet(() -> txCtx.usersDao().getUser(userId).map(org.sbot.entities.User::locale).orElse(DEFAULT_LOCALE));
            txCtx.notificationsDao().addNotification(MigratedNotification.of(Dates.nowUtc(txCtx.clock()),
                    locale, userId, null, null, MIGRATE_ALL, guildName(guild), null, reason, count));
        }
        return count;
    }
}
