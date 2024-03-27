package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.SpotBot;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.discord.CommandListener;
import org.sbot.services.discord.Discord;

import java.awt.Color;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static org.sbot.commands.SecurityAccess.sameUser;
import static org.sbot.commands.interactions.SelectEditInteraction.updateMenuOf;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.utils.ArgumentValidator.requireOneItem;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public abstract class CommandAdapter implements CommandListener {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    public static final String DISPLAY_FROM_PRICE = "from price";
    public static final String DISPLAY_TO_PRICE = "to price";
    public static final String DISPLAY_FROM_PRICE_OR_LOW = "from price (or low)";
    public static final String DISPLAY_TO_PRICE_OR_HIGH = "to price (or high)";
    public static final String DISPLAY_FROM_DATE_OR_DATE = "from date (or date)";
    public static final String DISPLAY_FROM_DATE = "from date";
    public static final String DISPLAY_TO_DATE = "to date";
    public static final String DISPLAY_CURRENT_TREND_PRICE = "current trend price";

    public static final String SELECTION_ARGUMENT = "selection";
    public static final String VALUE_ARGUMENT = "value";
    public static final String ALERT_ID_ARGUMENT = "alert_id";
    public static final String TYPE_ARGUMENT = "type";
    protected static final String TICKER_PAIR_ARGUMENT = "ticker_pair";
    protected static final String OFFSET_ARGUMENT = "offset";
    protected static final String OWNER_ARGUMENT = "owner";
    protected static final String EXCHANGE_ARGUMENT = "exchange";
    protected static final String PAIR_ARGUMENT = "pair";
    public static final String MESSAGE_ARGUMENT = "message";
    protected static final String LOW_ARGUMENT = "low";
    protected static final String HIGH_ARGUMENT = "high";
    protected static final String FROM_PRICE_ARGUMENT = "from_price";
    protected static final String TO_PRICE_ARGUMENT = "to_price";
    protected static final String DATE_ARGUMENT = "date";
    protected static final String FROM_DATE_ARGUMENT = "from_date";
    protected static final String TO_DATE_ARGUMENT = "to_date";

    public static final Color OK_COLOR = Color.green;
    public static final Color DENIED_COLOR = Color.orange;
    public static final Color NOT_FOUND_COLOR = Color.red;

    public static final String ALERT_TIPS = "Your message will be shown in the title of your alert notification.";
    public static final String ALERT_TITLE_PAIR_FOOTER = "] ";

    private final String name;
    private final String description;
    private final SlashCommandData options;
    protected final int responseTtlSeconds;

    protected CommandAdapter(@NotNull String name,@NotNull String description, @NotNull SlashCommandData options, int responseTtlSeconds) {
        this.name = requireNonNull(name, "missing CommandAdapter name");
        this.description = requireNonNull(description, "missing CommandAdapter description");
        this.options = requireNonNull(options, "missing CommandAdapter options");
        this.responseTtlSeconds = requirePositive(SpotBot.appProperties.getIntOr("command." + name + ".ttl.seconds", responseTtlSeconds));
        LOGGER.debug("new CommandAdapter {}, responseTtlSeconds : {}, options : {}", name, this.responseTtlSeconds, options.toData());
    }

    @Override
    @NotNull
    public final String name() {
        return name;
    }

    @Override
    @NotNull
    public final String description() {
        return description;
    }

    @Override
    @NotNull
    public final SlashCommandData options() {
        return SlashCommandData.fromData(options.toData()); // defensive copy
    }

    public static boolean isPrivateChannel(@NotNull CommandContext context) {
        return isPrivate(context.serverId());
    }

    public static EmbedBuilder embedBuilder(@NotNull String text) {
        return new EmbedBuilder().setDescription(requireNonNull(text));
    }

    public static EmbedBuilder embedBuilder(@Nullable String title, @Nullable Color color, @Nullable String text) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(text);
    }

    protected static OptionData option(@NotNull OptionType type, @NotNull String name, @NotNull String description, boolean isRequired) {
        return new OptionData(type, name, description, isRequired);
    }

    protected static Message securedAlertAccess(long alertId, @NotNull CommandContext context, @NotNull BiFunction<Alert, AlertsDao, Message> readHandler) {
        LOGGER.debug("securedAlertAccess, alertId : {}, user : {}, server : {}", alertId, context.userId, context.serverId());
        requireNonNull(readHandler);
        return context.transactional(txCtx -> {
            var alertsDao = txCtx.alertsDao();
            Alert alert = alertsDao.getAlert(context.clientType, alertId).orElse(null);
            if(SecurityAccess.notFound(context, alert)) {
                return Message.of(embedBuilder(":ghost:  " + context.userName, NOT_FOUND_COLOR, "Alert " + alertId + " not found"));
            }
            var message = readHandler.apply(alert, alertsDao);
            var embed = requireOneItem(message.embeds()).build();
            requireOneItem(message.embeds()).setTitle(null == embed.getTitle() ?":face_with_monocle: " + alert.type.titleName + " alert " + alertId : embed.getTitle());
            requireOneItem(message.embeds()).setColor(null == embed.getColor() ? OK_COLOR : embed.getColor());
            return message;
        });
    }

    @FunctionalInterface
    public interface UpdateHandler {
        Message update(@NotNull Alert alert, @NotNull AlertsDao alertsDao, @NotNull Supplier<NotificationsDao> notificationsDao);
    }

    public static Message securedAlertUpdate(long alertId, @NotNull CommandContext context, @NotNull UpdateHandler updateHandler) {
        LOGGER.debug("securedAlertUpdate, alertId : {}, user : {}, server : {}", alertId, context.userId, context.serverId());
        requireNonNull(updateHandler);
        NotificationsDao[] sendNotifications = new NotificationsDao[1];
        var message = context.transactional(txCtx -> {
            var alertsDao = txCtx.alertsDao();
            Alert alert = alertsDao.getAlertWithoutMessage(context.clientType, alertId).orElse(null);
            if(SecurityAccess.notFound(context, alert)) {
                return Message.of(embedBuilder(":ghost:  " + context.userName, NOT_FOUND_COLOR, "Alert " + alertId + " not found"));
            } else if(SecurityAccess.isDenied(context, alert)) {
                return Message.of(embedBuilder(":clown:  " + context.userName, DENIED_COLOR, "You are not allowed to modify alert " + alertId));
            }
            var notificationsDao = txCtx.notificationsDao();
            Supplier<NotificationsDao> notificationsDaoSupplier = () -> sendNotifications[0] = notificationsDao;
            var update = updateHandler.update(alert, alertsDao, notificationsDaoSupplier);
            var embedBuilder = requireOneItem(update.embeds());
            embedBuilder.setTitle(":+1:  " + context.userName)
                    .setColor(Optional.ofNullable(embedBuilder.build().getColor()).orElse(OK_COLOR));
            return update;
        });
        // perform user notification of its alerts being updated, if needed, once above update transaction is successful.
        Optional.ofNullable(sendNotifications[0]).ifPresent(v -> context.notificationService().sendNotifications());
        return message;
    }

    protected static boolean sendNotification(@NotNull CommandContext context, long userId, long count) {
        return count > 0 && !sameUser(context, userId);
    }

    protected static Optional<Alert> saveAlert(@NotNull CommandContext context, @NotNull Alert alert) {
        return context.transactional(txCtx -> {
            if (txCtx.userSettingsDao().userExists(context.clientType, alert.userId)) { // enforce foreign key constraint on user_id
                var newAlert = alert.withId(() -> txCtx.alertsDao().addAlert(alert));
                LOGGER.debug("saveAlert, alertId : {}, user : {}, server : {}", newAlert.id, context.userId, context.serverId());
                return Optional.of(newAlert);
            }
            LOGGER.debug("saveAlert skipped, missing user setup, user : {}, server : {}", context.userId, context.serverId());
            return empty();
        });
    }

    protected static Message createdAlertMessage(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert) {
        var message = editableAlertMessage(context, now, alert, 0, 0);
        if (remainder != alert.type) {
            requireOneItem(message.embeds()).appendDescription(alertMessageTips(alert.message));
        }
        return message;
    }

    protected static Message editableAlertMessage(@NotNull CommandContext context, ZonedDateTime now, @NotNull Alert alert, long offset, long total) {
        return Message.of(decoratedAlertEmbed(context, now, alert, offset, total),
                ActionRow.of(updateMenuOf(alert)));
    }

    protected static EmbedBuilder decoratedAlertEmbed(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert, long offset, long total) {
        return alertEmbed(context, now, alert)
                .setTitle('[' + alert.pair + ALERT_TITLE_PAIR_FOOTER + alert.message)
                .setFooter(total > 0 ? "(" + offset + "/" + total + ")" : null);
    }

    static EmbedBuilder alertEmbed(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert) {
        var serverName = switch (alert.clientType) {
            case DISCORD -> isPrivateChannel(context) && !isPrivate(alert.serverId) ?
                    context.discord().guildServer(alert.serverId).map(Discord::guildName).orElse("unknown") : null;
        };
        return alert.descriptionMessage(requireNonNull(now), serverName);
    }

    protected static Message userSetupNeeded(@NotNull String title, @NotNull String message) {
        return Message.of(embedBuilder(requireNonNull(title), NOT_FOUND_COLOR, requireNonNull(message) +
                        "\n\n> Missing user account setup !" +
                        "\n\nPlease use any slash command to setup your account once, like /spotbot, then try again."));
    }

    @NotNull
    protected static String alertMessageTips(@NotNull String message) {
        return "\n\n" + ALERT_TIPS +
                (message.contains("http://") || message.contains("https://") ? "" :
                ("\n\n**Please consider adding a link to your AT in your message!!\n\n**"));
    }
}
