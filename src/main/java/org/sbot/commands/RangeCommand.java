package org.sbot.commands;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.alerts.RangeAlert;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.math.BigDecimal;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on pair ticker1/ticker2";

    public RangeCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of(new OptionData(STRING, "exchange", "the exchange", true),
                new OptionData(STRING, "ticker1", "the first ticker", true),
                new OptionData(STRING, "ticker2", "the second ticker", true),
                new OptionData(NUMBER, "low", "the low price", true),
                new OptionData(NUMBER, "high", "the high price", true),
                new OptionData(STRING, "message", "a message to display on alert", false));
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

        String author = event.getAuthor().getAsMention();
        range(event, author, exchange, ticker1, ticker2, low, high, message);
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
        LOGGER.debug("range slash command: {}", event.getOptions());
        String exchange = event.getOption("exchange", OptionMapping::getAsString);
        String ticker1 = event.getOption("ticker1", OptionMapping::getAsString);
        String ticker2 = event.getOption("ticker2", OptionMapping::getAsString);
        BigDecimal low = new BigDecimal(event.getOption("low", OptionMapping::getAsString));
        BigDecimal high = new BigDecimal(event.getOption("high", OptionMapping::getAsString));
        String message = event.getOption("message", OptionMapping::getAsString);

        String author = event.getUser().getAsMention();
        range(event, author, exchange, ticker1, ticker2, low, high, message);
    }

    private void range(GenericEvent event, String author, String exchange, String ticker1, String ticker2, BigDecimal low, BigDecimal high, String message) {

        RangeAlert rangeAlert = new RangeAlert(exchange, ticker1, ticker2, low, high, message, author);
        alertStorage.addAlert(rangeAlert, error -> sendResponse(event, error));

        sendResponse(event, author + " New range alert added by user " + rangeAlert.owner + " with id " + rangeAlert.id +
                " on pair " + rangeAlert.getReadablePair() + " on exchange " + exchange + ". box from " + low + " to " + high);

    }
}
