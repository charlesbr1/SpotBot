package org.sbot.discord;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;

public interface CommandListener {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    String operator();

    void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event);
}
