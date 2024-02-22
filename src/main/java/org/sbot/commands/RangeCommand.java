package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.StringArgumentReader;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.RangeAlert;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;
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
                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
            option(STRING, MESSAGE_ARGUMENT, "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH),
            option(NUMBER, LOW_ARGUMENT, "the low range price", true)
                    .setMinValue(0d),
            option(NUMBER, HIGH_ARGUMENT, "the high range price", false)
                    .setMinValue(0d),
            option(STRING, FROM_DATE_ARGUMENT, "a date to start the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false)
                    .setMinLength(NOW_ARGUMENT.length()),
            option(STRING, TO_DATE_ARGUMENT, "a future date to end the box, UTC expected format : " + Dates.DATE_TIME_FORMAT, false)
                    .setMinLength(DATE_TIME_FORMAT.length()));

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(optionList);

    public RangeCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString(EXCHANGE_ARGUMENT));
        String pair = requirePairFormat(context.args.getMandatoryString(PAIR_ARGUMENT).toUpperCase());
        var reversed = context.args.reversed();
        boolean stringReader = context.args instanceof StringArgumentReader;
        ZonedDateTime toDate = reversed.getDateTime(context.locale, context.timezone, context.clock(), TO_DATE_ARGUMENT).orElse(null);
        ZonedDateTime fromDate = !stringReader || null != toDate ? reversed.getDateTime(context.locale, context.timezone, context.clock(), FROM_DATE_ARGUMENT).orElse(null) : null;
        BigDecimal toPrice = reversed.getNumber(HIGH_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(null);
        BigDecimal fromPrice = reversed.getNumber(LOW_ARGUMENT).map(ArgumentValidator::requirePositive).orElse(null);
        if (stringReader) { // optional arguments are rode backward, this need to ensure correct arguments mapping
            if(null != toDate && null == fromDate) { // both dates are optional, but fromDate is first
                fromDate = toDate;
                toDate = null;
            }
           if(null == fromPrice) { // toPrice mandatory, fromPrice optional
                fromPrice = toPrice;
            }
        } else if(null == toPrice) {
            toPrice = fromPrice;
        }
        if(null == fromPrice) {
            throw new IllegalArgumentException("Missing from_price");
        }
        ZonedDateTime now = Dates.nowUtc(context.clock());
        if(null != toDate) {
            requireInFuture(now, toDate);
        }
        String message = requireAlertMessageMaxLength(reversed.getLastArgs(MESSAGE_ARGUMENT)
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("range command - exchange : {}, pair : {}, low : {}, high : {}, from_date : {}, to_date : {}, message : {}",
                exchange, pair, fromPrice, toPrice, fromDate, toDate, message);
        context.reply(range(context, now, exchange, pair, message, fromPrice, requireNonNull(toPrice), fromDate, toDate), responseTtlSeconds);
    }

    private Message range(@NotNull CommandContext context, @NotNull ZonedDateTime now,
                          @NotNull String exchange, @NotNull String pair, @NotNull String message,
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
        RangeAlert rangeAlert = new RangeAlert(NEW_ALERT_ID, context.user.getIdLong(),
                context.serverId(), now, // creation date
                null != fromDate && fromDate.isAfter(now) ? fromDate : now, // listening date
                exchange, pair, message, fromPrice, toPrice, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
        return saveAlert(context, now, rangeAlert);
    }
}
