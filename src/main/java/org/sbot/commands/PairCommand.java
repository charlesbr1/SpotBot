package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.alerts.Alert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.stream.Collectors;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String HELP = "!pair (ticker | pair) - show the alerts defined on the given ticker or pair, example : !pair ETH, !pair ETH/BTC";

    public PairCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("pair command: {}", event.getMessage().getContentRaw());

        String ticker = argumentReader.getMandatoryString("ticker or pair").toUpperCase();

        String alerts = alertStorage.getAlerts()
                .filter(alert -> alert.getReadablePair().contains(ticker))
                .map(Alert::toString)
                .collect(Collectors.joining("\n"));
        sendResponse(event, alerts.isEmpty() ? "No alert found for " + ticker : alerts);
    }
}
