package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.TrendAlert;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class TrendCommand extends CommandAdapter {

    public static final String NAME = "trend";
    static final String DESCRIPTION = "create a new trend alert on pair ticker1/ticker2, a trend is defined by two prices and two dates";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "exchange", "the exchange", true)
                    .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Choice(e, e)).collect(toList())),
            new OptionData(STRING, "ticker1", "the first ticker", true),
            new OptionData(STRING, "ticker2", "the second ticker", true),
            new OptionData(NUMBER, "from_price", "the first price", true),
            new OptionData(STRING, "from_date", "the date of first price, UTC expected, format: " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(NUMBER, "to_price", "the second price", true),
            new OptionData(STRING, "to_date", "the date of second price, UTC expected, format: " + Dates.DATE_TIME_FORMAT, true),
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
        BigDecimal fromPrice = requirePositive(argumentReader.getMandatoryNumber("from price"));
        ZonedDateTime fromDate = argumentReader.getMandatoryDateTime("from date");
        BigDecimal toPrice = requirePositive(argumentReader.getMandatoryNumber("to price"));
        ZonedDateTime toDate = argumentReader.getMandatoryDateTime("to date");
        String message = argumentReader.getRemaining();

        event.getChannel().sendMessageEmbeds(trend(event.getAuthor(), event.getMember(), exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("trend slash command: {}", event.getOptions());

        String exchange = requireNonNull(event.getOption("exchange", OptionMapping::getAsString));
        String ticker1 = requireNonNull(event.getOption("ticker1", OptionMapping::getAsString));
        String ticker2 = requireNonNull(event.getOption("ticker2", OptionMapping::getAsString));
        BigDecimal fromPrice = requirePositive(new BigDecimal(requireNonNull(event.getOption("from_price", OptionMapping::getAsString))));
        ZonedDateTime fromDate = Dates.parseUTC(requireNonNull(event.getOption("from_date", OptionMapping::getAsString)));
        BigDecimal toPrice = requirePositive(new BigDecimal(requireNonNull(event.getOption("to_price", OptionMapping::getAsString))));
        ZonedDateTime toDate = Dates.parseUTC(requireNonNull(event.getOption("to_date", OptionMapping::getAsString)));
        String message = event.getOption("message", "", OptionMapping::getAsString);

        event.replyEmbeds(trend(event.getUser(), event.getMember(), exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message)).queue();
    }

    private MessageEmbed trend(@NotNull User user, @Nullable Member member, @NotNull String exchange,
                               @NotNull String ticker1, @NotNull String ticker2,
                               @NotNull BigDecimal fromPrice, @NotNull ZonedDateTime fromDate,
                               @NotNull BigDecimal toPrice, @NotNull ZonedDateTime toDate, @NotNull String message) {

        if(fromDate.isAfter(toDate)) { // ensure correct order of prices
            ZonedDateTime swap = fromDate;
            fromDate = toDate;
            toDate = swap;
            BigDecimal value = fromPrice;
            fromPrice = toPrice;
            toPrice = value;
        }
        TrendAlert trendAlert = new TrendAlert(user.getIdLong(),
                null != member ? member.getGuild().getIdLong() : PRIVATE_ALERT,
                exchange, ticker1, ticker2, fromPrice, fromDate, toPrice, toDate, message);

        alertStorage.addAlert(trendAlert);

        String answer = "New trend alert added with id " + trendAlert.id +
                " on pair " + trendAlert.getSlashPair() + " on exchange " + exchange + ". From price " + fromPrice +
                " at " + formatUTC(fromDate) + " to price: " + toPrice + " at " + formatUTC(toDate);

        return embedBuilder(NAME, Color.green, answer).build();
    }
}
