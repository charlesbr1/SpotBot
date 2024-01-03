package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.TrendAlert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.ALERT_MESSAGE_ARG_MAX_LENGTH;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.requireMaxMessageArgLength;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendCommand extends CommandAdapter {

    public static final String NAME = "trend";
    static final String DESCRIPTION = "create a new trend alert on pair ticker1/ticker2, a trend is defined by two prices and two dates";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true),
            new OptionData(STRING, "ticker2", "the second ticker", true),
            new OptionData(NUMBER, "from_price", "the first price", true).setMinValue(0d),
            new OptionData(STRING, "from_date", "the date of first price, UTC expected, format: " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(NUMBER, "to_price", "the second price", true).setMinValue(0d),
            new OptionData(STRING, "to_date", "the date of second price, UTC expected, format: " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(STRING, "message", "a message to display when the alert is triggered : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", false)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));



    public TrendCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = context.args.getMandatoryString("exchange");
        String ticker1 = context.args.getMandatoryString("ticker1");
        String ticker2 = context.args.getMandatoryString("ticker2");
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("from_price"));
        ZonedDateTime fromDate = context.args.getMandatoryDateTime("from_date");
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("to_price"));
        ZonedDateTime toDate = context.args.getMandatoryDateTime("to_date");
        String message = requireMaxMessageArgLength(context.args.getLastArgs("message").orElse(""));
        LOGGER.debug("trend command - exchange : {}, ticker1 : {}, ticker2 : {}, from_price : {}, from_date : {}, to_price : {}, to_date : {}, message : {}",
                exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message);
        context.reply(trend(context, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message));
    }

    private EmbedBuilder trend(@NotNull CommandContext context, @NotNull String exchange,
                               @NotNull String ticker1, @NotNull String ticker2,
                               @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                               @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate, @NotNull String message) {

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
                exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message);

        alertStorage.addAlert(trendAlert);

        String answer = context.user.getAsMention() + "\nNew trend alert added with id " + trendAlert.id +
                "\n* pair : " + trendAlert.getSlashPair() + "\n* exchange : " + exchange +
                "\n* from price " + formatUTC(fromDate) + "\n* from date " + fromDate +
                "\n* to price " + toPrice + "\n* to date " + formatUTC(toDate);

        return embedBuilder(NAME, Color.green, answer);
    }
}
