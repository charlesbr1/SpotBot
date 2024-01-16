package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao.UserIdServerIdType;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.*;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public final class UpdateCommand extends CommandAdapter {

    private static final String NAME = "update";
    static final String DESCRIPTION = "update an alert (only 'message' and 'from_date' fields can be updated on a remainder alert)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String CHOICE_MESSAGE = "message";
    private static final String CHOICE_FROM_PRICE = "from_price";
    private static final String CHOICE_TO_PRICE = "to_price";
    private static final String CHOICE_FROM_DATE = "from_date";
    private static final String CHOICE_TO_DATE = "to_date";
    private static final String CHOICE_MARGIN = "margin";
    private static final String CHOICE_REPEAT = "repeat";
    private static final String CHOICE_SNOOZE = "snooze";

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "field", "the field to update (" + CHOICE_MESSAGE + ", " + CHOICE_FROM_PRICE +
                            ", " + CHOICE_TO_PRICE + ", " + CHOICE_FROM_DATE + ", " + CHOICE_TO_DATE +
                            ", " + CHOICE_MARGIN + ", " + CHOICE_REPEAT + ", " + CHOICE_SNOOZE + ')', true)
                            .addChoice(CHOICE_MESSAGE, CHOICE_MESSAGE)
                            .addChoice(CHOICE_FROM_PRICE, CHOICE_FROM_PRICE)
                            .addChoice(CHOICE_TO_PRICE, CHOICE_TO_PRICE)
                            .addChoice(CHOICE_FROM_DATE, CHOICE_FROM_DATE)
                            .addChoice(CHOICE_TO_DATE, CHOICE_TO_DATE)
                            .addChoice(CHOICE_MARGIN, CHOICE_MARGIN)
                            .addChoice(CHOICE_REPEAT, CHOICE_REPEAT)
                            .addChoice(CHOICE_SNOOZE, CHOICE_SNOOZE),
                    option(INTEGER, "alert_id", "id of the alert", true)
                            .setMinValue(0),
                    option(STRING, "value", "a new value depending on the selected field : a price, a message, or an UTC date (" + Dates.DATE_TIME_FORMAT + ')', true)
                            .setMinLength(1));


    public UpdateCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String field = context.args.getMandatoryString("field");
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        LOGGER.debug("update command - field : {}, alert_id : {}, value : {}", field, alertId, context.args.getLastArgs("value").orElse(""));
        var updater = switch (field) {
            case CHOICE_MESSAGE -> message(context, alertId);
            case CHOICE_FROM_PRICE -> fromPrice(context, alertId);
            case CHOICE_TO_PRICE -> toPrice(context, alertId);
            case CHOICE_FROM_DATE -> fromDate(context, alertId);
            case CHOICE_TO_DATE -> toDate(context, alertId);
            case CHOICE_MARGIN -> margin(context, alertId);
            case CHOICE_REPEAT -> repeat(context, alertId);
            case CHOICE_SNOOZE -> snooze(context, alertId);
            default -> throw new IllegalArgumentException("Invalid field : " + field);
        };

        (CHOICE_MESSAGE.equals(field) ? context : context.noMoreArgs()).
                alertsDao.transactional(() -> {
                    AnswerColorSmiley answer = securedAlertAccess(alertId, context, updater);
                    context.reply(responseTtlSeconds, embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer()));
                });
    }

    private Function<UserIdServerIdType, String> fromPrice(@NotNull CommandContext context, long alertId) {
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("value").stripTrailingZeros());
        return alert -> {
            requireNotRemainder(alert.type(), "from_price");
            context.alertsDao.updateFromPrice(alertId, fromPrice);
            return "Value from_price of alert " + alertId + " updated to *" + fromPrice.toPlainString() + "*";
        };
    }

    private Function<UserIdServerIdType, String> toPrice(@NotNull CommandContext context, long alertId) {
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("value").stripTrailingZeros());
        return alert -> {
            requireNotRemainder(alert.type(), "to_price");
            context.alertsDao.updateToPrice(alertId, toPrice);
            return "Value to_price of alert " + alertId + " updated to *" + toPrice.toPlainString() + "*";
        };
    }

    private Function<UserIdServerIdType, String> fromDate(@NotNull CommandContext context, long alertId) {
        ZonedDateTime fromDate = context.args.getDateTime("value").orElse(null);
        return alert -> {
            if(alert.type() != range) {
                if(null == fromDate) {
                    throw new IllegalArgumentException("Missing from_date value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
                } else if(alert.type() == remainder) {
                    requireInFuture(fromDate);
                }
            }
            context.alertsDao.updateFromDate(alertId, fromDate);
            return "Value from_date of alert " + alertId + " updated to *" + (null != fromDate ? formatUTC(fromDate) : "null") + "*";
        };
    }

    private Function<UserIdServerIdType, String> toDate(@NotNull CommandContext context, long alertId) {
        ZonedDateTime toDate = context.args.getDateTime("value").orElse(null);
        return alert -> {
            requireNotRemainder(alert.type(), "to_date");
            if(alert.type() == trend) {
                if(null == toDate) {
                    throw new IllegalArgumentException("Missing to_date value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
                }
            } else if(null != toDate) {
                requireInFuture(toDate);
            }
            context.alertsDao.updateToDate(alertId, toDate);
            return "Value to_date of alert " + alertId + " updated to *" + (null != toDate ? formatUTC(toDate) : "null") + "*";
        };
    }

    private Function<UserIdServerIdType, String> message(@NotNull CommandContext context, long alertId) {
        String message = requireAlertMessageMaxLength(context.args.getLastArgs("value").orElse(""));
        return alert -> {
            context.alertsDao.updateMessage(alertId, message);
            return "Message of alert " + alertId + " updated to *" + message + "*" +
                    (remainder != alert.type() ? alertMessageTips(message, alertId) : "");
        };
    }

    private Function<UserIdServerIdType, String> margin(@NotNull CommandContext context, long alertId) {
        BigDecimal margin = requirePositive(context.args.getMandatoryNumber("value"));
        return alert -> {
            requireNotRemainder(alert.type(), "margin");
            context.alertsDao.updateMargin(alertId, margin);
            return "Margin of alert " + alertId + " updated to " + margin +
                    (hasMargin(margin) ? "" : " (disabled)");
        };
    }

    private Function<UserIdServerIdType, String> repeat(@NotNull CommandContext context, long alertId) {
        short repeat = requirePositiveShort(context.args.getMandatoryLong("value"));
        return alert -> {
            requireNotRemainder(alert.type(), "repeat");
            context.alertsDao.updateRepeatAndLastTrigger(alertId, repeat, hasRepeat(repeat) ? null : ZonedDateTime.now());
            return "Repeat of alert " + alertId + " updated to " + repeat +
                    (!hasRepeat(repeat) ? " (disabled)" : ", the alert can be raise now");
        };
    }

    private Function<UserIdServerIdType, String> snooze(@NotNull CommandContext context, long alertId) {
        short snooze = requirePositiveShort(context.args.getMandatoryLong("value"));
        return alert -> {
            requireNotRemainder(alert.type(), "snooze");
            context.alertsDao.updateSnoozeAndLastTrigger(alertId, 0 != snooze ? snooze : DEFAULT_SNOOZE_HOURS, null);
            return "Snooze of alert " + alertId + " updated to " +
                    (0 != snooze ? snooze : "default " + DEFAULT_SNOOZE_HOURS) +
                    (snooze > 1 ? " hours" : " hour") + ", the alert can be raise now";
        };
    }

}
