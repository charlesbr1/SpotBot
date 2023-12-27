package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.alerts.Alert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.stream.Collectors;

public final class ListCommand extends CommandAdapter {

    public static final String NAME = "list";
    static final String HELP = "TODO !list (exchange|pair|alerts), example : !list binance, !list ETH, !list ETH/BTC, !list alerts";

    public ListCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("list command: {}", event.getMessage().getContentRaw());

        String arg = argumentReader.getMandatoryString("exchange or ticker or pair or 'alerts'");
        if("alerts".equals(arg)) {
            String messages = alertStorage.getAlerts().map(Alert::toString).collect(Collectors.joining("\n"));
            sendResponse(event, messages);
        }
        //TODO
    }
}
