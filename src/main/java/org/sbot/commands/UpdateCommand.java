package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_POSITIVE_NUMBER;
import static org.sbot.commands.SecurityAccess.sameUser;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.services.dao.AlertsDao.UpdateField.*;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatDiscord;

public final class UpdateCommand extends CommandAdapter {

    private static final String NAME = "update";
    static final String DESCRIPTION = "update your settings or an alert";
    private static final int RESPONSE_TTL_SECONDS = 30;

    // user settings
    public static final String CHOICE_LOCALE = "locale";
    public static final String CHOICE_TIMEZONE = "timezone";

    // alert fields
    public static final String CHOICE_FROM_PRICE = FROM_PRICE_ARGUMENT;
    public static final String CHOICE_LOW = LOW_ARGUMENT;
    public static final String CHOICE_TO_PRICE = TO_PRICE_ARGUMENT;
    public static final String CHOICE_HIGH = HIGH_ARGUMENT;
    public static final String CHOICE_FROM_DATE = FROM_DATE_ARGUMENT;
    public static final String CHOICE_DATE = DATE_ARGUMENT;
    public static final String CHOICE_TO_DATE = TO_DATE_ARGUMENT;
    public static final String CHOICE_MESSAGE = MESSAGE_ARGUMENT;
    public static final String CHOICE_MARGIN = "margin";
    public static final String CHOICE_REPEAT = "repeat";
    public static final String CHOICE_SNOOZE = "snooze";
    public static final String CHOICE_ENABLE = "enable";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("settings", "update your settings (locale and timezone)").addOptions(
                            option(STRING, SELECTION_ARGUMENT, CHOICE_LOCALE + ", " + CHOICE_TIMEZONE, true)
                                    .addChoice(CHOICE_LOCALE, CHOICE_LOCALE)
                                    .addChoice(CHOICE_TIMEZONE, CHOICE_TIMEZONE),
                            option(STRING, VALUE_ARGUMENT, "a new locale or timezone to use by default", true)
                                    .setMinLength(1)),
                    new SubcommandData("alert", "update an alert field (only 'message' and 'from_date' fields can be updated on a remainder alert)").addOptions(
                            option(STRING, SELECTION_ARGUMENT, CHOICE_MESSAGE + ", " + CHOICE_FROM_PRICE + ", " + CHOICE_LOW +
                            ", " + CHOICE_TO_PRICE + ", " + CHOICE_HIGH + ", " + CHOICE_FROM_DATE + ", " + CHOICE_DATE +
                            ", " + DISPLAY_TO_DATE + ", " + CHOICE_MARGIN + ", " + CHOICE_REPEAT + ", " + CHOICE_SNOOZE, true)
                            .addChoice(CHOICE_MESSAGE, CHOICE_MESSAGE)
                            .addChoice(DISPLAY_FROM_PRICE_OR_LOW, CHOICE_FROM_PRICE)
                            .addChoice(DISPLAY_TO_PRICE_OR_HIGH, CHOICE_TO_PRICE)
                            .addChoice(DISPLAY_FROM_DATE_OR_DATE, CHOICE_FROM_DATE)
                            .addChoice(DISPLAY_TO_DATE, CHOICE_TO_DATE)
                            .addChoice(CHOICE_MARGIN, CHOICE_MARGIN)
                            .addChoice(CHOICE_REPEAT, CHOICE_REPEAT)
                            .addChoice(CHOICE_SNOOZE, CHOICE_SNOOZE)
                            .addChoice(CHOICE_ENABLE, CHOICE_ENABLE),
                    option(INTEGER, ALERT_ID_ARGUMENT, "id of the alert", true)
                            .setMinValue(0).setMaxValue((long) MAX_POSITIVE_NUMBER),
                    option(STRING, VALUE_ARGUMENT, "a new value depending on the selected field : a price, a message, or an UTC date (" + Dates.DATE_TIME_FORMAT + ')', true)
                            .setMinLength(1)));

    record Arguments(String selection, String value, Long alertId) {}


    public UpdateCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("update command - {}, last args : {}", arguments, context.args.getLastArgs(VALUE_ARGUMENT).orElse(""));
        }
        if(List.of(CHOICE_LOCALE, CHOICE_TIMEZONE).contains(arguments.selection)) {
            var reply = CHOICE_LOCALE.equals(arguments.selection) ?
                    locale(context, arguments.value) :
                    timezone(context, arguments.value);
            context.reply(reply, responseTtlSeconds);
            return;
        }

        long alertId = requirePositive(context.args.getMandatoryLong(ALERT_ID_ARGUMENT));
        Runnable[] notificationCallBack = new Runnable[1];
        var now = Dates.nowUtc(context.clock());

        var updater = switch (arguments.selection) {
            case CHOICE_MESSAGE -> message(context, now, notificationCallBack);
            case CHOICE_FROM_PRICE, CHOICE_LOW -> fromPrice(context, now, notificationCallBack);
            case CHOICE_TO_PRICE, CHOICE_HIGH -> toPrice(context, now, notificationCallBack);
            case CHOICE_FROM_DATE, CHOICE_DATE -> fromDate(context, now, notificationCallBack);
            case CHOICE_TO_DATE -> toDate(context, now, notificationCallBack);
            case CHOICE_MARGIN -> margin(context, now, notificationCallBack);
            case CHOICE_REPEAT -> repeat(context, now, notificationCallBack);
            case CHOICE_SNOOZE -> snooze(context, now, notificationCallBack);
            case CHOICE_ENABLE -> enable(context, now, notificationCallBack);
            default -> throw new IllegalArgumentException("Invalid " + SELECTION_ARGUMENT + " : " + arguments.selection);
        };

        (CHOICE_MESSAGE.equals(arguments.selection) ? context : context.noMoreArgs())
                .reply(Message.of(securedAlertUpdate(alertId, context, updater)), responseTtlSeconds);
        // perform user notification of its alerts being updated, if needed, once above update transaction is successful.
        Optional.ofNullable(notificationCallBack[0]).ifPresent(Runnable::run);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        String selection = context.args.getMandatoryString(SELECTION_ARGUMENT);
        if(List.of(CHOICE_LOCALE, CHOICE_TIMEZONE).contains(selection)) {
            String value = context.args.getMandatoryString(VALUE_ARGUMENT);
            context.noMoreArgs();
            return new Arguments(selection, value, null);
        }
        long alertId = requirePositive(context.args.getMandatoryLong(ALERT_ID_ARGUMENT));
        return new Arguments(selection, null, alertId); // value is read by each updater
    }

    private Message locale(@NotNull CommandContext context, @NotNull String value) {
        var locale = requireSupportedLocale(value);
        return context.transactional(txCtx -> {
            if(txCtx.usersDao().userExists(context.user.getIdLong())) {
                txCtx.usersDao().updateLocale(context.user.getIdLong(), locale);
                return Message.of(embedBuilder(NAME, OK_COLOR, "Your locale is set to " + locale.toLanguageTag()));
            }
            return userSetupNeeded("Update locale", "Unable to save your locale :");
        });
    }

    private Message timezone(@NotNull CommandContext context, @NotNull String value) {
        var timezone = ZoneId.of(value);
        return context.transactional(txCtx -> {
            if(txCtx.usersDao().userExists(context.user.getIdLong())) {
                txCtx.usersDao().updateTimezone(context.user.getIdLong(), timezone);
                return Message.of(embedBuilder(NAME, OK_COLOR, "Your timezone is set to " + timezone.getId()));
            }
            return userSetupNeeded("Update timezone", "Unable to save your timezone :");
        });
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> message(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        String message = requireAlertMessageMaxLength(context.args.getLastArgs(VALUE_ARGUMENT).orElse(""));
        return (alert, alertsDao) -> { // message is not loaded into the provided alert, just update it everytime
            alert = alert.withMessage(message);
            alertsDao.update(alert, Set.of(MESSAGE));
            return updateNotifyMessage(context, now, alert, CHOICE_MESSAGE, message, outNotificationCallBack)
                    .appendDescription(remainder != alert.type ? alertMessageTips(message, alert.id) : "");
        };
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> fromPrice(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal fromPrice = requirePositive(context.args.getMandatoryNumber(VALUE_ARGUMENT));
        return update(context, now, (alert, alertsDao) -> {
            alert = alert.withFromPrice(fromPrice);
            alertsDao.update(alert, Set.of(FROM_PRICE));
            String fieldName = range == alert.type ? CHOICE_LOW : DISPLAY_FROM_PRICE;
            return updateNotifyMessage(context, now, alert, fieldName, fromPrice.toPlainString(), outNotificationCallBack);
        }, alert -> fromPrice.compareTo(alert.fromPrice) != 0);
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> toPrice(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal toPrice = requirePositive(context.args.getMandatoryNumber(VALUE_ARGUMENT));
        return update(context, now, (alert, alertsDao) -> {
            alert = alert.withToPrice(toPrice);
            alertsDao.update(alert, Set.of(TO_PRICE));
            String fieldName = range == alert.type ? CHOICE_HIGH : DISPLAY_TO_PRICE;
            return updateNotifyMessage(context, now, alert, fieldName, toPrice.toPlainString(), outNotificationCallBack);
        }, alert -> toPrice.compareTo(alert.toPrice) != 0);
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> fromDate(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        ZonedDateTime fromDate = context.args.getDateTime(context.locale, context.timezone, context.clock(), VALUE_ARGUMENT).orElse(null);
        return update(context, now, (alert, alertsDao) -> { // TODO handle "null" date
            validateDateArgument(context, now, alert.type, fromDate, "from_date");
            var fields = Set.of(FROM_DATE);
            if(!alert.isEnabled() || alert.type == trend) {
                alert = alert.withFromDate(fromDate);
            } else { //TODO faux si range et null == fromDate
                var listeningDate = listeningDate(alert.listeningDate, alert);
                alert = alert.withListeningDateFromDate(listeningDate, fromDate);
                fields = Set.of(FROM_DATE, LISTENING_DATE);
            }
            alertsDao.update(alert, fields);
            String fieldName = remainder == alert.type ? CHOICE_DATE : DISPLAY_FROM_DATE;
            String date = null != fromDate ? formatDiscord(fromDate) : "null";
            return updateNotifyMessage(context, now, alert, fieldName, date, outNotificationCallBack);
        }, alert -> notEquals(fromDate, alert.fromDate));
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> toDate(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        ZonedDateTime toDate = context.args.getDateTime(context.locale, context.timezone, context.clock(), VALUE_ARGUMENT).orElse(null);
        return update(context, now, (alert, alertsDao) -> {
            validateDateArgument(context, now, alert.type, toDate, "to_date");
            alert = alert.withToDate(toDate);
            alertsDao.update(alert, Set.of(TO_DATE));
            String date = null != toDate ? formatDiscord(toDate) : "null";
            return updateNotifyMessage(context, now, alert, DISPLAY_TO_DATE, date, outNotificationCallBack);
        }, alert -> notEquals(toDate, alert.toDate));
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> margin(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        BigDecimal margin = requirePositive(context.args.getMandatoryNumber(VALUE_ARGUMENT));
        return update(context, now, (alert, alertsDao) -> {
            alert = alert.withMargin(margin);
            alertsDao.update(alert, Set.of(MARGIN));
            return updateNotifyMessage(context, now, alert, CHOICE_MARGIN, margin.toPlainString(), outNotificationCallBack);
        }, alert -> margin.compareTo(alert.margin) != 0);
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> repeat(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        short repeat = requirePositiveShort(context.args.getMandatoryLong(VALUE_ARGUMENT));
        return update(context, now, (alert, alertsDao) -> {
            var listeningDate = listeningDate(now, alert);
            alert = alert.withListeningDateRepeat(repeat > 0 ? listeningDate : null, repeat);
            alertsDao.update(alert, Set.of(REPEAT, LISTENING_DATE));
            return updateNotifyMessage(context, now, alert, CHOICE_REPEAT, repeat + (!hasRepeat(repeat) ? " (disabled)" : ""), outNotificationCallBack)
                    .appendDescription(hasRepeat(repeat) ? "\n\nThis alert is enable" : ""); //TODO clean from modal
        }, alert -> repeat != alert.repeat);
    }

    private BiFunction<Alert, AlertsDao, EmbedBuilder> snooze(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        short snooze = requirePositiveShort(context.args.getMandatoryLong(VALUE_ARGUMENT));
        return update(context, now, (alert, alertsDao) -> {
            alert = alert.withSnooze(0 != snooze ? snooze : DEFAULT_SNOOZE_HOURS);
            alertsDao.update(alert, Set.of(SNOOZE));
            return updateNotifyMessage(context, now, alert, CHOICE_SNOOZE,
                    (0 != snooze ? String.valueOf(snooze) : "(default) " + DEFAULT_SNOOZE_HOURS) + (snooze > 1 ? " hours" : " hour"), outNotificationCallBack);
        }, alert -> snooze != alert.snooze);
    }

    public static final String UPDATE_ENABLED_HEADER = "Status set to **enabled** !\n\n";
    public static final String UPDATE_DISABLED_HEADER = "Status set to **disabled** !\n\n";

    private BiFunction<Alert, AlertsDao, EmbedBuilder> enable(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Runnable[] outNotificationCallBack) {
        boolean enable = Boolean.parseBoolean(context.args.getMandatoryString(VALUE_ARGUMENT));
        return update(context, now, (alert, alertsDao) -> {
            if(remainder == alert.type) {
                throw new IllegalArgumentException("Remainder alert can't be disabled, drop it instead");
            }
            var repeat = enable ? (alert.repeat <= 0 ? DEFAULT_REPEAT : alert.repeat) : (short) 0;
            alert = alert.withListeningDateRepeat(enable ? listeningDate(now, alert) : null, repeat);
            alertsDao.update(alert, Set.of(REPEAT, LISTENING_DATE));
            return updateNotifyMessage(context, now, alert, enable ? UPDATE_ENABLED_HEADER : UPDATE_DISABLED_HEADER, Boolean.toString(enable), CHOICE_ENABLE, outNotificationCallBack);
        }, alert -> enable != alert.isEnabled());
    }

    @NotNull
    private static BiFunction<Alert, AlertsDao, EmbedBuilder> update(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull BiFunction<Alert, AlertsDao, EmbedBuilder> updater, @NotNull Predicate<Alert> tester) {
        return (alert, alertsDao) -> {
            if(tester.test(alert)) {
                return updater.apply(alert, alertsDao);
            }
            return ListCommand.listAlert(context, now, alert);
        };
    }

    private static boolean notEquals(@Nullable ZonedDateTime date1, @Nullable ZonedDateTime date2) {
        return (null != date1 && null == date2) || (null == date1 && null != date2) ||
                (null != date1 && date1.compareTo(date2) != 0);
    }

    private static void validateDateArgument(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Type type, @Nullable ZonedDateTime date, @NotNull String field) {
        if(null == date  && (type != range || context.args.getString("") .isPresent())) { // badly formatted date
            throw new IllegalArgumentException("Missing " + field + " value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
        } else if(trend != type && null != date) {
            requireInFuture(now, date);
        }
    }

    static ZonedDateTime listeningDate(@Nullable ZonedDateTime listeningDate, @NotNull Alert alert) {
        if(alert.type == trend) {
            return Optional.ofNullable(alert.listeningDate).orElse(listeningDate);
        }
        if(null == alert.fromDate) {
            return listeningDate;
        }
        return alert.fromDate.isAfter(alert.creationDate) ? alert.fromDate : listeningDate;
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
        if(!sameUser(context.user, alert.userId)) {
            outNotificationCallBack[0] = () -> sendUpdateNotification(context, alert.userId,
                    Message.of(embedBuilder("Notice of alert update", NOTIFICATION_COLOR,
                "Your alert " + alert.id + " was updated on guild " + guildName(requireNonNull(context.member).getGuild()) +
                        ", " + field + " = " + newValue)));
        }
    }
}
