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

import java.awt.*;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.commands.SecurityAccess.sameUser;
import static org.sbot.commands.SecurityAccess.sameUserOrAdmin;
import static org.sbot.entities.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.services.dao.AlertsDao.UpdateField.SERVER_ID;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class MigrateCommand extends CommandAdapter {

    private static final String NAME = "migrate";
    static final String DESCRIPTION = "migrate an alert to your private channel or on another guild that we have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String GUILD_ARGUMENT = "guild_id";
    private static final String MIGRATE_ALL = "all";

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, GUILD_ARGUMENT, PRIVATE_ALERT + " for private channel or id of a guild server whose alert' owner is a member and this bot too", true);

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "migrate an alert by id").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of one alert to migrate", true)
                                    .setMinValue(0),
                            SERVER_ID_OPTION),
                    new SubcommandData("filter", "migrate all your alerts or filtered by a ticker or pair").addOptions(
                            option(STRING, TICKER_PAIR_ARGUMENT, "a filter to select the alerts having a ticker or a pair (can be '" + MIGRATE_ALL + "')", true)
                                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            SERVER_ID_OPTION,
                            option(STRING, TYPE_ARGUMENT, "type of alert to migrate (range, trend or remainder)", false)
                                    .addChoices(Stream.of(Type.values()).map(t -> new Choice(t.name(), t.name())).toList()),
                            option(USER, OWNER_ARGUMENT, "for admin only, owner of the alerts to migrate", false)));

    private record Arguments(Long alertId, Type type, Long serverId, String tickerOrPair, Long ownerId) {}

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
        Long alertId = context.args.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(null);
        Long serverId = context.args.getLong(GUILD_ARGUMENT).orElse(null);
        String tickerOrPair = context.args.getString(TICKER_PAIR_ARGUMENT)
                .map(t -> null != alertId ? t : requireTickerPairLength(t)).orElse(null);
        Type type = null;
//TODO        validateExclusiveArguments(alertId, tickerOrPair);
        Long ownerId = null;
        if(null != alertId) { // command id
            if(null == serverId) {
                throw new IllegalArgumentException("Missing guild id");
            }
        } else { // command filtered
            type = context.args.getType(TYPE_ARGUMENT).orElse(null);
            serverId = null != serverId ? serverId : context.args.getLong(GUILD_ARGUMENT).orElse(null);
            ownerId = context.args.getLastArgs(OWNER_ARGUMENT).filter(not(String::isBlank)).isPresent() ?
                    context.args.getMandatoryUserId(OWNER_ARGUMENT) : null;
            serverId = null != serverId ? serverId : context.args.getMandatoryLong(GUILD_ARGUMENT);
        }
        context.noMoreArgs();
        return new Arguments(alertId, type, serverId, tickerOrPair, ownerId);
    }

    private Message migrate(@NotNull CommandContext context, @NotNull Arguments arguments) {
        Guild server = getGuild(context, arguments.serverId);
        if (null != arguments.alertId) { // command id
            return migrateById(context, server, arguments.alertId);
        } else if (null == arguments.ownerId || // command all_or_ticker_or_pair
                sameUserOrAdmin(context, arguments.ownerId)) {
            return migrateByOwnerOrTickerPair(context, server, arguments);
        } else {
            return Message.of(embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), Color.black, "You are not allowed to migrate your mates' alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    @Nullable
    private static Guild getGuild(@NotNull CommandContext context, long serverId) {
        return isPrivate(serverId) ? null :
                context.discord().getGuildServer(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("Bot is not supported on this guild : " + serverId));
    }

    private Message migrateById(@NotNull CommandContext context, @Nullable Guild guild, long alertId) {
        Runnable[] outNotificationCallBack = new Runnable[1];
        var answer = context.transactional(txCtx -> securedAlertAccess(alertId, context, (alert, alertsDao) -> {
            if(sameId(guild, alert.serverId)) {
                return embedBuilder("Alert " + alertId + " is already into " + (null == guild ? "this private channel" : "guild " + guildName(guild)));
            }
            EmbedBuilder embed;
            var serverId = PRIVATE_ALERT;
            if(null == guild) {
                embed = embedBuilder("Alert migrated to user private channel");
            } else if(isGuildMember(guild, alert.userId)) {
                serverId = guild.getIdLong();
                embed = embedBuilder("Alert migrated to guild " + guildName(guild));
            } else {
                throw notGuildMemberException(alert.userId, guild);
            }
            alertsDao.update(alert.withServerId(serverId), EnumSet.of(SERVER_ID));
            if(context.user.getIdLong() != alert.userId) { // send notification once transaction is successful
                outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId, ownerMigrateNotification(alertId, requireNonNull(context.member), guild));
            }
            return embed;
        }));
        Optional.ofNullable(outNotificationCallBack[0]).ifPresent(Runnable::run);
        return Message.of(answer);
    }

    private static boolean isGuildMember(@NotNull Guild guild, long userId) {
        return null != guild.retrieveMemberById(userId).complete(); // blocking call
    }

    private Message migrateByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Guild guild, @NotNull Arguments arguments) {
        if(sameId(guild, context.serverId())) {
            throw new IllegalArgumentException("Those alerts are already into " + (null == guild ? "the private channel" : "guild " + guildName(guild)));
        }

        long userId = null != arguments.ownerId ? arguments.ownerId : context.user.getIdLong();
        if(null != guild && !isGuildMember(guild, userId)) {
            throw notGuildMemberException(userId, guild);
        }
        String pair = MIGRATE_ALL.equalsIgnoreCase(arguments.tickerOrPair) ? null : arguments.tickerOrPair.toUpperCase();
        long serverId = null != guild ? guild.getIdLong() : PRIVATE_ALERT;
        long migrated = context.transactional(txCtx -> txCtx.alertsDao()
                .updateServerIdOf(SelectionFilter.of(context.serverId(), userId, arguments.type).withTickerOrPair(pair), serverId));
        if(migrated > 0 && null != arguments.ownerId && !sameUser(context.user, arguments.ownerId)) {
            sendUpdateNotification(context, arguments.ownerId, ownerMigrateNotification(arguments.tickerOrPair, requireNonNull(context.member), guild, migrated));
        }
        return Message.of(embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), Color.green, migrated + " " + (migrated > 1 ? " alerts" : " alert") +
                " migrated to " + (null == guild ? "user private channel" : "guild " + guildName(guild))));
    }

    static boolean sameId(@Nullable Guild guild, long serverId) {
        return null != guild ? guild.getIdLong() == serverId : PRIVATE_ALERT == serverId;
    }

    static IllegalArgumentException notGuildMemberException(long userId, @NotNull Guild guild) {
        return new IllegalArgumentException("User <@" + userId + "> is not a member of guild " + guildName(guild));
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
