package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.StringArgumentReader;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.time.ZoneId.SHORT_IDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.commands.SecurityAccess.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;

public final class ListCommand extends CommandAdapter {

    static final String NAME = "list";
    static final String DESCRIPTION = "list the settings, exchanges and pairs, alerts (optionally filtered by user, type, pair or ticker)";
    private static final int RESPONSE_TTL_SECONDS = 300;

    static final int MESSAGE_LIST_CHUNK = 100;
    static final String LIST_SETTINGS = "settings";
    static final String LIST_LOCALES = "locales";
    static final String LIST_TIMEZONES = "timezones";
    static final String LIST_EXCHANGES = "exchanges";
    static final String LIST_USER = "user";
    static final String LIST_ALL = "all";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, SELECTION_ARGUMENT, "settings, exchanges, timezones, alert ids list, alert type, user, a ticker, a pair, 'all' if omitted", false),
                    option(STRING, TICKER_PAIR_ARGUMENT, "an optional search filter on a ticker or a pair if selection is an user or a type", false)
                            .setMinLength(TICKER_MIN_LENGTH).setMaxLength(PAIR_MAX_LENGTH),
                    option(STRING, TYPE_ARGUMENT, "type of alert to list (range, trend or remainder, ignored if selection is already a type)", false)
                            .addChoices(Stream.of(Type.values()).map(t -> new Choice(t.name(), t.name())).toList()),
                    option(INTEGER, OFFSET_ARGUMENT, "an offset from where to start the search (results are limited to " + MESSAGE_LIST_CHUNK + " alerts)", false)
                            .setMinValue(0));

    record Arguments(List<Long> alertIds, Type type, String selection, Long ownerId, String tickerOrPair, Long offset) {
        String asNextCommand(int delta) {
            return (null != ownerId ? "list <@" + ownerId + ">" : "list") +
                    Optional.ofNullable(tickerOrPair).map(" "::concat).orElse(null != ownerId ? "" : " all") +
                    (null != type ? " " + type : "") +
                   " " + ((null != offset ? offset : 0L) + delta);
        }

        String asDescription() {
            return (null != ownerId ? "for user <@" + ownerId + ">" : "for") +
                    Optional.ofNullable(tickerOrPair).map(" ticker or pair "::concat).orElse(null != ownerId ? "" : " all") +
                    (null != type ? " with type " + type : "") +
                    (Optional.ofNullable(offset).orElse(0L) > 0 ? ", and offset : " + offset : "");

        }
    }

    public ListCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        LOGGER.debug("{} command - user {}, server {}, arguments {}", NAME, context.user.getIdLong(), context.serverId(), arguments);
        ZonedDateTime now = Dates.nowUtc(context.clock());

        if(null != arguments.alertIds) {
            context.reply(arguments.alertIds.stream().map(alertId -> listOneAlert(context, now, alertId)).toList(), responseTtlSeconds);
        } else {
            if(null != arguments.ownerId) {
                context.reply(listByOwnerAndPair(context, now, arguments), responseTtlSeconds);
            } else {
                switch (arguments.selection) {
                    case LIST_SETTINGS : context.reply(settings(context.locale, context.timezone), responseTtlSeconds); break; // list user settings
                    case LIST_LOCALES: context.reply(locales(), responseTtlSeconds); break; // list available timezones
                    case LIST_TIMEZONES: context.reply(timezones(), responseTtlSeconds); break; // list available timezones
                    case LIST_EXCHANGES : context.reply(exchanges(), responseTtlSeconds); break; // list exchanges and pairs
                    default : context.reply(listByTickerOrPair(context, now, arguments), responseTtlSeconds);
                }
            }
        }
    }

    static Arguments arguments(@NotNull CommandContext context) {
        var idsReader = new StringArgumentReader(context.args.getLastArgs(SELECTION_ARGUMENT).orElse(""));
        Long alertId = idsReader.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(null);
        if (null != alertId) {
            List<Long> alertIds = new ArrayList<>();
            alertIds.add(alertId);
            while(idsReader.getLastArgs("").isPresent()) {
                alertIds.add(requirePositive(idsReader.getMandatoryLong(ALERT_ID_ARGUMENT)));
            }
            return new Arguments(alertIds, null, null, null, null, null);
        }
        UnaryOperator<Optional<Type>> readType = type -> type.or(() -> context.args.getType(TYPE_ARGUMENT));

        Optional<Long> owner = Optional.empty();
        Optional<Type> type = Optional.empty();
        Optional<Long> offset = Optional.empty();
        String tickerOrPair = null;
        var selection = context.args.getString(SELECTION_ARGUMENT).map(String::toLowerCase).orElse(null);
        if(null != selection) {
            if(List.of(LIST_SETTINGS, LIST_LOCALES, LIST_TIMEZONES, LIST_EXCHANGES).contains(selection)) {
                context.noMoreArgs();
                return new Arguments(null, null, selection, null, null, null);
            } else {
                owner = asUser(selection);
                type = asType(selection);
                if (owner.isPresent() || type.isPresent()) {
                    selection = owner.isPresent() ? LIST_USER : selection;
                    type = readType.apply(type);
                    offset = context.args.getLong(TYPE_ARGUMENT).map(ArgumentValidator::requirePositive);
                    type = readType.apply(type); // re-read if needed for string command
                    tickerOrPair = context.args.getString(TICKER_PAIR_ARGUMENT).map(ArgumentValidator::requireTickerPairLength).map(String::toUpperCase).orElse(null);
                } else if(!LIST_ALL.equals(selection)) {
                    tickerOrPair = requireTickerPairLength(selection).toUpperCase();
                }
            }
        } else {
            selection = LIST_ALL;
        }
        type = readType.apply(type);
        offset = offset.or(() -> context.args.getLong(OFFSET_ARGUMENT).map(ArgumentValidator::requirePositive));
        type = readType.apply(type); // string command do not enforce argument order
        context.noMoreArgs();
        return new Arguments(null, type.orElse(null), selection, owner.orElse(null), tickerOrPair, offset.orElse(0L));
    }

    private Message listOneAlert(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId) {
        return securedAlertAccess(alertId, context, (alert, alertsDao) -> {
            if(sameUserOrAdmin(context, alert.userId)) {
                return editableAlertMessage(context, now, alert, 0L, 0L);
            } else {
                return Message.of(decoratedAlertEmbed(context, now, alert, 0L, 0L));
            }
        });
    }

    private List<Message> listByTickerOrPair(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Arguments arguments) {
        var filter = isPrivateChannel(context) ?
                SelectionFilter.ofUser(context.clientType, context.user.getIdLong(), arguments.type).withTickerOrPair(arguments.tickerOrPair) :
                SelectionFilter.ofServer(context.clientType, context.serverId(), arguments.type).withTickerOrPair(arguments.tickerOrPair);
        return listAlerts(context, now, filter, arguments);
    }

    private List<Message> listByOwnerAndPair(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Arguments arguments) {
        if(isPrivateChannel(context) && !sameUser(context.user, arguments.ownerId)) {
            return List.of(Message.of(embedBuilder(NAME, DENIED_COLOR, "You are not allowed to see alerts of members in a private channel")));
        }
        var filter = isPrivateChannel(context) ?
                SelectionFilter.ofUser(context.clientType, context.user.getIdLong(), arguments.type).withTickerOrPair(arguments.tickerOrPair) :
                SelectionFilter.of(context.clientType, context.serverId(), arguments.ownerId, arguments.type).withTickerOrPair(arguments.tickerOrPair);
        return listAlerts(context, now, filter, arguments);
    }

    private static List<Message> listAlerts(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull SelectionFilter filter, @NotNull Arguments arguments) {
        record AlertsTotal(@NotNull List<Alert> alerts, long total) {}
        var alertsTotal = context.transactional(txCtx -> {
            var dao = txCtx.alertsDao();
            long total = dao.countAlerts(filter);
            var alerts = dao.getAlertsOrderByPairUserIdId(filter, arguments.offset, MESSAGE_LIST_CHUNK);
            return new AlertsTotal(alerts, total);
        });
        var messages = alertMessages(context, now, arguments, alertsTotal.alerts, alertsTotal.total);
        return paginatedAnswer(messages, arguments, alertsTotal.alerts.size(), alertsTotal.total);
    }

    // return list of message containing all the embeds (alerts, ordered) between each editable message that can contains only one alert embed
    private static ArrayList<Message> alertMessages(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Arguments arguments, @NotNull List<Alert> alerts, long total) {
        boolean adminEditable = null != arguments.ownerId && isAdminMember(context.member);
        boolean editable = null != arguments.tickerOrPair || null != arguments.type;
        long userId = context.user.getIdLong();
        var messages = new ArrayList<Message>();
        int i = 0;
        while (i < alerts.size()) {
            int nextEditableIndex = i;
            while (!adminEditable && nextEditableIndex < alerts.size() && (!editable || alerts.get(nextEditableIndex).userId != userId)) {
                nextEditableIndex++;
            }
            if(i == nextEditableIndex) {
                messages.add(editableAlertMessage(context, now, alerts.get(i), arguments.offset + ++i, total));
            } else {
                var embedBuilders = new ArrayList<EmbedBuilder>(nextEditableIndex - i);
                while (i < nextEditableIndex) {
                    embedBuilders.add(decoratedAlertEmbed(context, now, alerts.get(i), arguments.offset + ++i, total));
                }
                messages.add(Message.of(embedBuilders));
            }
        }
        return messages;
    }

    private static List<Message> paginatedAnswer(@NotNull ArrayList<Message> messages, @NotNull Arguments arguments, int nbAlerts, long total) {
        if (messages.isEmpty()) {
            return List.of(Message.of(embedBuilder("Alerts search", OK_COLOR,
                    "No alert found " + arguments.asDescription())));
        } else if(arguments.offset + nbAlerts < total) {
            messages.add(Message.of(embedBuilder("...", OK_COLOR, "More results found, to get them use command with offset " +
                    (arguments.offset + nbAlerts) + " : " + arguments.asNextCommand(messages.size()))));
        }
        return messages;
    }

    private static Message settings(@NotNull Locale locale, @Nullable ZoneId timezone) {
        return Message.of(embedBuilder(LIST_SETTINGS, OK_COLOR, "Your current settings :")
                .addField("locale", locale.toLanguageTag(), true)
                .addField("timezone", null != timezone ? timezone.getId() : "UTC", true));
    }

    private static Message locales() {
        return Message.of(embedBuilder(" ", OK_COLOR, "Available locales :\n\n" +
                MarkdownUtil.codeblock(Stream.of(DiscordLocale.values())
                        .filter(not(DiscordLocale.UNKNOWN::equals))
                        .map(locale -> {
                            String desc = locale.toLocale().toLanguageTag();
                            return desc + " ".repeat(10 - desc.length()) +"(" + locale.getNativeName() + ')';
                        })
                        .collect(joining("\n")))));
    }

    private static List<Message> timezones() {
        var zoneIds = ZoneId.getAvailableZoneIds();
        zoneIds.addAll(SHORT_IDS.keySet());
        var zones = zoneIds.stream().sorted().toList();
        int splitIndex = zones.size() / 3;

        return List.of(Message.of(embedBuilder(" ", OK_COLOR, "Available timezones :\n\n" +
                        MarkdownUtil.quoteBlock("by offset : +HH:mm, -HH:mm,\n" + String.join(", ", zones.subList(0, splitIndex))))),
                Message.of(embedBuilder(" ", OK_COLOR, MarkdownUtil.quoteBlock(String.join(", ", zones.subList(splitIndex, 2 * splitIndex))))),
                Message.of(embedBuilder(" ", OK_COLOR, MarkdownUtil.quoteBlock(String.join(", ", zones.subList(2 * splitIndex, zones.size()))))));
    }

    private static Message exchanges() {
        return Message.of(embedBuilder(LIST_EXCHANGES, OK_COLOR, "Available exchanges :\n\n" +//TODO add pairs
                MarkdownUtil.quoteBlock("* " + String.join("\n* ", SUPPORTED_EXCHANGES))));
    }
}