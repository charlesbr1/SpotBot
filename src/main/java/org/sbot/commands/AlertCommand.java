package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.SlashArgumentReader;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.utils.Dates;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;

public class AlertCommand extends CommandAdapter {

    private static final String NAME = "alert";
    static final String DESCRIPTION = "create a new alert (range, trend or remainder)";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData(RangeCommand.NAME, RangeCommand.DESCRIPTION).addOptions(
                            option(STRING, "exchange", "the exchange, like binance", true)
                                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Command.Choice(e, e)).collect(toList())),
                            option(STRING, "pair", "the pair, like EUR/USDT", true)
                                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            option(NUMBER, "low", "the low range price", true)
                                    .setMinValue(0d),
                            option(NUMBER, "high", "the high range price", true)
                                    .setMinValue(0d),
                            option(STRING, "message", "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH),
                            option(STRING, "from_date", "a date to start the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false),
                            option(STRING, "to_date", "a future date to end the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false)),
                    new SubcommandData(TrendCommand.NAME, TrendCommand.DESCRIPTION).addOptions(
                            option(STRING, "exchange", "the exchange, like binance", true)
                                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Command.Choice(e, e)).collect(toList())),
                            option(STRING, "pair", "the pair, like EUR/USDT", true)
                                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            option(NUMBER, "from_price", "the first price", true)
                                    .setMinValue(0d),
                            option(STRING, "from_date", "the date of first price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
                            option(NUMBER, "to_price", "the second price", true)
                                    .setMinValue(0d),
                            option(STRING, "to_date", "the date of second price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
                            option(STRING, "message", "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH)),
                    new SubcommandData(RemainderCommand.NAME, RemainderCommand.DESCRIPTION).addOptions(
                            option(STRING, "pair", "the pair, like EUR/USDT", true)
                                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            option(STRING, "date", "a future date when to trigger the remainder, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
                            option(STRING, "message", "a message for this remainder (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH)));

    public AlertCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Type type = getType(context);
        LOGGER.debug("alert command - type : {}", type);
        (switch (type) {
            case range -> new RangeCommand();
            case trend -> new TrendCommand();
            case remainder -> new RemainderCommand();
        }).onCommand(context);
    }

    private Type getType(@NotNull CommandContext context) {
        if(context.args instanceof SlashArgumentReader reader) {
            return switch (reader.getSubcommandName()) {
                case RangeCommand.NAME -> range;
                case TrendCommand.NAME -> trend;
                default -> remainder;
            };
        }
        return Type.valueOf(context.args.getMandatoryString("type"));
    }
}
