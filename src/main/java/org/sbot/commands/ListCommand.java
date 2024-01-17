package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.discord.Discord;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.hasRepeat;
import static org.sbot.alerts.Alert.isPrivate;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.MULTI_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;

public final class ListCommand extends CommandAdapter {

    private static final String NAME = "list";
    static final String DESCRIPTION = "list the alerts (optionally filtered by pair or by user), or the supported exchanges and pairs";
    private static final int RESPONSE_TTL_SECONDS = 300;

    private static final String LIST_EXCHANGES = "exchanges";
    private static final String LIST_ALL = "all";

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "what", "an alert id, or 'exchanges', or 'all' alerts, or an user or a ticker or a pair, 'all' if omitted", false)
                            .setMinLength(1),
                    option(STRING, "ticker_pair", "an optional search filter on a ticker or a pair if 'what' is an user", false)
                            .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                    option(INTEGER, "offset", "an offset from where to start the search (results are limited to 1000 alerts)", false)
                            .setMinValue(0));

    public ListCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Long owner, offset;
        String what, tickerOrPair;
        Long alertId = context.args.getLong("what").orElse(null);
        List<EmbedBuilder> reply;

        if(null != alertId) {
            LOGGER.debug("list command - alertId : {}", alertId);
            reply = List.of(listOneAlert(context.noMoreArgs(), alertId));
        } else {
            owner = context.args.getUserId("what").orElse(null);
            offset = null == owner ? null : context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(null);
            tickerOrPair = null == owner ? null : context.args.getString("ticker_pair").map(String::toUpperCase).orElse(null);
            offset = null == owner || null != offset ? offset : context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(0L);
            what = null == owner ? context.args.getString("what").orElse(LIST_ALL) : null;
            offset = null == owner ? context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(0L) : offset;

            LOGGER.debug("list command - what : {}, owner : {}, ticker_pair : {}, offset : {}", what, owner, tickerOrPair, offset);
            context.noMoreArgs();
            reply = null != owner ? listByOwnerAndPair(context, tickerOrPair, owner, offset) :
                    switch (what.toLowerCase()) {
                        case LIST_ALL -> listAll(context, offset);  // list all alerts
                        case LIST_EXCHANGES -> exchanges(); // list exchanges and pairs
                        default -> listByTickerOrPair(context, what.toUpperCase(), offset);
                    };
        }
        context.reply(responseTtlSeconds, reply);
    }

    private EmbedBuilder listOneAlert(@NotNull CommandContext context, long alertId) {
        return context.alertsDao.transactional(() -> {
            List<EmbedBuilder> alertMessage = new ArrayList<>(1);
            AnswerColorSmiley answer = securedAlertAccess(alertId, context, alert -> {
                context.alertsDao.getAlert(alertId)
                        .map(a -> toMessage(context, a))
                        .ifPresent(alertMessage::add);
                return "";
            });
            return !alertMessage.isEmpty() ? alertMessage.get(0) :
                    embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
        });
    }

    private List<EmbedBuilder> listAll(@NotNull CommandContext context, long offset) {
        return context.alertsDao.transactional(() -> {
            long total = isPrivateChannel(context) ?
                    context.alertsDao.countAlertsOfUser(context.user.getIdLong()) :
                    context.alertsDao.countAlertsOfServer(context.serverId());
            List<EmbedBuilder> alertMessages = (isPrivateChannel(context) ?
                    context.alertsDao.getAlertsOfUser(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1) :
                    context.alertsDao.getAlertsOfServer(context.serverId(), offset, MESSAGE_PAGE_SIZE + 1))
                    .stream().map(alert -> toMessage(context, alert)).collect(toList());

            return paginatedAlerts(alertMessages, offset, total,
                    () -> "list all " + (offset + MESSAGE_PAGE_SIZE - 1),
                    () ->  offset > 0 ? " for offset : " + offset : "");
        });
    }

    private List<EmbedBuilder> listByOwnerAndPair(@NotNull CommandContext context, @Nullable String tickerOrPair, long ownerId, long offset) {
        if(null == context.member && context.user.getIdLong() != ownerId) {
            return List.of(embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel"));
        }
        return context.alertsDao.transactional(() -> {
            long total;
            List<EmbedBuilder> alertMessages;

            if (isPrivateChannel(context)) {
                total = null != tickerOrPair ?
                        context.alertsDao.countAlertsOfUserAndTickers(context.user.getIdLong(), tickerOrPair) :
                        context.alertsDao.countAlertsOfUser(context.user.getIdLong());
                alertMessages = (null != tickerOrPair ?
                        context.alertsDao.getAlertsOfUserAndTickers(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1, tickerOrPair) :
                        context.alertsDao.getAlertsOfUser(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1))
                        .stream().map(alert -> toMessage(context, alert)).collect(toList());
            } else {
                total = null != tickerOrPair ?
                        context.alertsDao.countAlertsOfServerAndUserAndTickers(context.serverId(), ownerId, tickerOrPair) :
                        context.alertsDao.countAlertsOfServerAndUser(context.serverId(), ownerId);
                alertMessages = (null != tickerOrPair ?
                        context.alertsDao.getAlertsOfServerAndUserAndTickers(context.serverId(), ownerId, offset, MESSAGE_PAGE_SIZE + 1, tickerOrPair) :
                        context.alertsDao.getAlertsOfServerAndUser(context.serverId(), ownerId, offset, MESSAGE_PAGE_SIZE + 1))
                        .stream().map(alert -> toMessage(context, alert)).collect(toList());
            }

            return paginatedAlerts(alertMessages, offset, total,
                    () -> "list <@" + ownerId + '>' +
                            (null != tickerOrPair ? ' ' + tickerOrPair : "") + " " + (offset + MESSAGE_PAGE_SIZE - 1),
                    () -> " for user <@" + ownerId + (null != tickerOrPair ? "> and '" + tickerOrPair : ">") +
                            (offset > 0 ? "', and offset " + offset : "'"));
        });
    }

    private List<EmbedBuilder> listByTickerOrPair(@NotNull CommandContext context, @NotNull String tickerOrPair, long offset) {
        return context.alertsDao.transactional(() -> {
            long total = isPrivateChannel(context) ?
                    context.alertsDao.countAlertsOfUserAndTickers(context.user.getIdLong(), tickerOrPair) :
                    context.alertsDao.countAlertsOfServerAndTickers(context.serverId(), tickerOrPair);

            List<EmbedBuilder> alertMessages = (isPrivateChannel(context) ?
                    context.alertsDao.getAlertsOfUserAndTickers(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE - 1, tickerOrPair) :
                    context.alertsDao.getAlertsOfServerAndTickers(context.serverId(), offset, MESSAGE_PAGE_SIZE - 1, tickerOrPair))
                    .stream().map(alert -> toMessage(context, alert)).collect(toList());

            return paginatedAlerts(alertMessages, offset, total,
                    () -> "list " + tickerOrPair + " " + (offset + MESSAGE_PAGE_SIZE - 1),
                    () -> " for ticker or pair '" + tickerOrPair + (offset > 0 ? "', and offset : " + offset : "'"));
        });
    }

    private static EmbedBuilder toMessage(@NotNull CommandContext context, @NotNull Alert alert) {
        return embedBuilder('[' + alert.pair + "] " + alert.message,
                !hasRepeat(alert.repeat) ? Color.black : (isPrivate(alert.serverId) ? Color.blue : Color.green),
                alert.descriptionMessage() + (isPrivate(context.serverId()) && !isPrivate(alert.serverId) ?
                        "\n\nGuild : " + context.discord.getGuildServer(alert.serverId).map(Discord::guildName).orElse("unknown") : ""));
    }

    //TODO doc mutation of list messages argument
    private static List<EmbedBuilder> paginatedAlerts(@NotNull List<EmbedBuilder> messages, long offset, long total, @NotNull Supplier<String> nextCommand, @NotNull Supplier<String> command) {
        if(messages.isEmpty()) {
            messages.add(embedBuilder("Alerts search", Color.yellow,
                    "No alert found" + command.get()));
        } else {
            addFooterNumber(messages, offset, total);
            shrinkToPageSize(messages, offset, nextCommand);
        }
        return messages;
    }

    private static void addFooterNumber(@NotNull List<EmbedBuilder> messages, long offset, long total) {
        for(int i = messages.size(); i-- != 0;) {
            messages.get(i).setFooter("(" + (i + 1 + offset) + '/' + total + ')');
        }
    }

    private static void shrinkToPageSize(@NotNull List<EmbedBuilder> messages, long offset, @NotNull Supplier<String> nextCommand) {
        if(messages.size() > MESSAGE_PAGE_SIZE) {
            while(messages.size() >= MESSAGE_PAGE_SIZE) {
                messages.remove(messages.size() - 1);
            }
            messages.add(embedBuilder("...", Color.green, "More results found, to get them type command with offset " +
                    (offset + MESSAGE_PAGE_SIZE - 1) + " : " + nextCommand.get()));
        }
    }

    private List<EmbedBuilder> exchanges() {
        return List.of(embedBuilder(LIST_EXCHANGES, Color.green, //TODO add pairs
                MULTI_LINE_BLOCK_QUOTE_MARKDOWN + "* " + String.join("\n* ", SUPPORTED_EXCHANGES)));
    }
}