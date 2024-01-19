package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.*;
import static org.sbot.discord.Discord.guildName;
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
        Runnable[] notificationCallBack = new Runnable[1];

        var updater = switch (field) {
            case CHOICE_MESSAGE -> message(context, alertId, notificationCallBack);
            case CHOICE_FROM_PRICE -> fromPrice(context, alertId, notificationCallBack);
            case CHOICE_TO_PRICE -> toPrice(context, alertId, notificationCallBack);
            case CHOICE_FROM_DATE -> fromDate(context, alertId, notificationCallBack);
            case CHOICE_TO_DATE -> toDate(context, alertId, notificationCallBack);
            case CHOICE_MARGIN -> margin(context, alertId, notificationCallBack);
            case CHOICE_REPEAT -> repeat(context, alertId, notificationCallBack);
            case CHOICE_SNOOZE -> snooze(context, alertId, notificationCallBack);
            default -> throw new IllegalArgumentException("Invalid field : " + field);
        };

        (CHOICE_MESSAGE.equals(field) ? context : context.noMoreArgs()).
                alertsDao.transactional(() -> {
                    AnswerColorSmiley answer = securedAlertAccess(alertId, context, updater);
                    context.reply(responseTtlSeconds, embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer()));
                });
        // perform user notification of its alerts being updated, if needed, once transaction is done.
        Optional.ofNullable(notificationCallBack[0]).ifPresent(Runnable::run);
    }

    private Function<Alert, String> fromPrice(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("value"));
        return alert -> {
            if(fromPrice.compareTo(alert.fromPrice) != 0) {
                context.alertsDao.updateFromPrice(alertId, alert.withFromPrice(fromPrice).fromPrice);
                ownerUpdateNotification(context, alertId, alert.userId, "from_price", fromPrice.toPlainString(), outNotificationCallBack);
                return "Value from_price of alert " + alertId + " updated to *" + fromPrice.toPlainString() + "*";
            }
            return "Alert " +  + alertId + ", from_price is already set to " + fromPrice.toPlainString();
        };
    }

    private Function<Alert, String> toPrice(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("value"));
        return alert -> {
            if(toPrice.compareTo(alert.toPrice) != 0) {
                context.alertsDao.updateToPrice(alertId, alert.withToPrice(toPrice).toPrice);
                ownerUpdateNotification(context, alertId, alert.userId, "to_price", toPrice.toPlainString(), outNotificationCallBack);
                return "Value to_price of alert " + alertId + " updated to *" + toPrice.toPlainString() + "*";
            }
            return "Alert " +  + alertId + ", to_price is already set to " + toPrice.toPlainString();
        };
    }

    private Function<Alert, String> fromDate(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        ZonedDateTime fromDate = context.args.getDateTime("value").orElse(null);
        return alert -> {
            if(null == fromDate && (alert.type == trend || alert.type == remainder)) {
                throw new IllegalArgumentException("Missing from_date value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
            } else if(alert.type == remainder) {
                requireInFuture(fromDate);
            }
            String date = null != fromDate ? formatUTC(fromDate) : "null";
            if((null != fromDate && null == alert.fromDate) ||
                    (null != fromDate && fromDate.compareTo(alert.fromDate) != 0) ||
                    (null == fromDate && null != alert.fromDate)) {
                context.alertsDao.updateFromDate(alertId, alert.withFromDate(fromDate).fromDate);
                ownerUpdateNotification(context, alertId, alert.userId, "from_date", date, outNotificationCallBack);
                return "Value from_date of alert " + alertId + " updated to *" + date + "*";
            }
            return "Alert " +  + alertId + ", from_date is already set to " + date;
        };
    }

    private Function<Alert, String> toDate(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        ZonedDateTime toDate = context.args.getDateTime("value").orElse(null);
        return alert -> {
            if(alert.type == trend) {
                if(null == toDate) {
                    throw new IllegalArgumentException("Missing to_date value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
                }
            } else if(null != toDate) {
                requireInFuture(toDate);
            }
            String date = null != toDate ? formatUTC(toDate) : "null";
            if((null != toDate && null == alert.toDate) ||
                    (null != toDate && toDate.compareTo(alert.toDate) != 0) ||
                    (null == toDate && null != alert.toDate)) {
                context.alertsDao.updateToDate(alertId, alert.withToDate(toDate).toDate);
                ownerUpdateNotification(context, alertId, alert.userId, "to_date", date, outNotificationCallBack);
                return "Value to_date of alert " + alertId + " updated to *" + date + "*";
            }
            return "Alert " +  + alertId + ", to_date is already set to " + date;
        };
    }

    private Function<Alert, String> message(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        String message = requireAlertMessageMaxLength(context.args.getLastArgs("value").orElse(""));
        return alert -> {
            context.alertsDao.updateMessage(alertId, alert.withMessage(message).message);
            ownerUpdateNotification(context, alertId, alert.userId, "message", message, outNotificationCallBack);
            return "Message of alert " + alertId + " updated to *" + message + "*" +
                    (remainder != alert.type ? alertMessageTips(message, alertId) : "");
        };
    }

    private Function<Alert, String> margin(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal margin = requirePositive(context.args.getMandatoryNumber("value"));
        return alert -> {
            context.alertsDao.updateMargin(alertId, alert.withMargin(margin).margin);
            ownerUpdateNotification(context, alertId, alert.userId, "margin", margin.toPlainString(), outNotificationCallBack);
            return "Margin of alert " + alertId + " updated to " + margin +
                    (hasMargin(margin) ? "" : " (disabled)");
        };
    }

    private Function<Alert, String> repeat(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        short repeat = requirePositiveShort(context.args.getMandatoryLong("value"));
        return alert -> {
            alert = alert.withLastTriggerMarginRepeat(hasRepeat(repeat) ? null : ZonedDateTime.now(), alert.margin, repeat);
            context.alertsDao.updateRepeatAndLastTrigger(alertId, alert.repeat, alert.lastTrigger);
            ownerUpdateNotification(context, alertId, alert.userId, "repeat", Short.toString(repeat), outNotificationCallBack);
            return "Repeat of alert " + alertId + " updated to " + repeat +
                    (!hasRepeat(repeat) ? " (disabled)" : ", the alert can be raise now");
        };
    }

    private Function<Alert, String> snooze(@NotNull CommandContext context, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        short snooze = requirePositiveShort(context.args.getMandatoryLong("value"));
        return alert -> {
            alert = alert.withLastTriggerRepeatSnooze(null, alert.repeat, 0 != snooze ? snooze : DEFAULT_SNOOZE_HOURS);
            context.alertsDao.updateSnoozeAndLastTrigger(alertId, alert.snooze, alert.lastTrigger);
            ownerUpdateNotification(context, alertId, alert.userId, "snooze", Short.toString(snooze), outNotificationCallBack);
            return "Snooze of alert " + alertId + " updated to " +
                    (0 != snooze ? snooze : "default " + DEFAULT_SNOOZE_HOURS) +
                    (snooze > 1 ? " hours" : " hour") + ", the alert can be raise now";
        };
    }

    private void ownerUpdateNotification(@NotNull CommandContext context, long alertId, long userId, @NotNull String field, @NotNull String newValue, @NotNull Runnable[] outNotificationCallBack) {
        if(context.user.getIdLong() != userId) {
            outNotificationCallBack[0] = () -> sendUpdateNotification(context, userId,
                    embedBuilder("Notice of alert update", Color.lightGray,
                "Your alert " + alertId + " was updated on guild " + guildName(requireNonNull(context.member).getGuild()) +
                        ", " + field + " = " + newValue));
        }
    }
}
