package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String HELP = "!delete alert_id - delete an alert (only for alert owner or admin), example : !delete 123";

    public DeleteCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("delete command: {}", event.getMessage().getContentRaw());

        // TODO check security : owner and admin only
        long alertId = argumentReader.getMandatoryLong("alert Id");
        if(alertStorage.deleteAlert(alertId)) {
            sendResponse(event, "Alert " + alertId + " deleted");
        } else {
            sendResponse(event, "Alert " + alertId + " not found");
        }
    }
}
