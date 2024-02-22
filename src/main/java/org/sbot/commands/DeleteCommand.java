package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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
import static org.sbot.commands.SecurityAccess.hasRightOnUser;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class DeleteCommand extends CommandAdapter {

    private static final String NAME = "delete";
    static final String DESCRIPTION = "delete one or many alerts (only your alerts, but an admin is allowed to do delete any user alerts)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String DELETE_ALL = "all";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "delete an alert by id").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of one alert to delete", true)
                                    .setMinValue(0)),
                    new SubcommandData("filter", "delete all your alerts or filtered by pair or ticker").addOptions(
                            option(STRING, TICKER_PAIR_ARGUMENT, "a pair or a ticker to filter the alerts to delete (can be '" + DELETE_ALL + "')", true)
                                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            option(USER, OWNER_ARGUMENT, "for admin only, a member to drop the alerts on your server", false)));

    private record Arguments(Long alertId, String tickerOrPair, Long ownerId) {}

    public DeleteCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        LOGGER.debug("delete command - {}", arguments);
        context.reply(delete(context, arguments), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        Long alertId = context.args.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(null);
        String tickerOrPair = context.args.getString(TICKER_PAIR_ARGUMENT)
                .map(t -> null != alertId ? t : requireTickerPairLength(t)).orElse(null);
        validateExclusiveArguments(alertId, tickerOrPair);
        Long ownerId = null != tickerOrPair && context.args.getLastArgs(OWNER_ARGUMENT).filter(not(String::isBlank)).isPresent() ?
                context.args.getMandatoryUserId(OWNER_ARGUMENT) : null;
        context.noMoreArgs();
        return new Arguments(alertId, tickerOrPair, ownerId);
    }
    static void validateExclusiveArguments(@Nullable Long alertId, @Nullable String tickerOrPair) {
        if(null == alertId && null == tickerOrPair) {
            throw new IllegalArgumentException("Missing arguments, an alert id or a ticker or a pair is expected");
        }
        if(null != alertId && null != tickerOrPair) {
            throw new IllegalArgumentException("Too many arguments provided, either an alert id or a filter on a ticker or a pair is expected");
        }
    }

    private Message delete(@NotNull CommandContext context, @NotNull Arguments arguments) {
        if (null != arguments.alertId) { // single id delete
            return deleteById(context, arguments.alertId);
        } else if (null == arguments.ownerId || // if ownerId is null -> delete all alerts of current user, or filtered by ticker or pair
                hasRightOnUser(context, arguments.ownerId)) { // ownerId != null -> only an admin can delete alerts of other users on his server
            return deleteByOwnerOrTickerPair(context, arguments.ownerId, arguments.tickerOrPair);
        } else {
            return Message.of(embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), Color.black, "You are not allowed to delete your mates' alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    private Message deleteById(@NotNull CommandContext context, long alertId) {
        Runnable[] outNotificationCallBack = new Runnable[1];
        var answer = context.transactional(txCtx -> securedAlertAccess(alertId, context, (alert, alertsDao) -> {
            alertsDao.deleteAlert(alertId);
            if(context.user.getIdLong() != alert.userId) {
                outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId, ownerDeleteNotification(alertId, requireNonNull(context.member)));
            }
            return embedBuilder("Alert " + alertId + " deleted");
        }));
        Optional.ofNullable(outNotificationCallBack[0]).ifPresent(Runnable::run);
        return Message.of(answer);
    }

    private Message deleteByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Long ownerId, @NotNull String tickerOrPair) {
        long userId = null != ownerId ? ownerId : context.user.getIdLong();
        long deleted = context.transactional(txCtx -> DELETE_ALL.equalsIgnoreCase(tickerOrPair) ?
                txCtx.alertsDao().deleteAlerts(context.serverId(), userId) :
                txCtx.alertsDao().deleteAlerts(context.serverId(), userId, tickerOrPair.toUpperCase()));
        if(deleted > 0 && null != ownerId && context.user.getIdLong() != ownerId) {
            sendUpdateNotification(context, ownerId, ownerDeleteNotification(tickerOrPair, requireNonNull(context.member), deleted));
        }
        return Message.of(embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), Color.green, deleted + " " + (deleted > 1 ? " alerts" : " alert") + " deleted"));
    }


    private Message ownerDeleteNotification(long alertId, @NotNull Member member) {
        return Message.of(embedBuilder("Notice of alert deletion", Color.lightGray,
                "Your alert " + alertId + " was deleted on guild " + guildName(member.getGuild())));
    }

    private Message ownerDeleteNotification(@NotNull String tickerOrPair, @NotNull Member member, long nbDeleted) {
        return Message.of(embedBuilder("Notice of " + (nbDeleted > 1 ? "alerts" : "alert") + " deletion", Color.lightGray,
                (DELETE_ALL.equalsIgnoreCase(tickerOrPair) ? "All your alerts were" :
                        (nbDeleted > 1 ? nbDeleted + " of your alerts having pair or ticker '" + tickerOrPair + "' were" : "Your alert was") +
                                " deleted on guild " + guildName(member.getGuild()))));
    }
}