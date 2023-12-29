package org.sbot.commands;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.TrendAlert;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendCommand extends CommandAdapter {

    public static final String NAME = "trend";
    static final String DESCRIPTION = "create a new trend alert on pair ticker1/ticker2, a trend is defined by two prices and two dates";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true),
            new OptionData(STRING, "ticker2", "the second ticker", true),
            new OptionData(NUMBER, "price1", "the first price", true),
            new OptionData(STRING, "date1", "the date of first price, UTC expected, format: " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(NUMBER, "price2", "the second price", true),
            new OptionData(STRING, "date2", "the date of second price, UTC expected, format: " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(STRING, "message", "a message to display when the alert is triggered", false));


    public TrendCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return options;
    }


    @Override
    public void onEvent(@NotNull ArgumentReader argumentReader, @NotNull MessageReceivedEvent event) {
        LOGGER.debug("trend command: {}", event.getMessage().getContentRaw());

        String exchange = argumentReader.getMandatoryString("exchange");
        String ticker1 = argumentReader.getMandatoryString("ticker1");
        String ticker2 = argumentReader.getMandatoryString("ticker2");
        BigDecimal price1 = argumentReader.getMandatoryNumber("price1");
        ZonedDateTime date1 = argumentReader.getMandatoryDateTime("date1");
        BigDecimal price2 = argumentReader.getMandatoryNumber("price2");
        ZonedDateTime date2 = argumentReader.getMandatoryDateTime("date2");
        String message = argumentReader.getRemaining();

        String author = event.getAuthor().getAsMention();
        trend(event.getChannel()::sendMessage, event.getChannel(), author, exchange, ticker1, ticker2, price1, date1, price2, date2, message);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("trend slash command: {}", event.getOptions());

        String exchange = requireNonNull(event.getOption("exchange", OptionMapping::getAsString));
        String ticker1 = requireNonNull(event.getOption("ticker1", OptionMapping::getAsString));
        String ticker2 = requireNonNull(event.getOption("ticker2", OptionMapping::getAsString));
        BigDecimal price1 = new BigDecimal(requireNonNull(event.getOption("price1", OptionMapping::getAsString)));
        ZonedDateTime date1 = Dates.parseUTC(requireNonNull(event.getOption("date1", OptionMapping::getAsString)));
        BigDecimal price2 = new BigDecimal(requireNonNull(event.getOption("price2", OptionMapping::getAsString)));
        ZonedDateTime date2 = Dates.parseUTC(requireNonNull(event.getOption("date2", OptionMapping::getAsString)));
        String message = event.getOption("message", "", OptionMapping::getAsString);

        String author = event.getUser().getAsMention();
        trend(sender(event), event.getChannel(), author, exchange, ticker1, ticker2, price1, date1, price2, date2, message);
    }

    private void trend(@NotNull MessageSender sender, @NotNull MessageChannel channel,
                       @NotNull String author, @NotNull String exchange,
                       @NotNull String ticker1, @NotNull String ticker2,
                       @NotNull BigDecimal price1, @NotNull ZonedDateTime date1,
                       @NotNull BigDecimal price2, @NotNull ZonedDateTime date2, @NotNull String message) {

        if(date1.isAfter(date2)) { // ensure correct order of prices
            ZonedDateTime swap = date1;
            date1 = date2;
            date2 = swap;
            BigDecimal value = price1;
            price1 = price2;
            price2 = value;
        }
        TrendAlert trendAlert = new TrendAlert(exchange, ticker1, ticker2, price1, date1, price2, date2, message, author);

        alertStorage.addAlert(trendAlert, asyncErrorHandler(channel, author, trendAlert.id));
        sendResponse(sender, "New trend alert added by user " + trendAlert.owner + " with id " + trendAlert.id +
                " on pair " + trendAlert.getReadablePair() + " on exchange " + exchange + ". price1: " + price1 +
                ", date1: " + formatUTC(date1) + ", price2: " + price2 + ", date2: " + formatUTC(date2));
    }
}
