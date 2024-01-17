package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.commands.DeleteCommand.validateExclusiveArguments;
import static org.sbot.commands.SecurityAccess.hasRightOnUser;
import static org.sbot.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class MigrateCommand extends CommandAdapter {

    private static final String NAME = "migrate";
    static final String DESCRIPTION = "migrate an alert to your private channel or on another guild that we have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String MIGRATE_ALL = "all";

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, "guild_id", PRIVATE_ALERT + " for private channel or id of a guild server whose alert' owner is a member and this bot too", true);

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "migrate an alert by id").addOptions(
                            option(INTEGER, "alert_id", "id of one alert to migrate", true)
                                    .setMinValue(0),
                            SERVER_ID_OPTION),
                    new SubcommandData("filtered", "migrate all your alerts or filtered by a pair or a ticker").addOptions(
                            option(STRING, "search_filter", "a filter to select the alerts having a ticker or a pair (can be '" + MIGRATE_ALL + "')", true)
                                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            SERVER_ID_OPTION,
                            option(USER, "owner", "for admin only, owner of the alerts to migrate", false)));

    public MigrateCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Long alertId = context.args.getLong("alert_id").map(ArgumentValidator::requirePositive).orElse(null);
        Long serverId = context.args.getLong("guild_id").orElse(null);
        String tickerOrPair = context.args.getString("search_filter")
                .map(t -> null != alertId ? t : requireTickerPairLength(t)).orElse(null);
        validateExclusiveArguments(alertId, tickerOrPair);
        Long ownerId = null;
        if(null != alertId) { // command id
            if(null == serverId) {
                throw new IllegalArgumentException("Missing guild id");
            }
        } else { // command all_or_ticker_or_pair
            serverId = null != serverId ? serverId : context.args.getLong("guild_id").orElse(null);
            ownerId = context.args.getLastArgs("owner").filter(not(String::isBlank)).isPresent() ?
                    context.args.getMandatoryUserId("owner") : null;
            serverId = null != serverId ? serverId : context.args.getMandatoryLong("guild_id");
        }
        long finalServerId = serverId;
        Long finalOwnerId = ownerId;

        LOGGER.debug("migrate command - alert_id : {}, server_id : {}, tickerOrPair : {}, ownerId : {}", alertId, serverId, tickerOrPair, ownerId);
        Runnable[] notificationCallBack = new Runnable[1];
        context.noMoreArgs().alertsDao.transactional(() ->
                context.reply(responseTtlSeconds, migrate(context, finalServerId, alertId, finalOwnerId, tickerOrPair, notificationCallBack)));
        // perform user notification of its alerts being migrated, if needed, once transaction is done.
        Optional.ofNullable(notificationCallBack[0]).ifPresent(Runnable::run);
    }

    private EmbedBuilder migrate(@NotNull CommandContext context, long serverId, @Nullable Long alertId, @Nullable Long ownerId, @Nullable String tickerOrPair, @NotNull Runnable[] outNotificationCallBack) {
        Guild server = getGuild(context, serverId);
        if (null != alertId) { // command id
            return migrateById(context, server, alertId, outNotificationCallBack);
        } else if (null == ownerId || // command all_or_ticker_or_pair
                hasRightOnUser(context, ownerId)) {
            return migrateByOwnerOrTickerPair(context, server, ownerId, requireNonNull(tickerOrPair), outNotificationCallBack);
        } else {
            return embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), Color.black, "You are not allowed to migrate your mates' alerts" +
                    (isPrivate(context.serverId()) ? ", you are on a private channel." : ""));
        }
    }

    private EmbedBuilder migrateById(@NotNull CommandContext context, @Nullable Guild guild, long alertId, Runnable[] outNotificationCallBack) {
        AnswerColorSmiley answer = securedAlertAccess(alertId, context, alert -> {
            if((isPrivate(alert.serverId()) && null == guild) || (null != guild && guild.getIdLong() == alert.serverId())) {
                throw new IllegalArgumentException("Alert " + alertId + " is already into " + (null == guild ? "the private channel" : "guild " + guildName(guild)));
            }
            if(null == guild || isGuildMember(guild, alert.userId())) {
                context.alertsDao.updateServerId(alertId, null != guild ? guild.getIdLong() : PRIVATE_ALERT);
                if(context.user.getIdLong() != alert.userId()) {
                    outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId(), ownerMigrateNotification(alertId, requireNonNull(context.member), guild));
                }
                return "Alert migrated to " + (null == guild ? "user private channel" : "guild " + guildName(guild));
            }
            throw new IllegalArgumentException("User <@" + alert.userId() + "> is not a member of guild " + guildName(guild));
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }

    @Nullable
    private static Guild getGuild(@NotNull CommandContext context, long serverId) {
        return isPrivate(serverId) ? null :
                Optional.ofNullable(context.channel.getJDA().getGuildById(serverId))
                        .orElseThrow(() -> new IllegalArgumentException("Bot is not supported on this guild : " + serverId));
    }

    private static boolean isGuildMember(@NotNull Guild guild, long userId) {
        return null != guild.retrieveMemberById(userId).complete(); // blocking call
    }

    private EmbedBuilder migrateByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Guild guild, @Nullable Long ownerId, @NotNull String tickerOrPair, Runnable[] outNotificationCallBack) {
        if((isPrivate(context.serverId()) && null == guild) || (null != guild && guild.getIdLong() == context.serverId())) {
            throw new IllegalArgumentException("Those alerts are already into " + (null == guild ? "the private channel" : "guild " +guildName(guild)));
        }

        long userId = null != ownerId ? ownerId : context.user.getIdLong();
        if(null != guild && !isGuildMember(guild, userId)) {
            throw new IllegalArgumentException("User <@" + userId + "> is not a member of guild " + guildName(guild));
        }
        long migrated = MIGRATE_ALL.equalsIgnoreCase(tickerOrPair) ?
                context.alertsDao.updateServerIdOfUserAndServerId(userId, context.serverId(), null != guild ? guild.getIdLong() : PRIVATE_ALERT) :
                context.alertsDao.updateServerIdOfUserAndServerIdAndTickers(userId, context.serverId(), tickerOrPair.toUpperCase(), null != guild ? guild.getIdLong() : PRIVATE_ALERT);
        if(migrated > 0 && null != ownerId && context.user.getIdLong() != ownerId) {
            outNotificationCallBack[0] = () -> sendUpdateNotification(context, ownerId, ownerMigrateNotification(tickerOrPair, requireNonNull(context.member), guild, migrated));
        }
        return embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), Color.green, migrated + " " + (migrated > 1 ? " alerts" : " alert") +
                " migrated to " + (null == guild ? "user private channel" : "guild " +guildName(guild)));
    }

    private EmbedBuilder ownerMigrateNotification(long alertId, @NotNull Member member, @Nullable Guild guild) {
        return embedBuilder("Notice of alert migration", Color.lightGray,
                "Your alert " + alertId + " was migrated from guild " + guildName(member.getGuild()) + " to " +
                        (null != guild ? guildName(guild) : "your private channel"));
    }

    private EmbedBuilder ownerMigrateNotification(@NotNull String tickerOrPair, @NotNull Member member, @Nullable Guild guild, long nbMigrated) {
        return embedBuilder("Notice of " + (nbMigrated > 1 ? "alerts" : "alert") + " migration", Color.lightGray,
                (MIGRATE_ALL.equalsIgnoreCase(tickerOrPair) ? "All your alerts were" :
                        (nbMigrated > 1 ? nbMigrated + " of your alerts having pair or ticker '" + tickerOrPair + "' were" : "Your alert was") +
                                " migrated from guild " + guildName(member.getGuild()) + " to " +
                                (null != guild ? guildName(guild) : "your private channel")));
    }
}
