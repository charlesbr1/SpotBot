package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.math.BigDecimal;
import java.util.List;

public final class OccurrenceCommand extends CommandAdapter {
//TODO add support in Alert

    public static final String NAME = "occurrence";
    static final String DESCRIPTION = "update the number of time the alert will be thrown";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "occurrence", "number of time the specified alert will be thrown", true));

    public OccurrenceCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("occurrence command: {}", event.getMessage().getContentRaw());

        long alertId = argumentReader.getMandatoryLong("alert id");
        BigDecimal value = argumentReader.getMandatoryNumber("new value");
// TODO
        alertStorage.getAlert(alertId).ifPresent(alert -> {
//            alert.setOccurrence(value);
            sendResponse(event.getChannel()::sendMessage, alert.toString());
        });
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
//TODO
    }
}
