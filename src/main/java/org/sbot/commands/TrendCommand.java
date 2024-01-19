package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.TrendAlert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendCommand extends CommandAdapter {

    private static final String NAME = "trend";
    static final String DESCRIPTION = "create a new trend alert on a pair, a trend is defined by two prices and two dates";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "exchange", "the exchange, like binance", true)
                            .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
                    option(STRING, "pair", "the pair, like EUR/USDT", true)
                            .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                    option(NUMBER, "from_price", "the first price", true)
                            .setMinValue(0d),
                    option(STRING, "from_date", "the date of first price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
                    option(NUMBER, "to_price", "the second price", true)
                            .setMinValue(0d),
                    option(STRING, "to_date", "the date of second price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
                    option(STRING, "message", "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                            .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));


    public TrendCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("from_price"));
        ZonedDateTime fromDate = context.args.getMandatoryDateTime("from_date");
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("to_price"));
        ZonedDateTime toDate = context.args.getMandatoryDateTime("to_date");
        String message = requireAlertMessageMaxLength(context.args.getLastArgs("message")
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("trend command - exchange : {}, pair : {}, from_price : {}, from_date : {}, to_price : {}, to_date : {}, message : {}",
                exchange, pair, fromPrice, fromDate, toPrice, toDate, message);
        context.alertsDao.transactional(() -> context.reply(responseTtlSeconds, trend(context, exchange, pair, message, fromPrice, fromDate, toPrice, toDate)));
    }

    private EmbedBuilder trend(@NotNull CommandContext context, @NotNull String exchange,
                               @NotNull String pair, @NotNull String message,
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
        //TODO if toPrice = fromPrice -> create range alert ?

        TrendAlert trendAlert = new TrendAlert(NULL_ALERT_ID, context.user.getIdLong(),
                context.serverId(),
                exchange, pair, message, fromPrice, toPrice, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);

        long alertId = context.alertsDao.addAlert(trendAlert);

        String answer = context.user.getAsMention() + " New trend alert added with id " + alertId +
                "\n\n* pair : " + trendAlert.pair + "\n* exchange : " + exchange +
                "\n* from price " + fromPrice + "\n* from date " + formatUTC(fromDate) +
                "\n* to price " + toPrice + "\n* to date " + formatUTC(toDate) +
                "\n* message : " + message +
                alertMessageTips(message, alertId);

        return embedBuilder(NAME, Color.green, answer);
    }
}
