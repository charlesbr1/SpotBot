package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.alerts.Alert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String HELP = "!owner owner_name (ticker | pair) (Optional) - show the alerts defined by the given user on a ticker or a pair, example : !owner zulu, !owner zulu ETH, !owner zulu ETH/BTC";

    public OwnerCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("owner command: {}", event.getMessage().getContentRaw());

        String owner = argumentReader.getMandatoryString("owner");
        String pair = argumentReader.getNextString().map(String::toUpperCase).orElse(null);

        Predicate<Alert> check = null != pair ?
                alert -> alert.owner.equals(owner) && alert.getReadablePair().contains(pair) :
                alert -> alert.owner.equals(owner);

        String alerts = alertStorage.getAlerts()
                .filter(check).map(Alert::toString)
                .collect(Collectors.joining("\n"));
        sendResponse(event, alerts.isEmpty() ?
                "No alert found for " + owner + (null != pair ? " and " + pair : "") : alerts);
    }
}
