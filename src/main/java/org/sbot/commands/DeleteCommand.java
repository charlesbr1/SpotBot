package org.sbot.commands;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.List;

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String DESCRIPTION = "delete an alert (only the alert owner or an admin is allowed to do it)";

    public DeleteCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of(new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true));
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("delete command: {}", event.getMessage().getContentRaw());

        // TODO check security : owner and admin only
        long alertId = argumentReader.getMandatoryLong("alert_id");
        String author = event.getAuthor().getAsMention();
        onDelete(event, author, alertId);
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
        LOGGER.debug("delete slash command: {}", event.getOptions());
        Long alertId = event.getOption("alert_id", OptionMapping::getAsLong);
        String author = event.getUser().getAsMention();
        onDelete(event, author, alertId);
    }

    private void onDelete(GenericEvent event, String author, long alertId) {
        if(alertStorage.deleteAlert(alertId, error -> sendResponse(event, error))) {
            sendResponse(event, author + " Alert " + alertId + " deleted");
        } else {
            sendResponse(event, author + " Alert " + alertId + " not found");
        }
    }
}
