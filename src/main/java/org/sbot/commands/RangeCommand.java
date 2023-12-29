package org.sbot.commands;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.RangeAlert;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.math.BigDecimal;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;

public final class RangeCommand extends CommandAdapter {

    public static final String NAME = "range";
    static final String DESCRIPTION = "create a new range alert on pair ticker1/ticker2, a range is defined by a low price and a high price";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true),
            new OptionData(STRING, "ticker2", "the second ticker", true),
            new OptionData(NUMBER, "low", "the low range price", true),
            new OptionData(NUMBER, "high", "the high range price", true),
            new OptionData(STRING, "message", "a message to display when the alert is triggered", false));

    public RangeCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("range command: {}", event.getMessage().getContentRaw());

        String exchange = argumentReader.getMandatoryString("exchange");
        String ticker1 = argumentReader.getMandatoryString("ticker1");
        String ticker2 = argumentReader.getMandatoryString("ticker2");
        BigDecimal low = argumentReader.getMandatoryNumber("low");
        BigDecimal high = argumentReader.getMandatoryNumber("high");
        String message = argumentReader.getRemaining();

        String author = event.getAuthor().getAsMention();
        range(event.getChannel()::sendMessage, event.getChannel(), author, exchange, ticker1, ticker2, low, high, message);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("range slash command: {}", event.getOptions());

        String exchange = requireNonNull(event.getOption("exchange", OptionMapping::getAsString));
        String ticker1 = requireNonNull(event.getOption("ticker1", OptionMapping::getAsString));
        String ticker2 = requireNonNull(event.getOption("ticker2", OptionMapping::getAsString));
        BigDecimal low = new BigDecimal(requireNonNull(event.getOption("low", OptionMapping::getAsString)));
        BigDecimal high = new BigDecimal(requireNonNull(event.getOption("high", OptionMapping::getAsString)));
        String message = event.getOption("message", "", OptionMapping::getAsString);

        String author = event.getUser().getAsMention();
        range(sender(event), event.getChannel(), author, exchange, ticker1, ticker2, low, high, message);
    }

    private void range(@NotNull MessageSender sender, @NotNull MessageChannel channel,
                       @NotNull String author, @NotNull String exchange,
                       @NotNull String ticker1, @NotNull String ticker2,
                       @NotNull BigDecimal low, @NotNull BigDecimal high, @NotNull String message) {

        if(low.compareTo(high) > 0) { // ensure correct order of prices
            BigDecimal swap = low;
            low = high;
            high = swap;
        }
        RangeAlert rangeAlert = new RangeAlert(exchange, ticker1, ticker2, low, high, message, author);
        alertStorage.addAlert(rangeAlert, asyncErrorHandler(channel, author, rangeAlert.id));

        sendResponse(sender, author + " New range alert added by user " + rangeAlert.owner + " with id " + rangeAlert.id +
                " on pair " + rangeAlert.getReadablePair() + " on exchange " + exchange + ". box from " + low + " to " + high);
    }
}