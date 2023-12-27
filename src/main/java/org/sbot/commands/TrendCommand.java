package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.alerts.TrendAlert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public final class TrendCommand extends CommandAdapter {

    public static final String NAME = "trend";
    static final String HELP = "!trend exchange ticker1 ticker2 price1 date1 price2 date2 message (Optional) - create a new trend alert on pair ticker1/ticker2, example : !trend binance ETH USDT 1900 date1 2100 date2 price reached trend line";

    public TrendCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("trend command: {}", event.getMessage().getContentRaw());

        String exchange = argumentReader.getMandatoryString("exchange");
        String ticker1 = argumentReader.getMandatoryString("ticker1");
        String ticker2 = argumentReader.getMandatoryString("ticker2");
        BigDecimal price1 = argumentReader.getMandatoryNumber("price1");
        ZonedDateTime date1 = argumentReader.getMandatoryDateTime("date1");
        BigDecimal price2 = argumentReader.getMandatoryNumber("price2");
        ZonedDateTime date2 = argumentReader.getMandatoryDateTime("date2");
        String message = argumentReader.getRemaining().orElse("");

        TrendAlert trendAlert = new TrendAlert(exchange, ticker1, ticker2, price1, date1, price2, date2, message, event.getAuthor().getName());

        alertStorage.addAlert(trendAlert);
        sendResponse(event, "New trend alert added by user " + trendAlert.owner + " with id " + trendAlert.id +
                " on pair " + trendAlert.getReadablePair() + " on exchange " + exchange + ". price1: " + price1 +
                ", date1: " + date1 + ", price2: " + price2 + ", date2: " + date2);
    }
}
