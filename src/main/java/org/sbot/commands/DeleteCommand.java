package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.utils.ArgumentValidator;

import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_POSITIVE_NUMBER;
import static org.sbot.commands.SecurityAccess.sameUser;
import static org.sbot.commands.SecurityAccess.sameUserOrAdmin;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class DeleteCommand extends CommandAdapter {

    static final String NAME = "delete";
    static final String DESCRIPTION = "delete one or many alerts (only your alerts, but an admin is allowed to do delete any user alerts)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final String DELETE_ALL = "all";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "delete an alert by id").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of one alert to delete", true)
                                    .setMinValue(0).setMaxValue((long) MAX_POSITIVE_NUMBER)),
                    new SubcommandData("filter", "delete all your alerts or filtered by pair or ticker or type").addOptions(
                            option(STRING, TICKER_PAIR_ARGUMENT, "a pair or a ticker to filter the alerts to delete (can be '" + DELETE_ALL + "')", true)
                                    .setMinLength(TICKER_MIN_LENGTH).setMaxLength(PAIR_MAX_LENGTH),
                            option(STRING, TYPE_ARGUMENT, "type of alert to delete (range, trend or remainder)", false)
                                    .addChoices(Stream.of(Type.values()).map(t -> new Choice(t.name(), t.name())).toList()),
                            option(USER, OWNER_ARGUMENT, "for admin only, an user whose alerts will be deleted", false)));

    record Arguments(Long alertId, Type type, String tickerOrPair, Long ownerId) {}

    public DeleteCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        LOGGER.debug("delete command - user {}, server {}, arguments {}", context.user.getIdLong(), context.serverId(), arguments);
        context.reply(delete(context, arguments), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        var alertId = context.args.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive);
        if(alertId.isPresent()) {
            context.noMoreArgs();
            return new Arguments(alertId.get(), null, null, null);
        }
        String tickerOrPair = requireTickerPairLength(context.args.getMandatoryString(TICKER_PAIR_ARGUMENT));
        Type type = context.args.getType(TYPE_ARGUMENT).orElse(null);
        Long ownerId = context.args.getUserId(OWNER_ARGUMENT).orElse(null);
        if(null == type && context.args.getLastArgs(TYPE_ARGUMENT).isPresent()) {
            type = context.args.getMandatoryType(TYPE_ARGUMENT); // string command flexible on argument order
        }
        context.noMoreArgs();
        return new Arguments(null, type, tickerOrPair, ownerId);
    }

    private Message delete(@NotNull CommandContext context, @NotNull Arguments arguments) {
        if (null != arguments.alertId) { // single id delete
            return deleteById(context, arguments.alertId);
        } else if (null == arguments.ownerId || // if ownerId is null -> delete all alerts of current user, or filtered by ticker or pair
                sameUserOrAdmin(context, arguments.ownerId)) { // ownerId != null -> only an admin can delete other users alerts on his server
            return deleteByOwnerOrTickerPair(context, arguments);
        } else {
            return Message.of(embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), DENIED_COLOR, "You are not allowed to delete your mates alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    private Message deleteById(@NotNull CommandContext context, long alertId) {
        Runnable[] outNotificationCallBack = new Runnable[1];
        var answer = securedAlertUpdate(alertId, context, (alert, alertsDao) -> {
            alertsDao.deleteAlert(alertId);
            if(!sameUser(context.user, alert.userId)) { // send notification once transaction is successful
                outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId, ownerDeleteNotification(alertId, requireNonNull(context.member)));
            }
            return embedBuilder("Alert " + alertId + " deleted");
        });
        Optional.ofNullable(outNotificationCallBack[0]).ifPresent(Runnable::run);
        return Message.of(answer);
    }

    private Message deleteByOwnerOrTickerPair(@NotNull CommandContext context, @NotNull Arguments arguments) {
        String tickerOrPair = DELETE_ALL.equalsIgnoreCase(arguments.tickerOrPair) ? null : arguments.tickerOrPair.toUpperCase();
        long userId = null != arguments.ownerId ? arguments.ownerId : context.user.getIdLong();
        var filter = (isPrivateChannel(context) ? SelectionFilter.ofUser(userId, arguments.type) : SelectionFilter.of(context.serverId(), userId, arguments.type))
                .withTickerOrPair(tickerOrPair);
        long deleted = context.transactional(txCtx -> txCtx.alertsDao().deleteAlerts(filter));
        if(deleted > 0 && !sameUser(context.user, userId)) {
            sendUpdateNotification(context, userId, ownerDeleteNotification(arguments.tickerOrPair, requireNonNull(context.member), deleted));
        }
        return Message.of(embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), OK_COLOR, deleted + (deleted > 1 ? " alerts" : " alert") + " deleted"));
    }


    private Message ownerDeleteNotification(long alertId, @NotNull Member member) {
        return Message.of(embedBuilder("Notice of alert deletion", NOTIFICATION_COLOR,
                "Your alert " + alertId + " was deleted on guild " + guildName(member.getGuild())));
    }

    private Message ownerDeleteNotification(@NotNull String tickerOrPair, @NotNull Member member, long nbDeleted) {
        return Message.of(embedBuilder("Notice of " + (nbDeleted > 1 ? "alerts" : "alert") + " deletion", NOTIFICATION_COLOR,
                (DELETE_ALL.equalsIgnoreCase(tickerOrPair) ? "All your alerts were" :
                        (nbDeleted > 1 ? nbDeleted + " of your alerts having pair or ticker '" + tickerOrPair + "' were" : "Your alert was") +
                                " deleted on guild " + guildName(member.getGuild()))));
    }
}