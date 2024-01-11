package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.TrendAlert;
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

public final class TrendCommand extends CommandAdapter {

    public static final String NAME = "trend";
    static final String DESCRIPTION = "create a new trend alert on pair ticker1/ticker2, a trend is defined by two prices and two dates";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_TICKER_LENGTH),
            new OptionData(STRING, "ticker2", "the second ticker", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_TICKER_LENGTH),
            new OptionData(NUMBER, "from_price", "the first price", true)
                    .setMinValue(0d),
            new OptionData(STRING, "from_date", "the date of first price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(NUMBER, "to_price", "the second price", true)
                    .setMinValue(0d),
            new OptionData(STRING, "to_date", "the date of second price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(STRING, "message", "a message to show when the alert is triggered : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));


    public TrendCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String ticker1 = requireTickerLength(context.args.getMandatoryString("ticker1"));
        String ticker2 = requireTickerLength(context.args.getMandatoryString("ticker2"));
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("from_price"));
        ZonedDateTime fromDate = context.args.getMandatoryDateTime("from_date");
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("to_price"));
        ZonedDateTime toDate = context.args.getMandatoryDateTime("to_date");
        String message = requireAlertMessageLength(context.args.getLastArgs("message")
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("trend command - exchange : {}, ticker1 : {}, ticker2 : {}, from_price : {}, from_date : {}, to_price : {}, to_date : {}, message : {}",
                exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message);
        alertsDao.transactional(() -> context.reply(RESPONSE_TTL_SECONDS, trend(context, exchange, ticker1, ticker2, message, fromPrice, fromDate, toPrice, toDate)));
    }

    private EmbedBuilder trend(@NotNull CommandContext context, @NotNull String exchange,
                               @NotNull String ticker1, @NotNull String ticker2, @NotNull String message,
                               @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                               @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate) {

        if(fromDate.isAfter(toDate)) { // ensure correct order of prices
            ZonedDateTime swap = fromDate;
            fromDate = toDate;
            toDate = swap;
            BigDecimal value = fromPrice;
            fromPrice = toPrice;
            toPrice = value;
        }
        TrendAlert trendAlert = new TrendAlert(context.user.getIdLong(),
                context.getServerId(),
                exchange, ticker1, ticker2, message, fromPrice, toPrice, fromDate, toDate);

        long alertId = alertsDao.addAlert(trendAlert);

        String answer = context.user.getAsMention() + " New trend alert added with id " + alertId +
                "\n\n* pair : " + trendAlert.getSlashPair() + "\n* exchange : " + exchange +
                "\n* from price " + fromPrice + "\n* from date " + formatUTC(fromDate) +
                "\n* to price " + toPrice + "\n* to date " + formatUTC(toDate) +
                "\n* message : " + message +
                alertMessageTips(message, alertId);

        return embedBuilder(NAME, Color.green, answer);
    }
}
