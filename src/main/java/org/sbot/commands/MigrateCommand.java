package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.utils.ArgumentValidator;

import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_POSITIVE_NUMBER;
import static org.sbot.commands.SecurityAccess.sameUser;
import static org.sbot.commands.SecurityAccess.sameUserOrAdmin;
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
    static final String MIGRATE_ALL = "all";

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, GUILD_ARGUMENT, PRIVATE_MESSAGES + " for private channel or id of a guild server whose alert owner is a member and this bot too", true)
            .setMaxValue((long) MAX_POSITIVE_NUMBER);

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "migrate an alert by id").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of one alert to migrate", true)
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
        LOGGER.debug("migrate command - {}", arguments);
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
            return migrateByOwnerOrTickerPair(context, server, arguments);
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
        EmbedBuilder answer = securedAlertUpdate(alertId, context, (alert, alertsDao) -> {
            if(sameId(guild, alert.serverId)) {
                throw new IllegalArgumentException("Alert " + alertId + " is already in guild " + guildName(guild));
            }
            userId[0] = alert.userId;
            return embedBuilder("");
        });
        if(null != userId[0]) { // alert exists and update rights are ok
            requireGuildMember(guild, userId[0]); // possibly blocking call
            Runnable[] notificationCallBack = new Runnable[1];
            answer = securedAlertUpdate(alertId, context, (alert, alertsDao) -> {
                if(!sameId(guild, alert.serverId)) { // this can have changed since above tx, unlikely but possible
                    alertsDao.update(alert.withServerId(guild.getIdLong()), EnumSet.of(SERVER_ID));
                    if(!sameUser(context.user, alert.userId)) { // send notification once transaction is successful
                        notificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId, ownerMigrateNotification(alertId, requireNonNull(context.member), guild));
                    }
                }
                return embedBuilder("Alert migrated to guild " + guildName(guild));
            });
            Optional.ofNullable(notificationCallBack[0]).ifPresent(Runnable::run);
        }
        return Message.of(answer);
    }


    private Message migrateByIdToPrivate(@NotNull CommandContext context, long alertId) {
        Runnable[] notificationCallBack = new Runnable[1];
        EmbedBuilder answer = securedAlertUpdate(alertId, context, (alert, alertsDao) -> {
            if (PRIVATE_MESSAGES == alert.serverId) {
                throw new IllegalArgumentException("Alert " + alertId + " is already in this private channel");
            }
            alertsDao.update(alert.withServerId(PRIVATE_MESSAGES), EnumSet.of(SERVER_ID));
            if (!sameUser(context.user, alert.userId)) { // send notification once transaction is successful
                notificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId, ownerMigrateNotification(alertId, requireNonNull(context.member), null));
            }
            return embedBuilder("Alert migrated to user private channel");
        });
        Optional.ofNullable(notificationCallBack[0]).ifPresent(Runnable::run);
        return Message.of(answer);
    }

    private Message migrateByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Guild guild, @NotNull Arguments arguments) {
        if(sameId(guild, context.serverId())) {
            throw new IllegalArgumentException("Those alerts are already into " + (null == guild ? "the private channel" : "guild " + guildName(guild)));
        }

        long userId = null != arguments.ownerId ? arguments.ownerId : context.user.getIdLong();
        requireGuildMember(guild, userId);
        String tickerOrPair = MIGRATE_ALL.equalsIgnoreCase(arguments.tickerOrPair) ? null : arguments.tickerOrPair.toUpperCase();
        long serverId = null != guild ? guild.getIdLong() : PRIVATE_MESSAGES;
        var filter = (isPrivateChannel(context) ? SelectionFilter.ofUser(userId, arguments.type) : SelectionFilter.of(context.serverId(), userId, arguments.type))
                .withTickerOrPair(tickerOrPair);
        long migrated = context.transactional(txCtx -> txCtx.alertsDao().updateServerIdOf(filter, serverId));
        if(migrated > 0 && !sameUser(context.user, userId)) {
            sendUpdateNotification(context, userId, ownerMigrateNotification(arguments.tickerOrPair, requireNonNull(context.member), guild, migrated));
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
                context.discord().getGuildServer(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("Bot is not supported on this guild : " + serverId));
    }


    // possibly blocking call
    static void requireGuildMember(@Nullable Guild guild, long userId) {
        if(null != guild && null == guild.retrieveMemberById(userId).complete()) {
            throw new IllegalArgumentException("User <@" + userId + "> is not a member of guild " + guildName(guild));
        }
    }

    private Message ownerMigrateNotification(long alertId, @NotNull Member member, @Nullable Guild guild) {
        return Message.of(embedBuilder("Notice of alert migration", NOTIFICATION_COLOR,
                "Your alert " + alertId + " was migrated from guild " + guildName(member.getGuild()) + " to " +
                        (null != guild ? guildName(guild) : "your private channel")));
    }

    private Message ownerMigrateNotification(@NotNull String tickerOrPair, @NotNull Member member, @Nullable Guild guild, long nbMigrated) {
        return Message.of(embedBuilder("Notice of " + (nbMigrated > 1 ? "alerts" : "alert") + " migration", NOTIFICATION_COLOR,
                (MIGRATE_ALL.equalsIgnoreCase(tickerOrPair) ? "All your alerts were" :
                        (nbMigrated > 1 ? nbMigrated + " of your alerts having pair or ticker '" + tickerOrPair + "' were" : "Your alert was") +
                                " migrated from guild " + guildName(member.getGuild()) + " to " +
                                (null != guild ? guildName(guild) : "your private channel"))));
    }
}
