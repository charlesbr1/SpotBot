package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.RangeAlert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on pair ticker1/ticker2, defined by two prices and two optional dates";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "exchange", "the exchange, like binance", true)
                            .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
                    option(STRING, "pair", "the pair, like EUR/USDT", true)
                            .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                    option(NUMBER, "low", "the low range price", true)
                            .setMinValue(0d),
                    option(NUMBER, "high", "the high range price", false) //TODO range with 1 price
                            .setMinValue(0d),
                    option(STRING, "from_date", "a date to start the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false),
                    option(STRING, "to_date", "a future date to end the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false),
                    option(STRING, "message", "a message to show when the alert is triggered : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", false)
                            .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));

    public RangeCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("low"));
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("high"));
        ZonedDateTime fromDate = context.args.getDateTime("from_date").orElse(null);
        ZonedDateTime toDate = null != fromDate ? context.args.getDateTime("to_date").map(ArgumentValidator::requireInFuture).orElse(null) : null;
        String message = requireAlertMessageLength(context.args.getLastArgs("message").orElse(""));

        LOGGER.debug("range command - exchange : {}, pair : {}, low : {}, high : {}, from_date : {}, to_date : {}, message : {}",
                exchange, pair, fromPrice, toPrice, fromDate, toDate, message);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, range(context, exchange, pair, message, fromPrice, toPrice, fromDate, toDate)));
    }

    private EmbedBuilder range(@NotNull CommandContext context, @NotNull String exchange,
                               @NotNull String pair, @NotNull String message,
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
                context.serverId(),
                exchange, pair, message, fromPrice, toPrice, fromDate, toDate);

        long alertId = alertsDao.addAlert(rangeAlert);

        String answer = context.user.getAsMention() + " New range alert added with id " + alertId +
                "\n\n* pair : " + rangeAlert.pair + "\n* exchange : " + exchange +
                "\n* low " + fromPrice + "\n* high " + toPrice +
                (null != fromDate ? "\n* from date " + formatUTC(fromDate) : "") +
                (null != toDate ? "\n* to date " + formatUTC(toDate) : "") +
                "\n* message : " + message +
                alertMessageTips(message, alertId);

        return embedBuilder(NAME, Color.green, answer);
    }
}
