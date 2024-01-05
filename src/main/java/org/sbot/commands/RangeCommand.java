package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.RangeAlert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on pair ticker1/ticker2, a range is defined by a low price and a high price";

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
            new OptionData(STRING, "message", "a message to shown when the alert is triggered : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", false)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));

    public RangeCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String ticker1 = requireTickerLength(context.args.getMandatoryString("ticker1"));
        String ticker2 = requireTickerLength(context.args.getMandatoryString("ticker2"));
        BigDecimal low = requirePositive(context.args.getMandatoryNumber("low"));
        BigDecimal high = requirePositive(context.args.getMandatoryNumber("high"));
        String message = requireAlertMessageLength(context.args.getLastArgs("message").orElse(""));
        LOGGER.debug("range command - exchange : {}, ticker1 : {}, ticker2 : {}, low : {}, high : {}, message : {}",
                exchange, ticker1, ticker2, low, high, message);
        context.reply(range(context, exchange, ticker1, ticker2, low, high, message));
    }

    private EmbedBuilder range(@NotNull CommandContext context, @NotNull String exchange,
                               @NotNull String ticker1, @NotNull String ticker2,
                               @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message) {

        if(low.compareTo(high) > 0) { // ensure correct order of prices
            BigDecimal swap = low;
            low = high;
            high = swap;
        }
        RangeAlert rangeAlert = new RangeAlert(context.user.getIdLong(),
                context.getServerId(),
                exchange, ticker1, ticker2, low, high, message);

        alertStorage.addAlert(rangeAlert);

        String answer = context.user.getAsMention() + "\nNew range alert added with id " + rangeAlert.id +
                "\n* pair : " + rangeAlert.getSlashPair() + "\n* exchange : " + exchange +
                "\n* low " + low + "\n* high " + high +
                alertMessageTips(message, rangeAlert.id);

        return embedBuilder(NAME, Color.green, answer);
    }
}
