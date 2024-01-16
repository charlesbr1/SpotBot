package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;

import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.commands.SecurityAccess.hasRightOnUser;
import static org.sbot.utils.ArgumentValidator.*;

public final class DeleteCommand extends CommandAdapter {

    private static final String NAME = "delete";
    static final String DESCRIPTION = "delete one or many alerts (only your alerts, but an admin is allowed to do delete any user alerts)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String DELETE_ALL = "all";

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", "delete an alert by id").addOptions(
                            option(INTEGER, "alert_id", "id of one alert to delete", true)
                                    .setMinValue(0)),
                    new SubcommandData("filtered", "delete all your alerts or filtered by pair or ticker").addOptions(
                            option(STRING, "search_filter", "a pair or a ticker to filter the alerts to delete (can be '" + DELETE_ALL + "')", true)
                                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            option(USER, "owner", "for admin only, a member to drop the alerts on your server", false)));

    public DeleteCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Long alertId = context.args.getLong("alert_id").map(ArgumentValidator::requirePositive).orElse(null);
        String tickerOrPair = context.args.getString("search_filter")
                .map(t -> null != alertId ? t : requireTickerPairLength(t)).orElse(null);
        validateExclusiveArguments(alertId, tickerOrPair);
        Long ownerId = null != tickerOrPair && context.args.getLastArgs("owner").filter(not(String::isBlank)).isPresent() ?
                context.args.getMandatoryUserId("owner") : null;

        LOGGER.debug("delete command - alert_id : {}, owner : {}, ticker_pair : {}", alertId, ownerId, tickerOrPair);
        context.noMoreArgs().alertsDao.transactional(() -> context.reply(responseTtlSeconds, delete(context, alertId, ownerId, tickerOrPair)));
    }

    static void validateExclusiveArguments(@Nullable Long alertId, @Nullable String tickerOrPair) {
        if(null == alertId && null == tickerOrPair) {
            throw new IllegalArgumentException("Missing arguments, an alert id or a ticker or a pair is expected");
        }
        if(null != alertId && null != tickerOrPair) {
            throw new IllegalArgumentException("Too many arguments provided, either an alert id or a filter on a ticker or a pair is expected");
        }
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
            context.alertsDao.deleteAlert(alertId);
            return "Alert " + alertId + " deleted";
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }

    private EmbedBuilder deleteByOwnerOrTickerPair(@NotNull CommandContext context, @Nullable Long ownerId, @Nullable String tickerOrPair) {
        long deleted = null == tickerOrPair || DELETE_ALL.equalsIgnoreCase(tickerOrPair) ?
                context.alertsDao.deleteAlerts(context.serverId(), null != ownerId ? ownerId : context.user.getIdLong()) :
                context.alertsDao.deleteAlerts(context.serverId(), null != ownerId ? ownerId : context.user.getIdLong(), tickerOrPair.toUpperCase());
        return embedBuilder(":+1:" + ' ' + context.user.getEffectiveName(), Color.green, deleted + " " + (deleted > 1 ? " alerts" : " alert") + " deleted");
    }
}