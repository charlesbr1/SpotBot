package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.entities.notifications.DeletedNotification;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_POSITIVE_NUMBER;
import static org.sbot.commands.SecurityAccess.sameUserOrAdmin;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;

public final class DeleteCommand extends CommandAdapter {

    static final String NAME = "delete";
    static final String DESCRIPTION = "delete one or many alerts (admins are allowed to delete member alerts)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    public static final String DELETE_ALL = "all";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "delete an alert by id").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of the alert to delete", true)
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
        LOGGER.debug("{} command - user {}, server {}, arguments {}", NAME, context.userId, context.serverId(), arguments);
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
        Long ownerId = context.args.getUserId(context.clientType, OWNER_ARGUMENT).orElse(null);
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
            return deleteByTypeOwnerOrTickerPair(context, arguments);
        } else {
            return Message.of(embedBuilder(":clown:  " + context.userName, DENIED_COLOR, "You are not allowed to delete your mates alerts" +
                    (isPrivateChannel(context) ? ", you are on a private channel." : "")));
        }
    }

    private Message deleteById(@NotNull CommandContext context, long alertId) {
        return securedAlertUpdate(alertId, context, (alert, alertsDao, notificationsDao) -> {
            alertsDao.delete(context.clientType, alertId);
            if(sendNotification(context, alert.userId, 1)) { // send notification once transaction is successful
                var serverName = switch (context.clientType) {
                    case DISCORD -> guildName(requireNonNull(context.discordMember).getGuild());
                };
                notificationsDao.get().addNotification(DeletedNotification.of(context.clientType, Dates.nowUtc(context.clock()), context.locale, alert.userId, alertId, alert.type, alert.pair, serverName, 1L, false));
            }
            return Message.of(embedBuilder("Alert " + alertId + " deleted"));
        });
    }

    private Message deleteByTypeOwnerOrTickerPair(@NotNull CommandContext context, @NotNull Arguments arguments) {
        String tickerOrPair = DELETE_ALL.equalsIgnoreCase(arguments.tickerOrPair) ? null : arguments.tickerOrPair.toUpperCase();
        long userId = null != arguments.ownerId ? arguments.ownerId : context.userId;
        var filter = (isPrivateChannel(context) ? SelectionFilter.ofUser(context.clientType, userId, arguments.type) : SelectionFilter.of(context.clientType, context.serverId(), userId, arguments.type))
                .withTickerOrPair(tickerOrPair);
        long deleted = context.transactional(txCtx -> {
            long count = txCtx.alertsDao().delete(filter);
            if(sendNotification(context, userId, count)) {
                var serverName = switch (context.clientType) {
                    case DISCORD -> guildName(requireNonNull(context.discordMember).getGuild());
                };
                txCtx.notificationsDao().addNotification(DeletedNotification.of(context.clientType, Dates.nowUtc(context.clock()), context.locale, userId, null, arguments.type, arguments.tickerOrPair, serverName, count, false));
            }
            return count;
        });
        if(sendNotification(context, userId, deleted)) {
            context.notificationService().sendNotifications();
        }
        return Message.of(embedBuilder(":+1:  " + context.userName, OK_COLOR, deleted + (deleted > 1 ? " alerts" : " alert") + " deleted"));
    }
}