package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.discord.CommandListener;
import org.sbot.discord.Discord;
import org.sbot.storage.AlertStorage;

public abstract class CommandAdapter implements CommandListener {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    protected final AlertStorage alertStorage;
    private final String operator;

    protected CommandAdapter(AlertStorage alertStorage, String operator) {
        this.alertStorage = alertStorage;
        this.operator = operator;
    }

    @Override
    public String operator() {
        return operator;
    }

    protected void sendResponse(MessageReceivedEvent event, String message) {
        Discord.sendMessage(event.getChannel(), message);
    }
}
