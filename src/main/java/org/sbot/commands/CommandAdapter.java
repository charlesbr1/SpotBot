package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.discord.DiscordCommand;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;

public abstract class CommandAdapter implements DiscordCommand {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    protected final AlertStorage alertStorage;
    private final String name;

    protected CommandAdapter(@NotNull AlertStorage alertStorage, @NotNull String name) {
        this.alertStorage = requireNonNull(alertStorage);
        this.name = requireNonNull(name);
    }

    @Override
    public String name() {
        return name;
    }

    protected record AnswerColor(@NotNull String answer, @NotNull Color color) {}

    protected AnswerColor updateAlert(long alertId, @NotNull User user, @Nullable Member member,
                                 Function<Alert, String> updateHandler) {
        return alertStorage.getAlert(alertId).map(alert -> {
            if (hasAccess(alert, user, member)) {
                return new AnswerColor(updateHandler.apply(alert), Color.green);
            } else {
                return new AnswerColor(user.getAsMention() + " Not allowed to update alert " + alertId, Color.red);
            }
        }).orElseGet(() -> new AnswerColor(user.getAsMention() + " Alert " + alertId + " not found", Color.black));
    }

    // the alert must belong to the user, or the user must be admin of the server and the alert belong to his server
    protected static boolean hasAccess(@NotNull Alert alert, @NotNull User user, @Nullable Member member) {
        return alertBelongToUser(alert, user) || userIsAdminAndAlertOnHisServer(alert, member);
    }

    protected static boolean alertBelongToUser(@NotNull Alert alert, @NotNull User user) {
        return alert.userId == user.getIdLong();
    }

    protected static boolean userIsAdminAndAlertOnHisServer(@NotNull Alert alert, @Nullable Member member) {
        return !alert.isPrivate() && null != member &&
                member.hasPermission(Permission.ADMINISTRATOR) &&
                alert.serverId == member.getGuild().getIdLong();
    }

    protected Predicate<Alert> serverOrPrivateFilter(@NotNull User user, @Nullable Member member) {
        long serverId = null != member ? member.getGuild().getIdLong() : PRIVATE_ALERT;
        return PRIVATE_ALERT != serverId ? alert -> alert.serverId == serverId :
                alert -> alert.userId == user.getIdLong();
    }
    protected static EmbedBuilder embedBuilder(@Nullable String title, @Nullable Color color, @Nullable String text) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(text);
    }
}
