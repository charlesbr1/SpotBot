package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.discord.Discord;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.time.ZoneId.SHORT_IDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.commands.SecurityAccess.*;
import static org.sbot.commands.interactions.SelectEditInteraction.updateMenuOf;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.services.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.ArgumentValidator.ALERT_MAX_PAIR_LENGTH;
import static org.sbot.utils.ArgumentValidator.ALERT_MIN_TICKER_LENGTH;

public final class ListCommand extends CommandAdapter {

    private static final String NAME = "list";
    static final String DESCRIPTION = "list the alerts (optionally filtered by pair or by user), or the supported exchanges and pairs";
    private static final int RESPONSE_TTL_SECONDS = 300;

    private static final int MESSAGE_LIST_CHUNK = 100;
    private static final String LIST_SETTINGS = "settings";
    private static final String LIST_LOCALES = "locales";
    private static final String LIST_TIMEZONES = "timezones";
    private static final String LIST_EXCHANGES = "exchanges";
    private static final String LIST_ALL = "all";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, SELECTION_ARGUMENT, "settings, timezones, alert id, 'all' (alerts), exchanges, user, a ticker, a pair, 'all' if omitted", false)
                            .setMinLength(1),
                    option(STRING, TICKER_PAIR_ARGUMENT, "an optional search filter on a ticker or a pair if selection is an user", false)
                            .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                    option(STRING, TYPE_ARGUMENT, "type of alert to list (range, trend or remainder)", false)
                            .addChoices(Stream.of(Type.values()).map(t -> new Choice(t.name(), t.name())).toList()),
                    option(INTEGER, OFFSET_ARGUMENT, "an offset from where to start the search (results are limited to " + MESSAGE_LIST_CHUNK + " alerts)", false)
                            .setMinValue(0));

    private record Arguments(Long alertId, Type type, String selection, Long ownerId, String tickerOrPair, Long offset) {}

    public ListCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        LOGGER.debug("list command - {}", arguments);
        ZonedDateTime now = Dates.nowUtc(context.clock());

        if(null != arguments.alertId) {
            context.reply(List.of(listOneAlert(context.noMoreArgs(), now, arguments.alertId)), responseTtlSeconds);
        } else {
            if(null != arguments.ownerId) {
                context.reply(listByOwnerAndPair(context, now, arguments), responseTtlSeconds);
            } else {
                switch (arguments.selection) {
                    case LIST_SETTINGS : context.reply(settings(context.locale, context.timezone), responseTtlSeconds); break; // list user settings
                    case LIST_LOCALES: context.reply(locales(), responseTtlSeconds); break; // list available timezones
                    case LIST_TIMEZONES: context.reply(timezones(), responseTtlSeconds); break; // list available timezones
                    case LIST_EXCHANGES : context.reply(exchanges(), responseTtlSeconds); break; // list exchanges and pairs
                    case LIST_ALL : context.reply(listAll(context, now, arguments), responseTtlSeconds); break;  // list all alerts
                    default : context.reply(listByTickerOrPair(context, now, arguments), responseTtlSeconds);
                }
            }
        }
    }

    static Arguments arguments(@NotNull CommandContext context) {
        Long alertId = context.args.getLong(SELECTION_ARGUMENT).orElse(null);
        if (null != alertId) {
            context.noMoreArgs();
            return new Arguments(alertId, null, null, null, null, null);
        }
        var owner = context.args.getUserId(SELECTION_ARGUMENT).orElse(null);
        var type = context.args.getType(TYPE_ARGUMENT).orElse(null);
        var offset = null == owner ? null : context.args.getLong(OFFSET_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(null);
        var tickerOrPair = null == owner ? null : context.args.getString(TICKER_PAIR_ARGUMENT).map(String::toUpperCase).orElse(null);
        offset = null == owner || null != offset ? offset : context.args.getLong(OFFSET_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(0L);
        var selection = null == owner ? context.args.getString(SELECTION_ARGUMENT).map(String::toLowerCase).orElse(LIST_ALL) : null;
        offset = null == owner ? context.args.getLong(OFFSET_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(0L) : offset;
        context.noMoreArgs();
        return new Arguments(null, type, selection, owner, tickerOrPair, offset);
    }

    private Message listOneAlert(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId) {
        return context.transactional(txCtx -> {
            Message[] message = new Message[1];
            var answer = securedAlertAccess(alertId, context, (alert, alertsDao) -> {
                if(sameUserOrAdmin(context, alert.userId)) {
                    message[0] = toMessageWithEdit(context, now, alert, 0L, 0L);
                } else {
                    message[0] = Message.of(toMessage(context, now, alert, 0L, 0L));
                }
                return embedBuilder(""); // ignored
            });
            return null != message[0] ? message[0] : Message.of(answer);
        });
    }

    private List<Message> listAll(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Arguments arguments) {
        var filter = isPrivateChannel(context) ?
                SelectionFilter.ofUser(context.user.getIdLong(), arguments.type) :
                SelectionFilter.ofServer(context.serverId(), arguments.type);
        return listAlerts(context, now, filter, arguments.offset, false,
                //TODO ajouter type dans la description
                () -> "list all " + (arguments.offset + MESSAGE_PAGE_SIZE - 1),
                () -> arguments.offset > 0 ? " for offset : " + arguments.offset : "");
    }

    private List<Message> listByOwnerAndPair(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Arguments arguments) {
        if(null == context.member && !sameUser(context.user, arguments.ownerId)) {
            return List.of(Message.of(embedBuilder(NAME, DENIED_COLOR, "You are not allowed to see alerts of members in a private channel")));
        }
        var filter = isPrivateChannel(context) ?
                SelectionFilter.ofUser(context.user.getIdLong(), arguments.type).withTickerOrPair(arguments.tickerOrPair) :
                SelectionFilter.of(context.serverId(), arguments.ownerId, arguments.type).withTickerOrPair(arguments.tickerOrPair);
        return listAlerts(context, now, filter, arguments.offset, true,
                () -> "list <@" + arguments.ownerId + '>' +
                        (null != arguments.tickerOrPair ? ' ' + arguments.tickerOrPair : "") + " " + (arguments.offset + MESSAGE_LIST_CHUNK - 1),
                () -> " for user <@" + arguments.ownerId + (null != arguments.tickerOrPair ? "> and '" + arguments.tickerOrPair : ">") +
                        (arguments.offset > 0 ? "', and offset " + arguments.offset : "'"));
    }

    private List<Message> listByTickerOrPair(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Arguments arguments) {
        var filter = isPrivateChannel(context) ?
                SelectionFilter.ofUser(context.user.getIdLong(), arguments.type).withTickerOrPair(arguments.tickerOrPair.toUpperCase()) :
                SelectionFilter.ofServer(context.serverId(), arguments.type).withTickerOrPair(arguments.tickerOrPair.toUpperCase());
        return listAlerts(context, now, filter, arguments.offset, isPrivateChannel(context),
                () -> "list " + arguments.tickerOrPair + " " + (arguments.offset + MESSAGE_LIST_CHUNK - 1),
                () -> " for ticker or pair '" + arguments.tickerOrPair + (arguments.offset > 0 ? "', and offset : " + arguments.offset : "'"));
    }

    private List<Message> listAlerts(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull SelectionFilter filter, long offset, boolean editAll, @NotNull Supplier<String> nextCommand, @NotNull Supplier<String> command) {
        record AlertsTotal(@NotNull List<Alert> alerts, long total) {}
        var alertsTotal = context.transactional(txCtx -> {
            var dao = txCtx.alertsDao();
            long total = dao.countAlerts(filter);
            var alerts = dao.getAlertsOrderByPairUserIdId(filter, offset, MESSAGE_LIST_CHUNK + 1L);
            return new AlertsTotal(alerts, total);
        });
        var messages = toEditableMessages(context, alertsTotal.alerts, now, offset, alertsTotal.total, editAll);
        return paginatedAlerts(messages, offset, nextCommand, command);
    }

    private static ArrayList<Message> toEditableMessages(@NotNull CommandContext context, @NotNull List<Alert> alerts, @NotNull ZonedDateTime now, long offset, long total, boolean editAll) {
        editAll = editAll && (isPrivateChannel(context) || isAdminMember(context.member));
        // return list of message containing all the embeds (alerts) between each editable message that can contains only one embed
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
            return List.of(Message.of(embedBuilder("Alerts search", OK_COLOR,
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
            messages.add(Message.of(embedBuilder("...", OK_COLOR, "More results found, to get them use command with offset " +
                    (offset + MESSAGE_LIST_CHUNK - 1) + " : " + nextCommand.get())));
        }
        return messages;
    }

    private Message settings(@NotNull Locale locale, @Nullable ZoneId timezone) {
        return Message.of(embedBuilder(LIST_SETTINGS, OK_COLOR, "Your current settings :")
                .addField("locale", locale.toLanguageTag(), true)
                .addField("timezone", null != timezone ? timezone.getId() : "UTC", true));
    }

    private Message locales() {
        return Message.of(embedBuilder(" ", OK_COLOR, "Available locales :\n\n```" +
                Stream.of(DiscordLocale.values())
                        .filter(not(DiscordLocale.UNKNOWN::equals))
                        .map(locale -> {
                            String desc = locale.toLocale().toLanguageTag();
                            return desc + " ".repeat(10 - desc.length()) +"(" + locale.getNativeName() + ')';
                        })
                        .collect(joining("\n")) + "```"));
    }

    private List<Message> timezones() {
        var zoneIds = ZoneId.getAvailableZoneIds();
        zoneIds.addAll(SHORT_IDS.keySet());
        var zones = zoneIds.stream().sorted().toList();
        int splitIndex = zones.size() / 3;

        return List.of(Message.of(embedBuilder(" ", OK_COLOR, "Available time zones :\n\n>>> " +
                        "by offset : +HH:mm, -HH:mm,\n" + String.join(", ", zones.subList(0, splitIndex)))),
                Message.of(embedBuilder(" ", OK_COLOR, ">>> " + String.join(", ", zones.subList(splitIndex, 2 * splitIndex)))),
                Message.of(embedBuilder(" ", OK_COLOR, ">>> " + String.join(", ", zones.subList(2 * splitIndex, zones.size())))));
    }

    private Message exchanges() {
        return Message.of(embedBuilder(LIST_EXCHANGES, OK_COLOR, //TODO add pairs
                MarkdownUtil.quoteBlock("* " + String.join("\n* ", SUPPORTED_EXCHANGES))));
    }
}