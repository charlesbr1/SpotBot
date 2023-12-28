package org.sbot.commands;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.discord.DiscordCommand;
import org.sbot.storage.AlertStorage;

public abstract class CommandAdapter implements DiscordCommand {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    protected final AlertStorage alertStorage;
    private final String name;

    protected CommandAdapter(AlertStorage alertStorage, String name) {
        this.alertStorage = alertStorage;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    protected void sendResponse(GenericEvent event, String message) {
        if(event instanceof MessageReceivedEvent) {
            ((MessageReceivedEvent)event).getChannel().sendMessage(message).queue();
        } else if(event instanceof SlashCommandInteractionEvent) {
            ((SlashCommandInteractionEvent)event).reply(message).queue();
        } else {
            throw new IllegalArgumentException("Unexpected class type: " + event.getClass().getName());
        }
    }
}
