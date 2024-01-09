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
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.AlertsDao.UserIdServerId;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.sbot.alerts.Alert.*;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;

public abstract class CommandAdapter implements CommandListener {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    protected final AlertsDao alertsDao;
    private final String name;
    private final String description;
    private final List<OptionData> options;

    protected CommandAdapter(@NotNull AlertsDao alertsDao, @NotNull String name, @NotNull String description, @NotNull List<OptionData> options) {
        this.alertsDao = requireNonNull(alertsDao);
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

    protected record AnswerColorSmiley(@NotNull String answer, @NotNull Color color, @NotNull String smiley) {}

    protected AnswerColorSmiley securedAlertUpdate(long alertId, @NotNull CommandContext context, @NotNull Supplier<String> updateHandler) {
        return alertsDao.getUserIdAndServerId(alertId).map(userIdServerId -> {
            if (hasAccess(userIdServerId, context)) {
                return new AnswerColorSmiley(updateHandler.get(), Color.green, ":+1:");
            } else {
                return new AnswerColorSmiley(context.user.getAsMention() + "\nYou are not allowed to update alert " + alertId +
                        (isPrivate(context.getServerId()) ? ", you are on a private channel." : ""), Color.black, ":clown:");
            }
        }).orElseGet(() -> new AnswerColorSmiley(context.user.getAsMention() + "\nAlert " + alertId + " not found", Color.red, ":ghost:"));
    }

    // the alert must belong to the user, or the user must be admin of the server and the alert belong to his server
    protected static boolean hasAccess(@NotNull UserIdServerId userIdServerId, @NotNull CommandContext context) {
        return alertBelongToUser(context.user, userIdServerId.userId()) || userIsAdminAndAlertOnHisServer(context.member, userIdServerId.serverId());
    }

    private static boolean alertBelongToUser(@NotNull User user, long userId) {
        return  user.getIdLong() == userId;
    }

    private static boolean userIsAdminAndAlertOnHisServer(@Nullable Member member, long serverId) {
        return !isPrivate(serverId) && null != member &&
                member.hasPermission(ADMINISTRATOR) &&
                member.getGuild().getIdLong() == serverId;
    }

    protected static boolean isPrivateChannel(@NotNull CommandContext context) {
        return PRIVATE_ALERT == context.getServerId();
    }
    public static EmbedBuilder embedBuilder(@Nullable String title, @Nullable Color color, @Nullable String text) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(text);
    }

    protected static EmbedBuilder toMessage(@NotNull Alert alert) {
        return embedBuilder('[' + alert.getSlashPair() + "] " + alert.message,
                isDisabled(alert.repeat) ? Color.black : (isPrivate(alert.serverId) ? Color.blue : Color.green),
                alert.descriptionMessage());
    }

    //TODO doc mutation of list messages argument
    protected static List<EmbedBuilder> paginatedAlerts(@NotNull List<EmbedBuilder> messages, long offset, long total, @NotNull Supplier<String> nextCommand, @NotNull Supplier<String> command) {
        if(messages.isEmpty()) {
            messages.add(embedBuilder("Alerts search", Color.yellow,
                    "No alert found for " + command.get()));
        } else {
            addFooterNumber(messages, offset, total);
            shrinkToPageSize(messages, offset, nextCommand);
        }
        return messages;
    }

    private static void addFooterNumber(@NotNull List<EmbedBuilder> messages, long offset, long total) {
        for(int i = messages.size(); i-- != 0;) {
            messages.get(i).setFooter("(" + (i + 1 + offset) + '/' + total + ')');
        }
    }

    @NotNull
    protected static String alertMessageTips(@NotNull String message, long alertId) {
        return "\n\nYour message will be shown in the title of your alert notification." +
                (message.contains("http://") || message.contains("https://") ? "" :
                ("\n\n**Please consider adding a link in your message to your AT !!**\nYou can update it using :\n" +
                SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "*!message " + alertId + " 'a message with a cool link to its AT'*"));
    }

    private static void shrinkToPageSize(@NotNull List<EmbedBuilder> messages, long offset, @NotNull Supplier<String> nextCommand) {
        if(messages.size() > MESSAGE_PAGE_SIZE) {
            while(messages.size() >= MESSAGE_PAGE_SIZE) {
                messages.remove(messages.size() - 1);
            }
            messages.add(embedBuilder("...", Color.green, "More results found, to get them type command with offset " +
                    (offset + MESSAGE_PAGE_SIZE - 1) + " : " + nextCommand.get()));
        }
    }
}
