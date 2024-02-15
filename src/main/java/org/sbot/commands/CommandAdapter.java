package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.SpotBot;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.commands.context.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.discord.CommandListener;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.services.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public abstract class CommandAdapter implements CommandListener {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

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

    public static final String ALERT_TIPS = "Your message will be shown in the title of your alert notification.";

    @NotNull
    protected static String alertMessageTips(@NotNull String message, long alertId) {
        return "\n\n" + ALERT_TIPS +
                (message.contains("http://") || message.contains("https://") ? "" :
                ("\n\n**Please consider adding a link in your message to your AT !!**\nYou can update it using :\n" +
                SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "*message " + alertId + " 'a message with a cool link to its AT'*"));
    }

    protected void sendUpdateNotification(@NotNull CommandContext context, long userId, @NotNull Message message) {
        if(!isPrivateChannel(context)) {
            context.discord().userChannel(userId).ifPresent(channel -> channel.sendMessages(List.of(message)));
        }
    }
}
