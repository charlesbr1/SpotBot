package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.alerts.RangeAlert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.math.BigDecimal;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String HELP = "!range exchange ticker1 ticker2 low high message (Optional) - create a new range alert on pair ticker1/ticker2, example : !range binance ETH USDT 1900 2100 zone conso 1";

    public RangeCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("range command: {}", event.getMessage().getContentRaw());

        String exchange = argumentReader.getMandatoryString("exchange");
        String ticker1 = argumentReader.getMandatoryString("ticker1");
        String ticker2 = argumentReader.getMandatoryString("ticker2");
        BigDecimal low = argumentReader.getMandatoryNumber("low");
        BigDecimal high = argumentReader.getMandatoryNumber("high");
        String message = argumentReader.getRemaining().orElse("");

        RangeAlert rangeAlert = new RangeAlert(exchange, ticker1, ticker2, low, high, message, event.getAuthor().getName());

        alertStorage.addAlert(rangeAlert);
        sendResponse(event, "New range alert added by user " + rangeAlert.owner + " with id " + rangeAlert.id +
                " on pair " + rangeAlert.getReadablePair() + " on exchange " + exchange + ". box from " + low + " to " + high);
    }
}
