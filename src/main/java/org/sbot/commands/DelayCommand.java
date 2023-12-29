package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.math.BigDecimal;
import java.util.List;

public final class DelayCommand extends CommandAdapter {
//TODO add support in Alert

    public static final String NAME = "delay";
    static final String DESCRIPTION = "update the delay between two occurrences of the specified alert";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "delay", "new delay in days", true));

    public DelayCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("delay command: {}", event.getMessage().getContentRaw());

        long alertId = argumentReader.getMandatoryLong("alert id");
        BigDecimal value = argumentReader.getMandatoryNumber("new value");
// TODO
        alertStorage.getAlert(alertId).ifPresent(alert -> {
//            alert.setDelay(value);
            sendResponse(event.getChannel()::sendMessage, alert.toString());
        });
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
//TODO
    }
}
