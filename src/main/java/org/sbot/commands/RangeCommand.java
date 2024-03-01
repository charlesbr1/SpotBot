package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.StringArgumentReader;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMAT;
import static org.sbot.utils.Dates.NOW_ARGUMENT;

public final class RangeCommand extends CommandAdapter {

    static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on a pair, defined by two prices and two optional dates";
    private static final int RESPONSE_TTL_SECONDS = 180;

    static final List<OptionData> optionList = List.of(
            option(STRING, EXCHANGE_ARGUMENT, "the exchange, like binance", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).toList()),
            option(STRING, PAIR_ARGUMENT, "the pair, like EUR/USDT", true)
                    .setMinLength(PAIR_MIN_LENGTH).setMaxLength(PAIR_MAX_LENGTH),
            option(STRING, MESSAGE_ARGUMENT, "a message to show when the alert is raised : add a link to your AT ! (" + MESSAGE_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(MESSAGE_MAX_LENGTH),
            option(NUMBER, LOW_ARGUMENT, "the low range price", true)
                    .setMinValue(0d),
            option(NUMBER, HIGH_ARGUMENT, "the high range price", false)
                    .setMinValue(0d),
            option(STRING, FROM_DATE_ARGUMENT, "a date to start the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false)
                    .setMinLength(NOW_ARGUMENT.length()),
            option(STRING, TO_DATE_ARGUMENT, "a future date to end the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false)
                    .setMinLength(DATE_TIME_FORMAT.length()));

    record Arguments(String exchange, String pair, String message, BigDecimal low, BigDecimal high, ZonedDateTime fromDate, ZonedDateTime toDate) {}

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(optionList);

    public RangeCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        ZonedDateTime now = Dates.nowUtc(context.clock());
        var arguments = arguments(context, now);
        LOGGER.debug("range command - {}", arguments);
        context.reply(range(context, now, arguments), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString(EXCHANGE_ARGUMENT));
        String pair = requirePairFormat(context.args.getMandatoryString(PAIR_ARGUMENT).toUpperCase());
        var reversed = context.args.reversed();
        boolean stringReader = context.args instanceof StringArgumentReader;
        ZonedDateTime toDate = reversed.getDateTime(context.locale, context.timezone, context.clock(), TO_DATE_ARGUMENT).orElse(null);
        ZonedDateTime fromDate = !stringReader || null != toDate ? reversed.getDateTime(context.locale, context.timezone, context.clock(), FROM_DATE_ARGUMENT).orElse(null) : null;
        BigDecimal high = reversed.getNumber(HIGH_ARGUMENT).map(ArgumentValidator::requirePrice).orElse(null);
        BigDecimal low = reversed.getNumber(LOW_ARGUMENT).map(ArgumentValidator::requirePrice).orElse(null);
        String message = requireAlertMessageMaxLength(reversed.getLastArgs(MESSAGE_ARGUMENT)
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        if (stringReader) { // optional arguments are rode backward, this need to ensure correct arguments mapping
            if(null != toDate && null == fromDate) { // both dates are optional, but fromDate is first
                fromDate = toDate;
                toDate = null;
            }
            if(null == low) { // toPrice mandatory, fromPrice optional
                low = high;
            }
        } else if(null == high) {
            high = low;
        }
        if(null == low) {
            throw new IllegalArgumentException("Missing low price");
        }
        if(null != fromDate) {
            requireInFuture(now, fromDate);
        }
        if(null != toDate) {
            requireInFuture(now.plusHours(1L), toDate);
        }
        return new Arguments(exchange, pair, message, low, high, fromDate, toDate);
    }

    private Message range(@NotNull CommandContext context, @NotNull ZonedDateTime now,
                          @NotNull Arguments arguments) {

        ZonedDateTime fromDate = arguments.fromDate;
        ZonedDateTime toDate = arguments.toDate;
        BigDecimal low = arguments.low;
        BigDecimal high = arguments.high;

        if(low.compareTo(high) > 0) { // ensure correct order of prices
            low = high;
            high = arguments.low;
        }
        if(null != fromDate && null != toDate && fromDate.isAfter(toDate)) { // same for dates
            fromDate = toDate;
            toDate = arguments.fromDate;
        }
        RangeAlert rangeAlert = new RangeAlert(NEW_ALERT_ID, context.user.getIdLong(),
                context.serverId(), now, // creation date
                null != fromDate && fromDate.isAfter(now) ? fromDate : now, // listening date
                arguments.exchange, arguments.pair, arguments.message, low, high, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
        return createdAlertMessage(context, now, saveAlert(context, rangeAlert));
    }
}
