package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.alerts.Alert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.List;
import java.util.stream.Collectors;

public final class ListCommand extends CommandAdapter {

    public static final String NAME = "list";
    static final String DESCRIPTION = "TODO !list (exchange|pair|alerts), example : !list binance, !list ETH, !list ETH/BTC, !list alerts";

    public ListCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of();
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("list command: {}", event.getMessage().getContentRaw());

        String arg = argumentReader.getMandatoryString("exchange or ticker or pair or 'alerts'");
        if("alerts".equals(arg)) {
            String messages = alertStorage.getAlerts().map(Alert::toString).collect(Collectors.joining("\n"));
            sendResponse(event, messages);
        }
        //TODO
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
//TODO
    }
}
