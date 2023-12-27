package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.List;

public final class HelpCommand extends CommandAdapter {

    public static final String NAME = "help";
    static final String HELP = "!help - print this help";

    public HelpCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("help command: {}", event.getMessage().getContentRaw());

        sendResponse(event, String.join("\n", List.of(
                HELP,
                DeleteCommand.HELP,
                ListCommand.HELP,
                OccurrenceCommand.HELP,
                OwnerCommand.HELP,
                PairCommand.HELP,
                RangeCommand.HELP,
                DelayCommand.HELP,
                ThresholdCommand.HELP,
                TrendCommand.HELP,
                UpTimeCommand.HELP)));
    }
}
