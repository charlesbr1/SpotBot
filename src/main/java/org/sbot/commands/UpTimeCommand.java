package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.time.Duration;
import java.time.Instant;

public final class UpTimeCommand extends CommandAdapter {

    public static final String NAME = "uptime";
    static final String HELP = "!uptime - returns the time since this server is up";

    private static final Instant start = Instant.now();

    public UpTimeCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("uptime command: {}", event.getMessage().getContentRaw());

        Duration upTime = Duration.between(start, Instant.now());
        sendResponse(event,"SpotBot is up since " +
                upTime.toDays() + (upTime.toDays() > 1 ? " days, " : " day, ") +
                (upTime.toHours() % 24) + ((upTime.toHours() % 24)  > 1 ? " hours, " : " hour, ") +
                (upTime.toMinutes() % 60) + ((upTime.toMinutes() % 60) > 1 ? " minutes" : " minute") +
                (upTime.toSeconds() % 60) + ((upTime.toSeconds() % 60) > 1 ? " seconds" : " second"));
    }
}
