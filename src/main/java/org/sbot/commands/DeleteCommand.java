package org.sbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.util.List;

import static java.util.Objects.requireNonNull;

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String DESCRIPTION = "delete an alert (only the alert owner or an admin is allowed to do it)";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert to delete", true));

    public DeleteCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return options;
    }

    @Override
    public void onEvent(@NotNull ArgumentReader argumentReader, @NotNull MessageReceivedEvent event) {
        LOGGER.debug("delete command: {}", event.getMessage().getContentRaw());
        long alertId = argumentReader.getMandatoryLong("alert_id");
        String author = event.getAuthor().getAsMention();
        onDelete(event.getChannel()::sendMessage, event.getChannel(), event.getMember(), author, alertId);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("delete slash command: {}", event.getOptions());
        long alertId = requireNonNull(event.getOption("alert_id", OptionMapping::getAsLong));
        String author = event.getUser().getAsMention();
        onDelete(sender(event), event.getChannel(), event.getMember(), author, alertId);
    }

    private void onDelete(@NotNull MessageSender sender, @NotNull MessageChannel channel, @Nullable Member member, @NotNull String author, long alertId) {
        alertStorage.getAlert(alertId).ifPresentOrElse(alert -> {
            if (checkDeleteAccess(member, author, alert)) {
                alertStorage.deleteAlert(alertId, asyncErrorHandler(channel, author, alertId));
                sendResponse(sender, author + " Alert " + alertId + " deleted");
            } else {
                sendResponse(sender, author + " Not allowed to delete alert " + alertId);
            }
        }, () -> sendResponse(sender, author + " Alert " + alertId + " not found"));
    }

    private boolean checkDeleteAccess(@Nullable Member member, @NotNull String author, @NotNull Alert alert) {
        return null != member && member.hasPermission(Permission.ADMINISTRATOR)
                || author.equals(alert.getOwner());
    }
}