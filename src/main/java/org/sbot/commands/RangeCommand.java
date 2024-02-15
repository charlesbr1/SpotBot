package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.StringArgumentReader;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public final class RangeCommand extends CommandAdapter {

    static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on a pair, defined by two prices and two optional dates";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "exchange", "the exchange, like binance", true)
                            .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
                    option(STRING, "pair", "the pair, like EUR/USDT", true)
                            .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                    option(NUMBER, "low", "the low range price", true)
                            .setMinValue(0d),
                    option(NUMBER, "high", "the high range price", true) //TODO range with 1 price
                            .setMinValue(0d),
                    option(STRING, "message", "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                            .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH),
                    option(STRING, "from_date", "a date to start the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false),
                    option(STRING, "to_date", "a future date to end the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false));

    public RangeCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("low"));
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("high"));
        var reversed = context.args.reversed();
        ZonedDateTime toDate = reversed.getDateTime("to_date").orElse(null);
        ZonedDateTime fromDate = null != toDate ? reversed.getDateTime("from_date").orElse(null) : null;
        if (context.args instanceof StringArgumentReader && null != toDate && null == fromDate) {
            fromDate = toDate;
            toDate = null;
        }
        ZonedDateTime now = Dates.nowUtc(context.clock());
        if(null != toDate) {
            requireInFuture(now, toDate);
        }
        String message = requireAlertMessageMaxLength(reversed.getLastArgs("message").orElse(""));

        LOGGER.debug("range command - exchange : {}, pair : {}, low : {}, high : {}, from_date : {}, to_date : {}, message : {}",
                exchange, pair, fromPrice, toPrice, fromDate, toDate, message);
        var finalFromDate = fromDate;
        var finalToDate = toDate;
        context.transaction(txCtx -> context.reply(range(context, now, txCtx.alertsDao(), exchange, pair, message, fromPrice, toPrice, finalFromDate, finalToDate), responseTtlSeconds));
    }

    private Message range(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull AlertsDao alertsDao,
                          @NotNull String exchange, @NotNull String pair, @NotNull String message,
                          @NotNull BigDecimal fromPrice, @NotNull BigDecimal toPrice,
                          @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate) {

        if(fromPrice.compareTo(toPrice) > 0) { // ensure correct order of prices
            BigDecimal swap = fromPrice;
            fromPrice = toPrice;
            toPrice = swap;
        }
        if(null != fromDate && null != toDate && fromDate.isAfter(toDate)) { // same for dates
            ZonedDateTime swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        RangeAlert rangeAlert = new RangeAlert(NEW_ALERT_ID, context.user.getIdLong(),
                context.serverId(), context.locale, now, // creation date
                null != fromDate ? fromDate : now, // listening date
                exchange, pair, message, fromPrice, toPrice, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);

        long alertId = alertsDao.addAlert(rangeAlert);

        String answer = context.user.getAsMention() + " New range alert added with id " + alertId +
                "\n\n* pair : " + rangeAlert.pair + "\n* exchange : " + exchange +
                "\n* low " + fromPrice + "\n* high " + toPrice +
                (null != fromDate ? "\n* from date " + formatUTC(fromDate) : "") +
                (null != toDate ? "\n* to date " + formatUTC(toDate) : "") +
                "\n* message : " + message +
                alertMessageTips(message, alertId);

        return Message.of(embedBuilder(NAME, Color.green, answer));
    }
}
