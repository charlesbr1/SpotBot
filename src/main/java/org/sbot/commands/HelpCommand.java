package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.List;

public final class HelpCommand extends CommandAdapter {

    public static final String NAME = "help";
    static final String DESCRIPTION = "print this help";

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of();
    }

    public HelpCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("help command: {}", event.getMessage().getContentRaw());

        sendResponse(event, String.join("\n", List.of(
                DESCRIPTION,
                DeleteCommand.DESCRIPTION,
                ListCommand.DESCRIPTION,
                OccurrenceCommand.DESCRIPTION,
                OwnerCommand.DESCRIPTION,
                PairCommand.DESCRIPTION,
                RangeCommand.DESCRIPTION,
                DelayCommand.DESCRIPTION,
                ThresholdCommand.DESCRIPTION,
                TrendCommand.DESCRIPTION,
                UpTimeCommand.DESCRIPTION)));
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
//TODO
    }
}
