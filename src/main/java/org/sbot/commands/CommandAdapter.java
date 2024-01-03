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
import org.sbot.commands.reader.CommandContext;
import org.sbot.discord.CommandListener;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;

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

    protected AnswerColor updateAlert(long alertId, @NotNull CommandContext context,
                                      @NotNull Function<Alert, String> updateHandler) {
        return alertStorage.getAlert(alertId).map(alert -> {
            if (hasAccess(alert, context)) {
                return new AnswerColor(updateHandler.apply(alert), Color.green);
            } else {
                return new AnswerColor(context.user.getAsMention() + " Not allowed to update alert " + alertId, Color.red);
            }
        }).orElseGet(() -> new AnswerColor(context.user.getAsMention() + " Alert " + alertId + " not found", Color.black));
    }

    // the alert must belong to the user, or the user must be admin of the server and the alert belong to his server
    protected static boolean hasAccess(@NotNull Alert alert, @NotNull CommandContext context) {
        return alertBelongToUser(alert, context.user) || userIsAdminAndAlertOnHisServer(alert, context.member);
    }

    protected static boolean alertBelongToUser(@NotNull Alert alert, @NotNull User user) {
        return alert.userId == user.getIdLong();
    }

    protected static boolean userIsAdminAndAlertOnHisServer(@NotNull Alert alert, @Nullable Member member) {
        return !alert.isPrivate() && null != member &&
                member.hasPermission(ADMINISTRATOR) &&
                alert.serverId == member.getGuild().getIdLong();
    }

    protected Predicate<Alert> serverOrPrivateFilter(@NotNull CommandContext context) {
        long serverId = context.getServerId();
        return PRIVATE_ALERT != serverId ? alert -> alert.serverId == serverId :
                alert -> alert.userId == context.user.getIdLong();
    }
    public static EmbedBuilder embedBuilder(@Nullable String title, @Nullable Color color, @Nullable String text) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(text);
    }

    protected static EmbedBuilder toMessage(@NotNull Alert alert, @NotNull String ownerName) {
        return embedBuilder('[' + alert.getSlashPair() + "] " + alert.message,
                alert.isPrivate() ? Color.blue : (alert.isOver() ? Color.black : Color.green),
                alert.descriptionMessage(ownerName));
    }

    protected static List<EmbedBuilder> adaptSize(@NotNull List<EmbedBuilder> messages, long offset, long total, @NotNull Supplier<String> nextCommand, @NotNull Supplier<String> command) {
        if(messages.isEmpty()) {
            messages.add(embedBuilder("Alerts search", Color.yellow,
                    "No alert found for " + command.get()));
        } else {
            toPageSize(messages, offset, total, nextCommand);
        }
        return messages;
    }

    private static void toPageSize(@NotNull List<EmbedBuilder> messages, long offset, long total, @NotNull Supplier<String> nextCommand) {
        if(messages.size() > MESSAGE_PAGE_SIZE) {
            while(messages.size() >= MESSAGE_PAGE_SIZE) {
                messages.remove(messages.size() - 1);
            }
            for(int i = messages.size(); i-- != 0;) {
                messages.get(i).setFooter("(" + (i + 1 + offset) + '/' + total + ')');
            }
            messages.add(embedBuilder("...", Color.green, "More results found, to get them type command with offset " +
                    (offset + MESSAGE_PAGE_SIZE - 1) + " : " + nextCommand.get()));
        }
    }
}
