package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.math.BigDecimal;

public final class DelayCommand extends CommandAdapter {
//TODO add support in Alert

    public static final String NAME = "delay";
    static final String HELP = "!delay alert_id new_value - update the delay between two occurrences of the given alert, example : !delay 123 5";

    public DelayCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("delay command: {}", event.getMessage().getContentRaw());

        long alertId = argumentReader.getMandatoryLong("alert id");
        BigDecimal value = argumentReader.getMandatoryNumber("new value");
// TODO
        alertStorage.getAlert(alertId).ifPresent(alert -> {
//            alert.setDelay(value);
            sendResponse(event, alert.toString());
        });
    }
}
