package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.SpotBot;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.discord.CommandListener;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
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

    private final String name;
    private final String description;
    private final SlashCommandData options;
    protected final int responseTtlSeconds;

    protected CommandAdapter(@NotNull String name,@NotNull String description, @NotNull SlashCommandData options, int responseTtlSeconds) {
        this.name = requireNonNull(name, "missing CommandAdapter name");
        this.description = requireNonNull(description, "missing CommandAdapter description");
        this.options = requireNonNull(options, "missing CommandAdapter options");
        this.responseTtlSeconds = requirePositive(SpotBot.appProperties.getIntOr("command." + name + ".ttl-seconds", responseTtlSeconds));
        LOGGER.debug("Created new CommandAdapter {}, responseTtlSeconds : {}, options : {}", name, this.responseTtlSeconds, options.toData());
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

    protected static OptionData option(@NotNull OptionType type, @NotNull String name, @NotNull String description, boolean isRequired) {
        return new OptionData(type, name, description, isRequired);
    }

    protected EmbedBuilder securedAlertAccess(long alertId, @NotNull CommandContext context, @NotNull BiFunction<Alert, AlertsDao, EmbedBuilder> updateHandler) {
        return context.transactional(txCtx -> {
            var dao = txCtx.alertsDao();
            Alert alert = dao.getAlertWithoutMessage(alertId).orElse(null);
            if(SecurityAccess.notFound(context, alert)) {
                return embedBuilder(":ghost: " + context.user.getEffectiveName(), Color.red, "Alert " + alertId + " not found");
            } else if(SecurityAccess.isDenied(context, alert)) {
                return embedBuilder(":clown: " + context.user.getEffectiveName(), Color.black, "You are not allowed to update alert " + alertId +
                        (isPrivateChannel(context) ? ", you are on a private channel." : ""));
            }
            var embedBuilder = updateHandler.apply(alert, dao).setTitle(":+1: " + context.user.getEffectiveName());
            return embedBuilder.setColor(Optional.ofNullable(embedBuilder.build().getColor()).orElse(Color.green));
        });
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

    protected static Message saveAlert(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull Alert alert) {
        Long alertId = context.transactional(txCtx -> {
            if(txCtx.usersDao().userExists(alert.userId)) { // enforce foreign key constraint on user_id
                return txCtx.alertsDao().addAlert(alert);
            }
            return null;
        });
        if(null == alertId) {
            return userSetupNeeded(context.name, "Unable to create a new alert :");
        }
        var message = ListCommand.toMessageWithEdit(context, now, alert.withId(() -> alertId), 0, 0);
        if (remainder != alert.type) {
            requireOneItem(message.embeds()).appendDescription(alertMessageTips(alert.message, alertId));
        }
        return message;
    }

    protected static Message userSetupNeeded(@NotNull String title, @NotNull String message) {
        return Message.of(embedBuilder(requireNonNull(title), Color.red, requireNonNull(message) +
                        "\n\n> Missing user account setup !" +
                        "\n\nPlease use any slash command to setup your account once, like /spotbot, then try again."));
    }

    public static final String ALERT_TIPS = "Your message will be shown in the title of your alert notification.";

    @NotNull
    protected static String alertMessageTips(@NotNull String message, long alertId) {
        return "\n\n" + ALERT_TIPS +
                (message.contains("http://") || message.contains("https://") ? "" :
                ("\n\n**Please consider adding a link in your message to your AT !!**\nYou can update it using :\n" +
                MarkdownUtil.quote("*update message " + alertId + " 'a message with a cool link to its AT'*")));
    }

    protected void sendUpdateNotification(@NotNull CommandContext context, long userId, @NotNull Message message) {
        if(!isPrivateChannel(context)) {
            context.discord().userChannel(userId).ifPresent(channel -> channel.sendMessages(List.of(message)));
        }
    }
}
