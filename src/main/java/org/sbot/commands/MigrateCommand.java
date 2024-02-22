package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.commands.DeleteCommand.validateExclusiveArguments;
import static org.sbot.commands.SecurityAccess.hasRightOnUser;
import static org.sbot.entities.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class MigrateCommand extends CommandAdapter {

    private static final String NAME = "migrate";
    static final String DESCRIPTION = "migrate an alert to your private channel or on another guild that we have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String MIGRATE_ALL = "all";

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, "guild_id", PRIVATE_ALERT + " for private channel or id of a guild server whose alert' owner is a member and this bot too", true);

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "migrate an alert by id").addOptions(
                            option(INTEGER, "alert_id", "id of one alert to migrate", true)
                                    .setMinValue(0),
                            SERVER_ID_OPTION),
                    new SubcommandData("filter", "migrate all your alerts or filtered by a ticker or pair").addOptions(
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
        } else { // command filtered
            serverId = null != serverId ? serverId : context.args.getLong("guild_id").orElse(null);
            ownerId = context.args.getLastArgs("owner").filter(not(String::isBlank)).isPresent() ?
                    context.args.getMandatoryUserId("owner") : null;
            serverId = null != serverId ? serverId : context.args.getMandatoryLong("guild_id");
        }

        LOGGER.debug("migrate command - alert_id : {}, server_id : {}, tickerOrPair : {}, ownerId : {}", alertId, serverId, tickerOrPair, ownerId);
        context.noMoreArgs().reply(migrate(context, serverId, alertId, ownerId, tickerOrPair), responseTtlSeconds);
    }

    private Message migrate(@NotNull CommandContext context, long serverId, @Nullable Long alertId, @Nullable Long ownerId, @Nullable String tickerOrPair) {
        Guild server = getGuild(context, serverId);
        if (null != alertId) { // command id
            return migrateById(context, server, alertId);
        } else if (null == ownerId || // command all_or_ticker_or_pair
                hasRightOnUser(context, ownerId)) {
            return migrateByOwnerOrTickerPair(context, server, ownerId, requireNonNull(tickerOrPair));
        } else {
            return Message.of(embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), Color.black, "You are not allowed to migrate your mates' alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    private Message migrateById(@NotNull CommandContext context, @Nullable Guild guild, long alertId) {
        Runnable[] outNotificationCallBack = new Runnable[1];
        var answer = context.transactional(txCtx -> securedAlertAccess(alertId, context, (alert, alertsDao) -> {
            if((isPrivate(alert.serverId) && null == guild) || (null != guild && guild.getIdLong() == alert.serverId)) {
                return embedBuilder("Alert " + alertId + " is already into " + (null == guild ? "the private channel" : "guild " + guildName(guild)));
            }
            if(null == guild || isGuildMember(guild, alert.userId)) {
                long serverId = null != guild ? guild.getIdLong() : PRIVATE_ALERT;
                alertsDao.updateServerId(alertId, serverId);
                if(context.user.getIdLong() != alert.userId) {
                    outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId, ownerMigrateNotification(alertId, requireNonNull(context.member), guild));
                }
                return embedBuilder("Alert migrated to " + (null == guild ? "user private channel" : "guild " + guildName(guild)));
            }
            throw new IllegalArgumentException("User <@" + alert.userId + "> is not a member of guild " + guildName(guild));
        }));
        Optional.ofNullable(outNotificationCallBack[0]).ifPresent(Runnable::run);
        return Message.of(answer);
    }

    @Nullable
    private static Guild getGuild(@NotNull CommandContext context, long serverId) {
        return isPrivate(serverId) ? null :
                context.discord().getGuildServer(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("Bot is not supported on this guild : " + serverId));
    }

    private static boolean isGuildMember(@NotNull Guild guild, long userId) {
        return null != guild.retrieveMemberById(userId).complete(); // blocking call
    }

    private Message migrateByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Guild guild, @Nullable Long ownerId, @NotNull String tickerOrPair) {
        if((isPrivateChannel(context) && null == guild) || (null != guild && guild.getIdLong() == context.serverId())) {
            throw new IllegalArgumentException("Those alerts are already into " + (null == guild ? "the private channel" : "guild " +guildName(guild)));
        }

        long userId = null != ownerId ? ownerId : context.user.getIdLong();
        if(null != guild && !isGuildMember(guild, userId)) {
            throw new IllegalArgumentException("User <@" + userId + "> is not a member of guild " + guildName(guild));
        }
        long migrated = context.transactional(txCtx -> MIGRATE_ALL.equalsIgnoreCase(tickerOrPair) ?
                txCtx.alertsDao().updateServerIdOfUserAndServerId(userId, context.serverId(), null != guild ? guild.getIdLong() : PRIVATE_ALERT) :
                txCtx.alertsDao().updateServerIdOfUserAndServerIdAndTickers(userId, context.serverId(), tickerOrPair.toUpperCase(), null != guild ? guild.getIdLong() : PRIVATE_ALERT));
        if(migrated > 0 && null != ownerId && context.user.getIdLong() != ownerId) {
            sendUpdateNotification(context, ownerId, ownerMigrateNotification(tickerOrPair, requireNonNull(context.member), guild, migrated));
        }
        return Message.of(embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), Color.green, migrated + " " + (migrated > 1 ? " alerts" : " alert") +
                " migrated to " + (null == guild ? "user private channel" : "guild " +guildName(guild))));
    }

    private Message ownerMigrateNotification(long alertId, @NotNull Member member, @Nullable Guild guild) {
        return Message.of(embedBuilder("Notice of alert migration", Color.lightGray,
                "Your alert " + alertId + " was migrated from guild " + guildName(member.getGuild()) + " to " +
                        (null != guild ? guildName(guild) : "your private channel")));
    }

    private Message ownerMigrateNotification(@NotNull String tickerOrPair, @NotNull Member member, @Nullable Guild guild, long nbMigrated) {
        return Message.of(embedBuilder("Notice of " + (nbMigrated > 1 ? "alerts" : "alert") + " migration", Color.lightGray,
                (MIGRATE_ALL.equalsIgnoreCase(tickerOrPair) ? "All your alerts were" :
                        (nbMigrated > 1 ? nbMigrated + " of your alerts having pair or ticker '" + tickerOrPair + "' were" : "Your alert was") +
                                " migrated from guild " + guildName(member.getGuild()) + " to " +
                                (null != guild ? guildName(guild) : "your private channel"))));
    }
}
