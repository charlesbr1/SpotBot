package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.math.BigDecimal;

public final class OccurrenceCommand extends CommandAdapter {
//TODO add support in Alert

    public static final String NAME = "occurrence";
    static final String HELP = "!occurrence alert_id new_value - update the occurrence of the given alert, example : !occurrence 123 5";

    public OccurrenceCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("occurrence command: {}", event.getMessage().getContentRaw());

        long alertId = argumentReader.getMandatoryLong("alert id");
        BigDecimal value = argumentReader.getMandatoryNumber("new value");
// TODO
        alertStorage.getAlert(alertId).ifPresent(alert -> {
//            alert.setOccurrence(value);
            sendResponse(event, alert.toString());
        });
    }
}
