package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;

import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.commands.SecurityAccess.hasRightOnUser;
import static org.sbot.utils.ArgumentValidator.*;

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String DESCRIPTION = "delete one or many alerts (only your alerts, but an admin is allowed to do delete any user alerts)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String DELETE_ALL = "all";

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "delete an alert giving his id").addOptions(
                            option(INTEGER, "alert_id", "id of one alert to delete", true)
                                    .setMinValue(0)),
                    new SubcommandData("all_or_ticker_or_pair", "delete all your alerts or filtered by pair or ticker").addOptions(
                            option(STRING, "all_ticker_pair", "a filter to select the alerts having a ticker or a pair (can be '" + DELETE_ALL + "')", true)
                                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            option(USER, "user", "for admin only, a member to drop the alerts on your server", false)));

    public DeleteCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Long alertId = context.args.getLong("alert_id").map(ArgumentValidator::requirePositive).orElse(null);
        String tickerOrPair = context.args.getString("all_ticker_pair")
                .map(t -> null != alertId ? t : requireTickerPairLength(t)).orElse(null);
        Long ownerId = validateArguments(context, alertId, tickerOrPair);

        LOGGER.debug("delete command - alert_id : {}, user : {}, ticker_pair : {}", alertId, ownerId, tickerOrPair);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, delete(context.noMoreArgs(), alertId, ownerId, tickerOrPair)));
    }

    private Long validateArguments(@NotNull CommandContext context, @Nullable Long alertId, @Nullable String tickerOrPair) {
        if(null == alertId && null == tickerOrPair) {
            throw new IllegalArgumentException("Missing arguments, an alert id or a ticker or a pair is expected");
        }
        if(null != alertId && null != tickerOrPair) {
            throw new IllegalArgumentException("Too many arguments provided, either an alert id or a filter on a ticker or a pair is expected");
        }
        if(null != tickerOrPair && context.args.getLastArgs("user").filter(not(String::isBlank)).isPresent()) {
            return context.args.getMandatoryUserId("user");
        }
        return null;
    }

    private EmbedBuilder delete(@NotNull CommandContext context, @Nullable Long alertId, @Nullable Long ownerId, @Nullable String tickerOrPair) {
        if (null != alertId) { // single id delete
            return deleteById(context, alertId);
        } else if (null == ownerId || // if ownerId is null -> delete all alerts of current user, or filtered by ticker or pair
                hasRightOnUser(context, ownerId)) { // ownerId != null -> only an admin can delete alerts of other users on his server
            return deleteByOwnerOrTickerPair(context, ownerId, tickerOrPair);
        } else {
            return embedBuilder(":clown:" + ' ' + context.user.getEffectiveName(), Color.black, "You are not allowed to delete your mates' alerts" +
                    (isPrivate(context.serverId()) ? ", you are on a private channel." : ""));
        }
    }

    private EmbedBuilder deleteById(@NotNull CommandContext context, long alertId) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, type -> {
            alertsDao.deleteAlert(alertId);
            return "Alert " + alertId + " deleted";
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }

    private EmbedBuilder deleteByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Long ownerId, @Nullable String tickerOrPair) {
        long deleted = null == tickerOrPair || DELETE_ALL.equals(tickerOrPair) ?
                alertsDao.deleteAlerts(context.serverId(), null != ownerId ? ownerId : context.user.getIdLong()) :
                alertsDao.deleteAlerts(context.serverId(), null != ownerId ? ownerId : context.user.getIdLong(), tickerOrPair);
        return embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), Color.green, deleted + " " + (deleted > 1 ? " alerts" : " alert") + " deleted");
    }
}