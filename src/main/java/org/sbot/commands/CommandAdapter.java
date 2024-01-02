package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.Command;
import org.sbot.discord.CommandListener;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;

public abstract class CommandAdapter implements CommandListener {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    protected final AlertStorage alertStorage;
    private final String name;
    private final String description;
    private final List<OptionData> options;

    protected CommandAdapter(@NotNull AlertStorage alertStorage, @NotNull String name, @NotNull String description, @NotNull List<OptionData> options) {
        this.alertStorage = requireNonNull(alertStorage);
        this.name = requireNonNull(name);
        this.description = requireNonNull(description);
        this.options = requireNonNull(options);
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
    public final List<OptionData> options() {
        return options;
    }

    protected record AnswerColor(@NotNull String answer, @NotNull Color color) {}

    protected AnswerColor updateAlert(long alertId, @NotNull Command command,
                                      @NotNull Function<Alert, String> updateHandler) {
        return alertStorage.getAlert(alertId).map(alert -> {
            if (hasAccess(alert, command)) {
                return new AnswerColor(updateHandler.apply(alert), Color.green);
            } else {
                return new AnswerColor(command.user.getAsMention() + " Not allowed to update alert " + alertId, Color.red);
            }
        }).orElseGet(() -> new AnswerColor(command.user.getAsMention() + " Alert " + alertId + " not found", Color.black));
    }

    // the alert must belong to the user, or the user must be admin of the server and the alert belong to his server
    protected static boolean hasAccess(@NotNull Alert alert, @NotNull Command command) {
        return alertBelongToUser(alert, command.user) || userIsAdminAndAlertOnHisServer(alert, command.member);
    }

    protected static boolean alertBelongToUser(@NotNull Alert alert, @NotNull User user) {
        return alert.userId == user.getIdLong();
    }

    protected static boolean userIsAdminAndAlertOnHisServer(@NotNull Alert alert, @Nullable Member member) {
        return !alert.isPrivate() && null != member &&
                member.hasPermission(ADMINISTRATOR) &&
                alert.serverId == member.getGuild().getIdLong();
    }

    protected Predicate<Alert> serverOrPrivateFilter(@NotNull Command command) {
        long serverId = command.getServerId();
        return PRIVATE_ALERT != serverId ? alert -> alert.serverId == serverId :
                alert -> alert.userId == command.user.getIdLong();
    }
    public static EmbedBuilder embedBuilder(@Nullable String title, @Nullable Color color, @Nullable String text) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(text);
    }
}
