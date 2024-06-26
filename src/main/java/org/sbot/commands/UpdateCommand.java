package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.settings.UserSettings;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.notifications.UpdatedNotification;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_POSITIVE_NUMBER;
import static org.sbot.commands.SecurityAccess.sameUser;
import static org.sbot.commands.interactions.SelectEditInteraction.updateMenuOf;
import static org.sbot.entities.alerts.Alert.DEFAULT_REPEAT;
import static org.sbot.entities.alerts.Alert.Type;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.services.dao.AlertsDao.UpdateField.*;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatDiscord;
import static org.sbot.utils.Dates.parse;

public final class UpdateCommand extends CommandAdapter {

    static final String NAME = "update";
    static final String DESCRIPTION = "update your settings or an alert (admins are allowed to update member alerts)";
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

    private static final String NULL_DATE_ARGUMENT = "null";


    public static final String UPDATE_ENABLED_HEADER = "Status set to **enabled** !\n\n";
    public static final String UPDATE_DISABLED_HEADER = "Status set to **disabled** !\n\n";
    public static final String UPDATE_SUCCESS_FOOTER = "** updated by {author} !\n\n";
    public static final String UPDATE_SUCCESS_FOOTER_NO_AUTHOR = "** updated !\n\n";


    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("settings", "update your settings (locale and timezone)").addOptions(
                            option(STRING, SELECTION_ARGUMENT, "which setting to set : " + CHOICE_LOCALE + " or " + CHOICE_TIMEZONE, true)
                                    .addChoice(CHOICE_LOCALE, CHOICE_LOCALE)
                                    .addChoice(CHOICE_TIMEZONE, CHOICE_TIMEZONE),
                            option(STRING, VALUE_ARGUMENT, "a new locale or timezone to use by default", true)
                                    .setMinLength(1)),
                    new SubcommandData("alert", "update an alert field (only 'message', 'date', 'repeat', 'snooze' can be updated on a remainder)").addOptions(
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
                    option(INTEGER, ALERT_ID_ARGUMENT, "id of the alert to update", true)
                            .setMinValue(0).setMaxValue((long) MAX_POSITIVE_NUMBER),
                    option(STRING, VALUE_ARGUMENT, "a new value depending on the selected field : a price, a message, or a date (" + Dates.DATE_TIME_FORMAT + " now+h)", true)
                            .setMinLength(1)));

    record Arguments(String selection, String value, Long alertId) {}


    public UpdateCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("update command - user {}, server {}, arguments {}, last args : {}", context.userId, context.serverId(), arguments, context.args.getLastArgs(VALUE_ARGUMENT).orElse(""));
        }
        switch (arguments.selection) {
            case CHOICE_LOCALE -> context.reply(locale(context, arguments.value), responseTtlSeconds);
            case CHOICE_TIMEZONE -> context.reply(timezone(context, arguments.value), responseTtlSeconds);
            default -> updateField(context, arguments);
        }
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
            if (txCtx.userSettingsDao().userExists(context.clientType, context.userId)) {
                txCtx.userSettingsDao().updateUserLocale(context.clientType, context.userId, locale);
            } else { // string command does not provide user local and can't allow to setup an user account, excepted here where we got a locale
                txCtx.userSettingsDao().addSettings(UserSettings.ofDiscordUser(context.userId, locale, context.serverSettings.timezone(), Dates.nowUtc(context.clock())));
            }
            return Message.of(embedBuilder(NAME, OK_COLOR, "Your locale is set to " + locale.toLanguageTag()));
        });
    }

    private Message timezone(@NotNull CommandContext context, @NotNull String value) {
        var timezone = ZoneId.of(value, ZoneId.SHORT_IDS);
        return context.transactional(txCtx -> {
            switch (context.clientType) {
                case DISCORD:
                    if (txCtx.userSettingsDao().userExists(context.clientType, context.userId)) {
                        txCtx.userSettingsDao().updateUserTimezone(context.clientType, context.userId, timezone);
                        return Message.of(embedBuilder(NAME, OK_COLOR, "Your timezone is set to " + timezone.getId()));
                    }
            }
            return userSetupNeeded("Update timezone", "Unable to save your timezone :");
        });
    }

    private void updateField(@NotNull CommandContext context, @NotNull Arguments arguments) {
        var now = Dates.nowUtc(context.clock());
        context.reply(securedAlertUpdate(arguments.alertId, context,
                switch (arguments.selection) {
                    case CHOICE_MESSAGE -> message(context, now);
                    case CHOICE_FROM_PRICE, CHOICE_LOW -> fromPrice(context, now);
                    case CHOICE_TO_PRICE, CHOICE_HIGH -> toPrice(context, now);
                    case CHOICE_FROM_DATE -> fromDate(context, now, DISPLAY_FROM_DATE);
                    case CHOICE_DATE -> fromDate(context, now, CHOICE_DATE);
                    case CHOICE_TO_DATE -> toDate(context, now);
                    case CHOICE_MARGIN -> margin(context, now);
                    case CHOICE_REPEAT -> repeat(context, now);
                    case CHOICE_SNOOZE -> snooze(context, now);
                    case CHOICE_ENABLE -> enable(context, now);
                    default -> throw new IllegalArgumentException("Invalid " + SELECTION_ARGUMENT + " : " + arguments.selection);
                }), responseTtlSeconds);
    }

    @NotNull
    private static UpdateHandler update(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull UpdateHandler updater, @NotNull Predicate<Alert> tester) {
        return (alert, alertsDao, notificationsDao) -> {
            if(tester.test(alert)) {
                return updater.update(alert, alertsDao, notificationsDao);
            }
            return Message.of(alertEmbed(context, now, alert), ActionRow.of(updateMenuOf(alert)));
        };
    }

    @NotNull
    private UpdateHandler message(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        String message = requireAlertMessageMaxLength(context.args.getLastArgs(VALUE_ARGUMENT)
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));
        return (alert, alertsDao, notificationsDao) -> { // message is not loaded into the provided alert, just update it everytime
            alert = alert.withMessage(message);
            alertsDao.update(alert, Set.of(MESSAGE));
            var answer = updateNotifyMessage(context, now, alert, CHOICE_MESSAGE, message, notificationsDao);
            requireOneItem(answer.embeds()).appendDescription(remainder != alert.type ? alertMessageTips(message) : "");
            return answer;
        };
    }

    @NotNull
    private UpdateHandler fromPrice(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        BigDecimal fromPrice = requirePrice(context.args.getMandatoryNumber(VALUE_ARGUMENT));
        return update(context.noMoreArgs(), now, (alert, alertsDao, notificationsDao) -> {
            alert = alert.withFromPrice(fromPrice);
            alertsDao.update(alert, Set.of(FROM_PRICE));
            String displayName = range == alert.type ? CHOICE_LOW : DISPLAY_FROM_PRICE;
            return updateNotifyMessage(context, now, alert, displayName, fromPrice.toPlainString(), notificationsDao);
        }, alert -> fromPrice.compareTo(alert.fromPrice) != 0);
    }

    @NotNull
    private UpdateHandler toPrice(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        BigDecimal toPrice = requirePrice(context.args.getMandatoryNumber(VALUE_ARGUMENT));
        return update(context.noMoreArgs(), now, (alert, alertsDao, notificationsDao) -> {
            alert = alert.withToPrice(toPrice);
            alertsDao.update(alert, Set.of(TO_PRICE));
            String displayName = range == alert.type ? CHOICE_HIGH : DISPLAY_TO_PRICE;
            return updateNotifyMessage(context, now, alert, displayName, toPrice.toPlainString(), notificationsDao);
        }, alert -> toPrice.compareTo(alert.toPrice) != 0);
    }

    @NotNull
    private UpdateHandler fromDate(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull String displayName) {
        ZonedDateTime fromDate = readDate(context, displayName);
        return update(context, now, (alert, alertsDao, notificationsDao) -> {
            validateDateArgument(now, alert.type, fromDate, displayName);
            var fields = Set.of(FROM_DATE);
            if(trend == alert.type || (range == alert.type && !alert.isEnabled())) {
                alert = alert.withFromDate(fromDate);
            } else if (range == alert.type || alert.repeat >= 0) { // for range alert, this will also remove snoozing state
                alert = alert.withListeningDateFromDate(null != fromDate ? fromDate : now, fromDate);
                fields = Set.of(LISTENING_DATE, FROM_DATE);
            } else { // for disabled remainder alert, this will also reset repeat
                alert = alert.withListeningDateFromDate(fromDate, fromDate).withRepeat(REMAINDER_DEFAULT_REPEAT);
                fields = Set.of(LISTENING_DATE, FROM_DATE, REPEAT);
            }
            alertsDao.update(alert, fields);
            String date = null != fromDate ? formatDiscord(fromDate) : NULL_DATE_ARGUMENT;
            return updateNotifyMessage(context, now, alert, displayName, date, notificationsDao);
        }, alert -> notEquals(fromDate, alert.fromDate));
    }

    @NotNull
    private UpdateHandler toDate(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        ZonedDateTime toDate = readDate(context, DISPLAY_TO_DATE);
        return update(context, now, (alert, alertsDao, notificationsDao) -> {
            validateDateArgument(now, alert.type, toDate, DISPLAY_TO_DATE);
            alert = alert.withToDate(toDate);
            alertsDao.update(alert, Set.of(TO_DATE));
            String date = null != toDate ? formatDiscord(toDate) : NULL_DATE_ARGUMENT;
            return updateNotifyMessage(context, now, alert, DISPLAY_TO_DATE, date, notificationsDao);
        }, alert -> notEquals(toDate, alert.toDate));
    }

    @NotNull
    private UpdateHandler margin(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        BigDecimal margin = requirePrice(context.args.getMandatoryNumber(VALUE_ARGUMENT));
        return update(context.noMoreArgs(), now, (alert, alertsDao, notificationsDao) -> {
            alert = alert.withMargin(margin);
            alertsDao.update(alert, Set.of(MARGIN));
            return updateNotifyMessage(context, now, alert, CHOICE_MARGIN, margin.toPlainString(), notificationsDao);
        }, alert -> margin.compareTo(alert.margin) != 0);
    }

    @NotNull
    private UpdateHandler repeat(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        short repeat = requireRepeat(context.args.getMandatoryLong(VALUE_ARGUMENT));
        return update(context.noMoreArgs(), now, (alert, alertsDao, notificationsDao) -> {
            alert = alert.withRepeat(repeat);
            alertsDao.update(alert, Set.of(REPEAT));
            return updateNotifyMessage(context, now, alert, CHOICE_REPEAT, String.valueOf(repeat), notificationsDao);
        }, alert -> repeat != alert.repeat);
    }

    @NotNull
    private UpdateHandler snooze(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        short snooze = requireSnooze(context.args.getMandatoryLong(VALUE_ARGUMENT));
        return update(context.noMoreArgs(), now, (alert, alertsDao, notificationsDao) -> {
            var fields = Set.of(SNOOZE);
            if(alert.inSnooze(now)) {
                alert = alert.withListeningDateSnooze(requireNonNull(alert.listeningDate)
                        .plusHours((long) snooze - alert.snooze), snooze);
                fields = Set.of(LISTENING_DATE, SNOOZE);
            } else {
                alert = alert.withSnooze(snooze);
            }
            alertsDao.update(alert, fields);
            return updateNotifyMessage(context, now, alert, CHOICE_SNOOZE, String.valueOf(snooze) + (snooze > 1 ? " hours" : " hour"), notificationsDao);
        }, alert -> snooze != alert.snooze);
    }

    @NotNull
    private UpdateHandler enable(@NotNull CommandContext context, @NotNull ZonedDateTime now) {
        boolean enable = requireBoolean(context.args.getMandatoryString(VALUE_ARGUMENT), CHOICE_ENABLE);
        return update(context.noMoreArgs(), now, (alert, alertsDao, notificationsDao) -> {
            if(remainder == alert.type) {
                throw new IllegalArgumentException("Remainder alert can't be disabled, drop it instead");
            }
            var repeat = enable && alert.repeat < 0 ? DEFAULT_REPEAT : alert.repeat;
            alert = alert.withListeningDateRepeat(enable ? listeningDate(now, alert) : null, repeat);
            alertsDao.update(alert, Set.of(LISTENING_DATE, REPEAT));
            return updateNotifyMessage(context, now, alert, enable ? UPDATE_ENABLED_HEADER : UPDATE_DISABLED_HEADER, Boolean.toString(enable), CHOICE_ENABLE, notificationsDao);
        }, alert -> enable != alert.isEnabled());
    }

    static ZonedDateTime listeningDate(@NotNull ZonedDateTime now, @NotNull Alert alert) {
        requireNonNull(now);
        if(alert.type == trend) {
            return Optional.ofNullable(alert.listeningDate).orElse(now);
        }
        return null != alert.fromDate ? alert.fromDate : now;
    }

    static boolean notEquals(@Nullable ZonedDateTime inputDate, @Nullable ZonedDateTime alertDate) {
        return (null != inputDate && null == alertDate) || (null == inputDate && null != alertDate) ||
                (null != inputDate && !inputDate.isEqual(alertDate));
    }

    private static ZonedDateTime readDate(@NotNull CommandContext context, @NotNull String displayName) {
        String date = context.args.getLastArgs(VALUE_ARGUMENT)
                .orElseThrow(() -> new IllegalArgumentException("Missing '" + displayName + "' argument"));
        return NULL_DATE_ARGUMENT.equalsIgnoreCase(date) ? null :
                parse(context.locale, context.timezone, context.clock(), date);
    }

    static void validateDateArgument(@NotNull ZonedDateTime now, @NotNull Type type, @Nullable ZonedDateTime date, @NotNull String displayName) {
        if(null == date) {
            if(range != type) { // null value allowed for range type only
                throw new IllegalArgumentException("Missing " + displayName + " value, expected format : " + Dates.DATE_TIME_FORMAT + " UTC");
            }
        } else if(trend != type) { // range and remainder dates in future
            requireInFuture(remainder == type || DISPLAY_TO_DATE.equals(displayName) ? now.plusHours(1L) : now, date); // reject 'now' for to_date and remainder date
        }
    }

    private Message updateNotifyMessage(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, @NotNull String displayName, @NotNull String newValue, @NotNull Supplier<NotificationsDao> notificationsDao) {
        return updateNotifyMessage(context, now, alert, null, displayName, newValue, notificationsDao);
    }

    private Message updateNotifyMessage(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, @Nullable String altFieldName, @NotNull String displayName, @NotNull String newValue, @NotNull Supplier<NotificationsDao> notificationsDao) {
        ownerUpdateNotification(context, now, alert, displayName, newValue, notificationsDao);
        var embed = alertEmbed(context, now, alert);
        var updatedBy = sameUser(context, alert.userId) ? UPDATE_SUCCESS_FOOTER_NO_AUTHOR : UPDATE_SUCCESS_FOOTER.replace("{author}", "<@" + context.userId + ">");
        return Message.of(embed.setDescription((null != altFieldName ? altFieldName : "Field **" + requireNonNull(displayName) + updatedBy) + embed.getDescriptionBuilder()), ActionRow.of(updateMenuOf(alert)));
    }

    private void ownerUpdateNotification(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, @NotNull String field, @NotNull String newValue, @NotNull Supplier<NotificationsDao> notificationsDao) {
        if(!sameUser(context, alert.userId)) {
            var serverName = switch (context.clientType) {
                case DISCORD -> guildName(requireNonNull(context.discordMember).getGuild());
            };
            notificationsDao.get().addNotification(UpdatedNotification.of(context.clientType, now, context.locale, alert.userId, alert.id, field, newValue, serverName));
        }
    }
}
