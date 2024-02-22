package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.TrendAlert;
import org.sbot.utils.ArgumentValidator;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.TrendAlert.currentTrendPrice;
import static org.sbot.entities.chart.Ticker.formatPrice;
import static org.sbot.entities.chart.Ticker.getSymbol;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.*;

public final class TrendCommand extends CommandAdapter {

    static final String NAME = "trend";
    static final String DESCRIPTION = "create or check a trend alert on a pair, a trend is defined by two prices and two dates";
    private static final int RESPONSE_TTL_SECONDS = 180;

    static final List<OptionData> optionList = List.of(
            option(STRING, EXCHANGE_ARGUMENT, "the exchange, like binance", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).toList()),
            option(STRING, PAIR_ARGUMENT, "the pair, like EUR/USDT", true)
                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
            option(STRING, MESSAGE_ARGUMENT, "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH),
            option(NUMBER, FROM_PRICE, "the first price", true)
                    .setMinValue(0d),
            option(STRING, FROM_DATE_ARGUMENT, "the date of first price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true)
                    .setMinLength(NOW_ARGUMENT.length()),
            option(NUMBER, TO_PRICE, "the second price", true)
                    .setMinValue(0d),
            option(STRING, TO_DATE_ARGUMENT, "the date of second price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true)
                    .setMinLength(NOW_ARGUMENT.length()));

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("price", "get the computed trend price at a provided date").addOptions(
                            option(INTEGER, ALERT_ID_ARGUMENT, "id of one trend alert", true)
                                    .setMinValue(0),
                            option(STRING, DATE_ARGUMENT, "a date from where to compute the trend price", true)
                                    .setMinLength(NOW_ARGUMENT.length())),
                    new SubcommandData("create", "create a new trend alert").addOptions(optionList));


    public TrendCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var alertId = context.args.getLong(ALERT_ID_ARGUMENT).map(ArgumentValidator::requirePositive);
        if(alertId.isPresent()) {
            var date = context.args.getMandatoryDateTime(context.locale, context.timezone, context.clock(), DATE_ARGUMENT);
            LOGGER.debug("trend command - alert_id : {}, date : {}", alertId, date);
            context.noMoreArgs().reply(trendPrice(context, date, alertId.get()), responseTtlSeconds);
            return;
        }
        String exchange = requireSupportedExchange(context.args.getMandatoryString(EXCHANGE_ARGUMENT));
        String pair = requirePairFormat(context.args.getMandatoryString(PAIR_ARGUMENT).toUpperCase());
        var reversed = context.args.reversed();
        ZonedDateTime toDate = reversed.getMandatoryDateTime(context.locale, context.timezone, context.clock(), TO_DATE_ARGUMENT);
        BigDecimal toPrice = requirePositive(reversed.getMandatoryNumber(TO_PRICE));
        ZonedDateTime fromDate = reversed.getMandatoryDateTime(context.locale, context.timezone, context.clock(), FROM_DATE_ARGUMENT);
        BigDecimal fromPrice = requirePositive(reversed.getMandatoryNumber(FROM_PRICE));
        String message = requireAlertMessageMaxLength(reversed.getLastArgs(MESSAGE_ARGUMENT)
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("trend command - exchange : {}, pair : {}, from_price : {}, from_date : {}, to_price : {}, to_date : {}, message : {}",
                exchange, pair, fromPrice, fromDate, toPrice, toDate, message);
        context.reply(trend(context, exchange, pair, message, fromPrice, fromDate, toPrice, toDate), responseTtlSeconds);
    }

    private Message trend(@NotNull CommandContext context,
                          @NotNull String exchange, @NotNull String pair, @NotNull String message,
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
        var now = Dates.nowUtc(context.clock());
        TrendAlert trendAlert = new TrendAlert(NEW_ALERT_ID, context.user.getIdLong(),
                context.serverId(), now, // creation date
                now, // listening date
                exchange, pair, message, fromPrice, toPrice, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);

        return saveAlert(context, now, trendAlert);
    }

    private Message trendPrice(@NotNull CommandContext context, @NotNull ZonedDateTime date, long alertId) {
        var answer = context.transactional(txCtx -> securedAlertAccess(alertId, context, (alert, alertsDao) -> {
            if(trend == alert.type) {
                return embedBuilder("[" + alert.pair + "] at " + Dates.formatDiscord(date) + " : **" +
                        formatPrice(currentTrendPrice(date, alert.fromPrice, alert.toPrice, alert.fromDate, alert.toDate), alert.getTicker2()) + "**")
                        .addField("from price", alert.fromPrice.toPlainString() + ' ' + getSymbol(alert.getTicker2()), true)
                        .addField("from date", formatDiscord(alert.fromDate), true)
                        .addBlankField(true)
                        .addField("to price", alert.toPrice.toPlainString() + ' ' + getSymbol(alert.getTicker2()), true)
                        .addField("to date", formatDiscord(alert.toDate), true)
                        .addBlankField(true)
                        .addField("current trend price", formatPrice(currentTrendPrice(Dates.nowUtc(context.clock()), alert.fromPrice, alert.toPrice, alert.fromDate, alert.toDate), alert.getTicker2()), true);
            }
            throw new IllegalArgumentException("Alert " + alertId + " is not a trend alert");
        }));
        return Message.of(answer.setTitle(":face_with_monocle: Trend alert " + alertId));
    }
}
