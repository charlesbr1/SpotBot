package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.math.BigDecimal;

public final class ThresholdCommand extends CommandAdapter {
    //TODO add support in Alert
    public static final String NAME = "threshold";
    static final String HELP = "!threshold alert_id new_value - update the threshold of the given alert, example : !threshold 123 3";

    public ThresholdCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("threshold command: {}", event.getMessage().getContentRaw());

        long alertId = argumentReader.getMandatoryLong("alert id");
        BigDecimal value = argumentReader.getMandatoryNumber("new value");
// TODO
        alertStorage.getAlert(alertId).ifPresent(alert -> {
//            alert.setRepeat(value);
            sendResponse(event, alert.toString());
        });
    }
}
