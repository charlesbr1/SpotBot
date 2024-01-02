package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.TrendAlert;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
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
            new OptionData(STRING, "message", "a message to display when the alert is triggered", false));


    public TrendCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        String exchange = command.args.getMandatoryString("exchange");
        String ticker1 = command.args.getMandatoryString("ticker1");
        String ticker2 = command.args.getMandatoryString("ticker2");
        BigDecimal fromPrice = requirePositive(command.args.getMandatoryNumber("from_price"));
        ZonedDateTime fromDate = command.args.getMandatoryDateTime("from_date");
        BigDecimal toPrice = requirePositive(command.args.getMandatoryNumber("to_price"));
        ZonedDateTime toDate = command.args.getMandatoryDateTime("to_date");
        String message = command.args.getLastArgs("message").orElse("");
        LOGGER.debug("trend command - exchange : {}, ticker1 : {}, ticker2 : {}, from_price : {}, from_date : {}, to_price : {}, to_date : {}, message : {}",
                exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message);
        command.reply(trend(command, exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message));
    }

    private EmbedBuilder trend(@NotNull Command command, @NotNull String exchange,
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
        TrendAlert trendAlert = new TrendAlert(command.user.getIdLong(),
                command.getServerId(),
                exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message);

        alertStorage.addAlert(trendAlert);

        String answer = "New trend alert added with id " + trendAlert.id +
                " on pair " + trendAlert.getSlashPair() + " on exchange " + exchange + ". From price " + fromPrice +
                " at " + formatUTC(fromDate) + " to price: " + toPrice + " at " + formatUTC(toDate);

        return embedBuilder(NAME, Color.green, answer);
    }
}
