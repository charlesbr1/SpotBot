package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.RangeAlert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on pair ticker1/ticker2, defined by two prices and two optional dates";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_TICKER_LENGTH),
            new OptionData(STRING, "ticker2", "the second ticker", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_TICKER_LENGTH),
            new OptionData(NUMBER, "low", "the low range price", true)
                    .setMinValue(0d),
            new OptionData(NUMBER, "high", "the high range price", true)
                    .setMinValue(0d),
            new OptionData(STRING, "from_date", "a date to start the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false),
            new OptionData(STRING, "to_date", "a date to end the box (only if a start date is provided), UTC expected format : " + Dates.DATE_TIME_FORMAT, false),
            new OptionData(STRING, "message", "a message to show when the alert is triggered : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", false)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));

    public RangeCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String ticker1 = requireTickerLength(context.args.getMandatoryString("ticker1"));
        String ticker2 = requireTickerLength(context.args.getMandatoryString("ticker2"));
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("low"));
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("high"));
        ZonedDateTime fromDate = context.args.getDateTime("from_date").orElse(null);
        ZonedDateTime toDate = null != fromDate ? context.args.getDateTime("to_date").orElse(null) : null;
        String message = requireAlertMessageLength(context.args.getLastArgs("message").orElse(""));

        LOGGER.debug("range command - exchange : {}, ticker1 : {}, ticker2 : {}, low : {}, high : {}, from_date : {}, to_date : {}, message : {}",
                exchange, ticker1, ticker2, fromPrice, toPrice, fromDate, toDate, message);
        alertsDao.transactional(() -> context.reply(RESPONSE_TTL_SECONDS, range(context, exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate)));
    }

    private EmbedBuilder range(@NotNull CommandContext context, @NotNull String exchange,
                               @NotNull String ticker1, @NotNull String ticker2, @NotNull String message,
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
        RangeAlert rangeAlert = new RangeAlert(context.user.getIdLong(),
                context.getServerId(),
                exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate);

        long alertId = alertsDao.addAlert(rangeAlert);

        String answer = context.user.getAsMention() + " New range alert added with id " + alertId +
                "\n\n* pair : " + rangeAlert.getSlashPair() + "\n* exchange : " + exchange +
                "\n* low " + fromPrice + "\n* high " + toPrice +
                (null != fromDate ? "\n* from date " + formatUTC(fromDate) : "") +
                (null != toDate ? "\n* to date " + formatUTC(toDate) : "") +
                "\n* message : " + message +
                alertMessageTips(message, alertId);

        return embedBuilder(NAME, Color.green, answer);
    }
}
