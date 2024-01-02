package org.sbot.commands;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentReader.getMandatoryString;

public final class ListCommand extends CommandAdapter {

    public static final String NAME = "list";
    static final String DESCRIPTION = "list the supported exchanges, or pairs, or the alerts currently set";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "value", "the data to list, one of 'exchanges', 'pair', or 'alerts'", true)
                    .addChoice("exchanges", "exchanges")
                    .addChoice("pair", "pair")
                    .addChoice("alerts", "alerts"));

    public ListCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("list command: {}", event.getMessage().getContentRaw());
        String value = argumentReader.getMandatoryString("value");
        event.getChannel().sendMessageEmbeds(list(value)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("list slash command: {}", event.getOptions());
        String value = getMandatoryString(event, "value");
        event.replyEmbeds(list(value)).queue();
    }

    private MessageEmbed list(@NotNull String value) {
        String answer = switch (value) {
            case "alerts" -> { // TODO list alert on channel or exchange
                String messages = alertStorage.getAlerts().map(Alert::toString).collect(Collectors.joining(""));
                yield messages.isEmpty() ? "No record found" : messages;
            }
            case "exchanges" -> String.join("\n", SUPPORTED_EXCHANGES);
            case "pair" -> "TODO";
            default -> "bad arg";
        };
        return embedBuilder(NAME, Color.green, answer).build();
    }
}
