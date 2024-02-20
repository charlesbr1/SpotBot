package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatDiscord;

public final class UpdateCommand extends CommandAdapter {

    private static final String NAME = "update";
    static final String DESCRIPTION = "update an alert (only 'message' and 'from_date' fields can be updated on a remainder alert)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String DISPLAY_FROM_PRICE_OR_LOW = "from price (or low)";
    public static final String DISPLAY_FROM_PRICE = "from price";
    public static final String CHOICE_FROM_PRICE = "from_price";
    public static final String CHOICE_LOW = "low";
    private static final String DISPLAY_TO_PRICE_OR_HIGH = "to price (or high)";
    public static final String DISPLAY_TO_PRICE = "to price";
    public static final String CHOICE_TO_PRICE = "to_price";
    public static final String CHOICE_HIGH = "high";
    public static final String DISPLAY_FROM_DATE = "from date";
    public static final String CHOICE_FROM_DATE = "from_date";
    public static final String DISPLAY_TO_DATE = "to date";
    public static final String CHOICE_TO_DATE = "to_date";
    public static final String CHOICE_MESSAGE = "message";
    public static final String CHOICE_MARGIN = "margin";
    public static final String CHOICE_REPEAT = "repeat";
    public static final String CHOICE_SNOOZE = "snooze";
    public static final String CHOICE_ENABLE = "enable";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "field", CHOICE_MESSAGE + ", " + DISPLAY_FROM_PRICE_OR_LOW +
                            ", " + DISPLAY_TO_PRICE_OR_HIGH + ", " + DISPLAY_FROM_DATE + ", " + DISPLAY_TO_DATE +
                            ", " + CHOICE_MARGIN + ", " + CHOICE_REPEAT + ", or " + CHOICE_SNOOZE, true)
                            .addChoice(CHOICE_MESSAGE, CHOICE_MESSAGE)
                            .addChoice(DISPLAY_FROM_PRICE_OR_LOW, CHOICE_FROM_PRICE)
                            .addChoice(DISPLAY_TO_PRICE_OR_HIGH, CHOICE_TO_PRICE)
                            .addChoice(DISPLAY_FROM_DATE, CHOICE_FROM_DATE)
                            .addChoice(DISPLAY_TO_DATE, CHOICE_TO_DATE)
                            .addChoice(CHOICE_MARGIN, CHOICE_MARGIN)
                            .addChoice(CHOICE_REPEAT, CHOICE_REPEAT)
                            .addChoice(CHOICE_SNOOZE, CHOICE_SNOOZE)
                            .addChoice(CHOICE_ENABLE, CHOICE_ENABLE),
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
        var now = Dates.nowUtc(context.clock());

        var updater = switch (field) {
            case CHOICE_MESSAGE -> message(context, now, alertId, notificationCallBack);
            case CHOICE_FROM_PRICE, CHOICE_LOW -> fromPrice(context, now, alertId, notificationCallBack);
            case CHOICE_TO_PRICE, CHOICE_HIGH -> toPrice(context, now, alertId, notificationCallBack);
            case CHOICE_FROM_DATE -> fromDate(context, now, alertId, notificationCallBack);
            case CHOICE_TO_DATE -> toDate(context, now, alertId, notificationCallBack);
            case CHOICE_MARGIN -> margin(context, now, alertId, notificationCallBack);
            case CHOICE_REPEAT -> repeat(context, now, alertId, notificationCallBack);
            case CHOICE_SNOOZE -> snooze(context, now, alertId, notificationCallBack);
            case CHOICE_ENABLE -> enable(context, now, alertId, notificationCallBack);
            default -> throw new IllegalArgumentException("Invalid field : " + field);
        };

        context.transaction(txCtx -> {
            var answer = securedAlertAccess(alertId, context, updater);
            (CHOICE_MESSAGE.equals(field) ? context : context.noMoreArgs()).reply(Message.of(answer), responseTtlSeconds);
        });
        // perform user notification of its alerts being updated, if needed, once above update transaction is done.
        Optional.ofNullable(notificationCallBack[0]).ifPresent(Runnable::run);
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> fromPrice(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber("value"));
        return (alert, alertsDao) -> {
            String fieldName = range == alert.type ? CHOICE_LOW : DISPLAY_FROM_PRICE;
            if(fromPrice.compareTo(alert.fromPrice) != 0) {
                alertsDao.updateFromPrice(alertId, (alert = alert.withFromPrice(fromPrice)).fromPrice);
                return updateNotifyMessage(context, now, alert, fieldName, fromPrice.toPlainString(), outNotificationCallBack);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> toPrice(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber("value"));
        return (alert, alertsDao) -> {
            String fieldName = range == alert.type ? CHOICE_HIGH : DISPLAY_TO_PRICE;
            if(toPrice.compareTo(alert.toPrice) != 0) {
                alertsDao.updateToPrice(alertId, (alert = alert.withToPrice(toPrice)).toPrice);
                return updateNotifyMessage(context, now, alert, fieldName, toPrice.toPlainString(), outNotificationCallBack);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> fromDate(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        ZonedDateTime fromDate = context.args.getDateTime(context.locale, context.clock(), "value").orElse(null);
        return (alert, alertsDao) -> {
            if(null == fromDate && (context.args.getString("") .isPresent() || (alert.type == trend || alert.type == remainder))) {
                throw new IllegalArgumentException("Missing from_date value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
            } else if(alert.type == remainder) {
                requireInFuture(now, fromDate);
            }
            String date = null != fromDate ? formatDiscord(fromDate) : "null";
            if((null != fromDate && null == alert.fromDate) ||
                    (null != fromDate && fromDate.compareTo(alert.fromDate) != 0) ||
                    (null == fromDate && null != alert.fromDate)) {
                //TODO update listening date accordingly
                alertsDao.updateFromDate(alertId, (alert = alert.withFromDate(fromDate)).fromDate);
                return updateNotifyMessage(context, now, alert, DISPLAY_FROM_DATE, date, outNotificationCallBack);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> toDate(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        ZonedDateTime toDate = context.args.getDateTime(context.locale, context.clock(), "value").orElse(null);
        return (alert, alertsDao) -> {
            if(null == toDate  && (context.args.getString("") .isPresent() || alert.type == trend)) {
                throw new IllegalArgumentException("Missing to_date value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
            } else if(null != toDate) {
                requireInFuture(now, toDate);
            }
            String date = null != toDate ? formatDiscord(toDate) : "null";
            if((null != toDate && null == alert.toDate) ||
                    (null != toDate && toDate.compareTo(alert.toDate) != 0) ||
                    (null == toDate && null != alert.toDate)) {
                alertsDao.updateToDate(alertId, (alert = alert.withToDate(toDate)).toDate);
                return updateNotifyMessage(context, now, alert, DISPLAY_TO_DATE, date, outNotificationCallBack);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> message(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        String message = requireAlertMessageMaxLength(context.args.getLastArgs("value").orElse(""));
        return (alert, alertsDao) -> {
            alertsDao.updateMessage(alertId, (alert = alert.withMessage(message)).message);
            return updateNotifyMessage(context, now, alert, CHOICE_MESSAGE, message, outNotificationCallBack)
                    .appendDescription(remainder != alert.type ? alertMessageTips(message, alertId) : "");
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> margin(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal margin = requirePositive(context.args.getMandatoryNumber("value"));
        return (alert, alertsDao) -> {
            if(margin.compareTo(alert.margin) != 0) {
                alert = alert.withMargin(margin);
                alertsDao.updateMargin(alertId, alert.margin);
                return updateNotifyMessage(context, now, alert, CHOICE_MARGIN, margin.toPlainString(), outNotificationCallBack);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> repeat(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        short repeat = requirePositiveShort(context.args.getMandatoryLong("value"));
        return (alert, alertsDao) -> {
            alert = alert.withListeningDateRepeat(repeat > 0 ? now : null, repeat);
            alertsDao.updateListeningDateRepeat(alertId, alert.listeningDate, alert.repeat);
            return updateNotifyMessage(context, now, alert, CHOICE_REPEAT, repeat + (!hasRepeat(repeat) ? " (disabled)" : ""), outNotificationCallBack)
                    .appendDescription(hasRepeat(repeat) ? "\n\nThis alert can be raise now" : "");
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> snooze(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        short snooze = requirePositiveShort(context.args.getMandatoryLong("value"));
        return (alert, alertsDao) -> {
            alert = alert.withListeningDateSnooze(now, 0 != snooze ? snooze : DEFAULT_SNOOZE_HOURS);
            alertsDao.updateListeningDateSnooze(alertId, alert.listeningDate, alert.snooze);
            return updateNotifyMessage(context, now, alert, CHOICE_SNOOZE,
                    (0 != snooze ? "" + snooze : "(default) " + DEFAULT_SNOOZE_HOURS) + (snooze > 1 ? " hours" : " hour"), outNotificationCallBack);
        };
    }

    public static final String UPDATE_ENABLED_HEADER = "Status set to **enabled** !\n\n";
    public static final String UPDATE_DISABLED_HEADER = "Status set to **disabled** !\n\n";

    private BiFunction<Alert, AlertsDao, EmbedBuilder> enable(@NotNull CommandContext context, @NotNull ZonedDateTime now, long alertId, @NotNull Runnable[] outNotificationCallBack) {
        boolean enable = Boolean.parseBoolean(context.args.getMandatoryString("value"));
        return (alert, alertsDao) -> {
            if(enable != alert.isEnabled()) {
                short repeat = enable && alert.repeat <= 0 ? (remainder == alert.type ? REMAINDER_DEFAULT_REPEAT : DEFAULT_REPEAT) : (enable ? alert.repeat : 0);
                alert = alert.withListeningDateRepeat(enable ? now : null, repeat);
                alertsDao.updateListeningDateRepeat(alertId, alert.listeningDate, alert.repeat);
                return updateNotifyMessage(context, now, alert, enable ? UPDATE_ENABLED_HEADER : UPDATE_DISABLED_HEADER, Boolean.toString(enable), CHOICE_ENABLE, outNotificationCallBack);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private EmbedBuilder updateNotifyMessage(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, @NotNull String fieldName, @NotNull String newValue, @NotNull Runnable[] outNotificationCallBack) {
        return updateNotifyMessage(context, now, alert, null, fieldName, newValue, outNotificationCallBack);
    }

    public static final String UPDATE_SUCCESS_FOOTER = "** updated !\n\n";

    private EmbedBuilder updateNotifyMessage(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, @Nullable String altFieldName, @NotNull String fieldName, @NotNull String newValue, @NotNull Runnable[] outNotificationCallBack) {
        ownerUpdateNotification(context, alert, fieldName, newValue, outNotificationCallBack);
        var embed = ListCommand.listAlert(context, now, alert);
        return embed.setDescription((null != altFieldName ? altFieldName : "Field **" + requireNonNull(fieldName) + UPDATE_SUCCESS_FOOTER) + embed.getDescriptionBuilder());
    }

    private void ownerUpdateNotification(@NotNull CommandContext context, @NotNull Alert alert, @NotNull String field, @NotNull String newValue, @NotNull Runnable[] outNotificationCallBack) {
        if(context.user.getIdLong() != alert.userId) {
            outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId,
                    Message.of(embedBuilder("Notice of alert update", Color.lightGray,
                "Your alert " + alert.id + " was updated on guild " + guildName(requireNonNull(context.member).getGuild()) +
                        ", " + field + " = " + newValue)));
        }
    }
}
