package org.sbot.commands;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public final class UpTimeCommand extends CommandAdapter {

    public static final String NAME = "uptime";
    static final String DESCRIPTION = "returns the time since this server is up";

    private static final Instant start = Instant.now();

    public UpTimeCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return Collections.emptyList();
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("uptime command: {}", event.getMessage().getContentRaw());
        uptime(event);
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
        LOGGER.debug("uptime slash command: {}", event.getOptions());
        uptime(event);
    }

    private void uptime(GenericEvent event) {
        Duration upTime = Duration.between(start, Instant.now());
        sendResponse(event,"SpotBot is up since " +
                upTime.toDays() + (upTime.toDays() > 1 ? " days, " : " day, ") +
                (upTime.toHours() % 24) + ((upTime.toHours() % 24)  > 1 ? " hours, " : " hour, ") +
                (upTime.toMinutes() % 60) + ((upTime.toMinutes() % 60) > 1 ? " minutes" : " minute") +
                (upTime.toSeconds() % 60) + ((upTime.toSeconds() % 60) > 1 ? " seconds" : " second"));
    }
}
