package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.sbot.discord.Discord.MESSAGE_SECTION_DELIMITER;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;

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
        list(event.getChannel()::sendMessage, value);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("list slash command: {}", event.getOptions());
        String value = requireNonNull(event.getOption("value", OptionMapping::getAsString));
        list(sender(event), value);
    }

    private void list(@NotNull MessageSender sender, @NotNull String value) {
        switch (value) {
            case "alerts" -> {
                String messages = alertStorage.getAlerts().map(Alert::toString).collect(Collectors.joining(MESSAGE_SECTION_DELIMITER));
                sendResponse(sender, messages.isEmpty() ? "No record found" : messages);
            }
            case "exchanges" -> {
                String message = String.join("\n", SUPPORTED_EXCHANGES);
                sendResponse(sender, message);
            }
            case "pair" -> {
            }
            //TODO
        }
    }
}
