package org.sbot.commands;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.alerts.TrendAlert;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public final class TrendCommand extends CommandAdapter {

    public static final String NAME = "trend";
    static final String DESCRIPTION = "create a new trend alert on pair ticker1/ticker2";

    public TrendCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of(new OptionData(OptionType.STRING, "exchange", "description", true),
                new OptionData(OptionType.STRING, "ticker1", "description", true),
                new OptionData(OptionType.STRING, "ticker2", "description", true),
                new OptionData(OptionType.NUMBER, "price1", "description", true),
                new OptionData(OptionType.STRING, "date1", "description", true),
                new OptionData(OptionType.NUMBER, "price2", "description", true),
                new OptionData(OptionType.STRING, "date2", "description", true),
                new OptionData(OptionType.STRING, "message", "description", false));
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

        String author = event.getAuthor().getAsMention();
        trend(event, author, exchange, ticker1, ticker2, price1, date1, price2, date2, message);
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
        LOGGER.debug("trend slash command: {}", event.getOptions());
        String exchange = event.getOption("exchange", OptionMapping::getAsString);
        String ticker1 = event.getOption("ticker1", OptionMapping::getAsString);
        String ticker2 = event.getOption("ticker2", OptionMapping::getAsString);
        BigDecimal price1 = new BigDecimal(event.getOption("price1", OptionMapping::getAsString));
        ZonedDateTime date1 = ZonedDateTime.parse(event.getOption("date1", OptionMapping::getAsString));
        BigDecimal price2 = new BigDecimal(event.getOption("price2", OptionMapping::getAsString));
        ZonedDateTime date2 = ZonedDateTime.parse(event.getOption("date2", OptionMapping::getAsString));
        String message = event.getOption("message", OptionMapping::getAsString);

        String author = event.getUser().getAsMention();
        trend(event, author, exchange, ticker1, ticker2, price1, date1, price2, date2, message);
    }

    private void trend(GenericEvent event, String author, String ticker1, String ticker2, String exchange, BigDecimal price1, ZonedDateTime date1, BigDecimal price2, ZonedDateTime date2, String message) {

        TrendAlert trendAlert = new TrendAlert(exchange, ticker1, ticker2, price1, date1, price2, date2, message, author);

        alertStorage.addAlert(trendAlert, error -> sendResponse(event, error));
        sendResponse(event, "New trend alert added by user " + trendAlert.owner + " with id " + trendAlert.id +
                " on pair " + trendAlert.getReadablePair() + " on exchange " + exchange + ". price1: " + price1 +
                ", date1: " + date1 + ", price2: " + price2 + ", date2: " + date2);

    }}
