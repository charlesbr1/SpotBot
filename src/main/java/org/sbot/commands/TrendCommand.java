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

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.TrendAlert.currentTrendPrice;
import static org.sbot.entities.chart.Ticker.formatPrice;
import static org.sbot.entities.chart.Ticker.getSymbol;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMAT;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendCommand extends CommandAdapter {

    static final String NAME = "trend";
    static final String DESCRIPTION = "create or check a trend alert on a pair, a trend is defined by two prices and two dates";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final List<OptionData> optionList = List.of(
            option(STRING, "exchange", "the exchange, like binance", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            option(STRING, "pair", "the pair, like EUR/USDT", true)
                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
            option(STRING, "message", "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH),
            option(NUMBER, "from_price", "the first price", true)
                    .setMinValue(0d),
            option(STRING, "from_date", "the date of first price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true)
                    .setMinLength(DATE_TIME_FORMAT.length()).setMaxLength(DATE_TIME_FORMAT.length()),
            option(NUMBER, "to_price", "the second price", true)
                    .setMinValue(0d),
            option(STRING, "to_date", "the date of second price, UTC expected format : " + Dates.DATE_TIME_FORMAT, true)
                    .setMinLength(DATE_TIME_FORMAT.length()).setMaxLength(DATE_TIME_FORMAT.length()));

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("price", "get the computed trend price at a provided date").addOptions(
                            option(INTEGER, "alert_id", "id of one trend alert", true)
                                    .setMinValue(0),
                            option(STRING, "date", "a date from where to compute the trend price", true)
                                    .setMinLength(DATE_TIME_FORMAT.length()).setMaxLength(DATE_TIME_FORMAT.length())),
                    new SubcommandData("create", "create a new trend alert").addOptions(optionList));


    public TrendCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var alertId = context.args.getLong("alert_id").map(ArgumentValidator::requirePositive);
        if(alertId.isPresent()) {
            var date = context.args.getMandatoryDateTime("date");
            LOGGER.debug("trend command - alert_id : {}, date : {}", alertId, date);
            context.noMoreArgs().reply(trendPrice(context, date, alertId.get()), responseTtlSeconds);
            return;
        }
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        var reversed = context.args.reversed();
        ZonedDateTime toDate = reversed.getMandatoryDateTime("to_date");
        BigDecimal toPrice = requirePositive(reversed.getMandatoryNumber("to_price"));
        ZonedDateTime fromDate = reversed.getMandatoryDateTime("from_date");
        BigDecimal fromPrice = requirePositive(reversed.getMandatoryNumber("from_price"));
        String message = requireAlertMessageMaxLength(reversed.getLastArgs("message")
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
                context.serverId(), context.locale, now, // creation date
                now, // listening date
                exchange, pair, message, fromPrice, toPrice, fromDate, toDate,
                null, MARGIN_DISABLED, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);

        long alertId = context.transactional(txCtx -> txCtx.alertsDao().addAlert(trendAlert));

        String answer = context.user.getAsMention() + " New trend alert added with id " + alertId +
                "\n\n* pair : " + trendAlert.pair + "\n* exchange : " + exchange +
                "\n* from price " + fromPrice + "\n* from date " + formatUTC(fromDate) +
                "\n* to price " + toPrice + "\n* to date " + formatUTC(toDate) +
                "\n* message : " + message +
                alertMessageTips(message, alertId);

        return Message.of(embedBuilder(NAME, Color.green, answer));
    }

    private Message trendPrice(@NotNull CommandContext context, @NotNull ZonedDateTime date, long alertId) {
        var answer = context.transactional(txCtx -> securedAlertAccess(alertId, context, (alert, alertsDao) -> {
            if(trend == alert.type) {
                return embedBuilder("[" + alert.pair + "] at " + Dates.formatUTC(date) + " : **" +
                        formatPrice(currentTrendPrice(date, alert.fromPrice, alert.toPrice, alert.fromDate, alert.toDate), alert.getTicker2()) + "**")
                        .addField("from price", alert.fromPrice.toPlainString() + ' ' + getSymbol(alert.getTicker2()), true)
                        .addField("from date", formatUTC(alert.fromDate), true)
                        .addBlankField(true)
                        .addField("to price", alert.toPrice.toPlainString() + ' ' + getSymbol(alert.getTicker2()), true)
                        .addField("to date", formatUTC(alert.toDate), true)
                        .addBlankField(true)
                        .addField("current trend price", formatPrice(currentTrendPrice(Dates.nowUtc(context.clock()), alert.fromPrice, alert.toPrice, alert.fromDate, alert.toDate), alert.getTicker2()), true);
            }
            throw new IllegalArgumentException("Alert " + alertId + " is not a trend alert");
        }));
        return Message.of(answer.setTitle(":face_with_monocle: Trend alert " + alertId));
    }
}
