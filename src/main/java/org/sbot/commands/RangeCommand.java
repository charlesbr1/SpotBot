package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.RangeAlert;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on pair ticker1/ticker2, a range is defined by a low price and a high price";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true),
            new OptionData(STRING, "ticker2", "the second ticker", true),
            new OptionData(NUMBER, "low", "the low range price", true).setMinValue(0d),
            new OptionData(NUMBER, "high", "the high range price", true).setMinValue(0d),
            new OptionData(STRING, "message", "a message to display when the alert is triggered", false));

    public RangeCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        String exchange = command.args.getMandatoryString("exchange");
        String ticker1 = command.args.getMandatoryString("ticker1");
        String ticker2 = command.args.getMandatoryString("ticker2");
        BigDecimal low = requirePositive(command.args.getMandatoryNumber("low"));
        BigDecimal high = requirePositive(command.args.getMandatoryNumber("high"));
        String message = command.args.getLastArgs("message").orElse("");
        LOGGER.debug("range command - exchange : {}, ticker1 : {}, ticker2 : {}, low : {}, high : {}, message : {}",
                exchange, ticker1, ticker2, low, high, message);
        command.reply(range(command, exchange, ticker1, ticker2, low, high, message));
    }

    private EmbedBuilder range(@NotNull Command command, @NotNull String exchange,
                               @NotNull String ticker1, @NotNull String ticker2,
                               @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message) {

        if(low.compareTo(high) > 0) { // ensure correct order of prices
            BigDecimal swap = low;
            low = high;
            high = swap;
        }
        RangeAlert rangeAlert = new RangeAlert(command.user.getIdLong(),
                command.getServerId(),
                exchange, ticker1, ticker2, low, high, message);

        alertStorage.addAlert(rangeAlert);

        String answer = command.user.getAsMention() + " New range alert added with id " + rangeAlert.id +
                " on pair " + rangeAlert.getSlashPair() + " on exchange " + exchange + ". Box from " + low + " to " + high;

        return embedBuilder(NAME, Color.green, answer);
    }
}
