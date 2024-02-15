package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.discord.Discord;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.commands.SecurityAccess.isAdminMember;
import static org.sbot.commands.interactions.SelectEditInteraction.updateMenuOf;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.services.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.services.discord.Discord.MULTI_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.utils.ArgumentValidator.*;

public final class ListCommand extends CommandAdapter {

    private static final String NAME = "list";
    static final String DESCRIPTION = "list the alerts (optionally filtered by pair or by user), or the supported exchanges and pairs";
    private static final int RESPONSE_TTL_SECONDS = 300;

    private static final int MESSAGE_LIST_CHUNK = 100;
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
        ZonedDateTime now = Dates.nowUtc(context.clock());

        if(null != alertId) {
            LOGGER.debug("list command - alertId : {}", alertId);
            context.reply(List.of(listOneAlert(context.noMoreArgs(), now, alertId)), responseTtlSeconds);
        } else {
            owner = context.args.getUserId("what").orElse(null);
            offset = null == owner ? null : context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(null);
            tickerOrPair = null == owner ? null : context.args.getString("ticker_pair").map(String::toUpperCase).orElse(null);
            offset = null == owner || null != offset ? offset : context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(0L);
            what = null == owner ? context.args.getString("what").map(String::toLowerCase).orElse(LIST_ALL) : null;
            offset = null == owner ? context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(0L) : offset;

            LOGGER.debug("list command - what : {}, owner : {}, ticker_pair : {}, offset : {}", what, owner, tickerOrPair, offset);
            context.noMoreArgs();
            if(null != owner) {
                context.reply(listByOwnerAndPair(context, now, tickerOrPair, owner, offset), responseTtlSeconds);
            } else {
                switch (what) {
                    case LIST_EXCHANGES : context.reply(exchanges(), responseTtlSeconds); break; // list exchanges and pairs
                    case LIST_ALL : context.reply(listAll(context, now, offset), responseTtlSeconds); break;  // list all alerts
                    default : context.reply(listByTickerOrPair(context, now, what.toUpperCase(), offset), responseTtlSeconds);
                };
            }
        }
    }

    private Message listOneAlert(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId) {
        return context.transactional(txCtx -> {
            List<Message> alertMessage = new ArrayList<>(1);
            var answer = securedAlertAccess(alertId, context, (alert, alertsDao) -> {
                if(alert.userId == context.user.getIdLong() || isAdminMember(context.member)) {
                    alertMessage.add(toMessageWithEdit(context, now, alert, 0L, 0L));
                } else {
                    alertMessage.add(Message.of(toMessage(context, now, alert, 0L, 0L)));
                }
                return embedBuilder("");
            });
            return !alertMessage.isEmpty() ? alertMessage.get(0) :
                    Message.of(answer);
        });
    }

    private List<Message> listAll(@NotNull CommandContext context, @NotNull ZonedDateTime now, long offset) {
        return context.transactional(txCtx -> {
            var dao = txCtx.alertsDao();
            long total = isPrivateChannel(context) ?
                    dao.countAlertsOfUser(context.user.getIdLong()) :
                    dao.countAlertsOfServer(context.serverId());
            var alerts = isPrivateChannel(context) ?
                    dao.getAlertsOfUserOrderByPairId(context.user.getIdLong(), offset, MESSAGE_LIST_CHUNK + 1) :
                    dao.getAlertsOfServerOrderByPairUserIdId(context.serverId(), offset, MESSAGE_LIST_CHUNK + 1);

            var messages = toEditableMessages(context, alerts, now, offset, total, false);
            return paginatedAlerts(messages, offset,
                    () -> "list all " + (offset + MESSAGE_PAGE_SIZE - 1),
                    () ->  offset > 0 ? " for offset : " + offset : "");
        });
    }

    private List<Message> listByOwnerAndPair(@NotNull CommandContext context, @NotNull ZonedDateTime now, @Nullable String tickerOrPair, long ownerId, long offset) {
        if(null == context.member && context.user.getIdLong() != ownerId) {
            return List.of(Message.of(embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel")));
        }
        return context.transactional(txCtx -> {
            var dao = txCtx.alertsDao();
            long total;
            List<Alert> alerts;

            if (isPrivateChannel(context)) {
                total = null != tickerOrPair ?
                        dao.countAlertsOfUserAndTickers(context.user.getIdLong(), tickerOrPair) :
                        dao.countAlertsOfUser(context.user.getIdLong());
                alerts = null != tickerOrPair ?
                        dao.getAlertsOfUserAndTickersOrderById(context.user.getIdLong(), offset, MESSAGE_LIST_CHUNK + 1, tickerOrPair) :
                        dao.getAlertsOfUserOrderByPairId(context.user.getIdLong(), offset, MESSAGE_LIST_CHUNK + 1);
            } else {
                total = null != tickerOrPair ?
                        dao.countAlertsOfServerAndUserAndTickers(context.serverId(), ownerId, tickerOrPair) :
                        dao.countAlertsOfServerAndUser(context.serverId(), ownerId);
                alerts = null != tickerOrPair ?
                        dao.getAlertsOfServerAndUserAndTickersOrderById(context.serverId(), ownerId, offset, MESSAGE_LIST_CHUNK + 1, tickerOrPair) :
                        dao.getAlertsOfServerAndUserOrderByPairId(context.serverId(), ownerId, offset, MESSAGE_LIST_CHUNK + 1);
            }

            var messages = toEditableMessages(context, alerts, now, offset, total, true);
            return paginatedAlerts(messages, offset,
                    () -> "list <@" + ownerId + '>' +
                            (null != tickerOrPair ? ' ' + tickerOrPair : "") + " " + (offset + MESSAGE_LIST_CHUNK - 1),
                    () -> " for user <@" + ownerId + (null != tickerOrPair ? "> and '" + tickerOrPair : ">") +
                            (offset > 0 ? "', and offset " + offset : "'"));
        });
    }

    private List<Message> listByTickerOrPair(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull String tickerOrPair, long offset) {
        return context.transactional(txCtx -> {
            var dao = txCtx.alertsDao();
            long total = isPrivateChannel(context) ?
                    dao.countAlertsOfUserAndTickers(context.user.getIdLong(), tickerOrPair) :
                    dao.countAlertsOfServerAndTickers(context.serverId(), tickerOrPair);
            var alerts = isPrivateChannel(context) ?
                    dao.getAlertsOfUserAndTickersOrderById(context.user.getIdLong(), offset, MESSAGE_LIST_CHUNK - 1, tickerOrPair) :
                    dao.getAlertsOfServerAndTickersOrderByUserIdId(context.serverId(), offset, MESSAGE_LIST_CHUNK - 1, tickerOrPair);

            var messages = toEditableMessages(context, alerts, now, offset, total, isPrivateChannel(context));
            return paginatedAlerts(messages, offset,
                    () -> "list " + tickerOrPair + " " + (offset + MESSAGE_LIST_CHUNK - 1),
                    () -> " for ticker or pair '" + tickerOrPair + (offset > 0 ? "', and offset : " + offset : "'"));
        });
    }

    private static ArrayList<Message> toEditableMessages(@NotNull CommandContext context, @NotNull List<Alert> alerts, @NotNull ZonedDateTime now, long offset, long total, boolean editAll) {
        editAll = editAll && (isPrivateChannel(context) || isAdminMember(context.member));
        // return list of one message containing all the embeds (alerts) between each editable message that can contains only one embed
        long userId = context.user.getIdLong();
        var messages = new ArrayList<Message>();
        for(int i = 0; i < alerts.size(); i++) {
            int nextEditableIndex = i;
            while (!editAll && nextEditableIndex < alerts.size() && alerts.get(nextEditableIndex).userId != userId) {
                nextEditableIndex++;
            }
            if(i == nextEditableIndex) {
                messages.add(toMessageWithEdit(context, now, alerts.get(i), offset + i + 1, total));
            } else {
                var embedBuilders = new ArrayList<EmbedBuilder>(nextEditableIndex - i);
                for(; i < nextEditableIndex; i++) {
                    embedBuilders.add(toMessage(context, now, alerts.get(i), offset + i + 1, total));
                }
                messages.add(Message.of(embedBuilders));
            }
        }
        return messages;
    }

    public static Message toMessageWithEdit(@NotNull CommandContext context, ZonedDateTime now, @NotNull Alert alert, long offset, long total) {
        return Message.of(toMessage(context, now, alert, offset, total), ActionRow.of(updateMenuOf(alert)));
    }

    public static final String ALERT_TITLE_PAIR_FOOTER = "] ";
    public static EmbedBuilder toMessage(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, long offset, long total) {
        return listAlert(context, now, alert)
                .setTitle('[' + alert.pair + ALERT_TITLE_PAIR_FOOTER + alert.message)
                .setFooter(total > 0 ? "(" + offset + "/" + total + ")" : "");
    }

    static EmbedBuilder listAlert(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert) {
        return alert.descriptionMessage(now, isPrivateChannel(context) && !isPrivate(alert.serverId) ?
                context.discord().getGuildServer(alert.serverId).map(Discord::guildName).orElse("unknown") : null);
    }

    private static List<Message> paginatedAlerts(@NotNull ArrayList<Message> messages, long offset, @NotNull Supplier<String> nextCommand, @NotNull Supplier<String> command) {
        if (messages.isEmpty()) {
            return List.of(Message.of(embedBuilder("Alerts search", Color.yellow,
                    "No alert found" + command.get())));
        } else {
            return shrinkToPageSize(messages, offset, nextCommand);
        }
    }

    private static List<Message> shrinkToPageSize(@NotNull ArrayList<Message> messages, long offset, @NotNull Supplier<String> nextCommand) {
        if(messages.size() > MESSAGE_LIST_CHUNK) {
            while(messages.size() >= MESSAGE_LIST_CHUNK) {
                messages.remove(messages.size() - 1);
            }
            messages.add(Message.of(embedBuilder("...", Color.green, "More results found, to get them type command with offset " +
                    (offset + MESSAGE_LIST_CHUNK - 1) + " : " + nextCommand.get())));
        }
        return messages;
    }

    private Message exchanges() {
        return Message.of(embedBuilder(LIST_EXCHANGES, Color.green, //TODO add pairs
                MULTI_LINE_BLOCK_QUOTE_MARKDOWN + "* " + String.join("\n* ", SUPPORTED_EXCHANGES)));
    }
}