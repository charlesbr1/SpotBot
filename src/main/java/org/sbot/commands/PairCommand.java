package org.sbot.commands;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.alerts.Alert;
import org.sbot.utils.ArgumentReader;
import org.sbot.storage.AlertStorage;

import java.util.List;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";

    public PairCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of(new OptionData(STRING, "ticker", "the ticker or pair to show alerts on", true));
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("pair command: {}", event.getMessage().getContentRaw());

        String ticker = argumentReader.getMandatoryString("ticker or pair").toUpperCase();
        pair(event, ticker);

    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
        LOGGER.debug("pair slash command: {}", event.getOptions());
        String ticker = event.getOption("ticker", OptionMapping::getAsString);
        pair(event, ticker);
    }

    private void pair(GenericEvent event, String ticker) {
        String alerts = alertStorage.getAlerts()
                .filter(alert -> alert.getReadablePair().contains(ticker))
                .map(Alert::toString)
                .collect(Collectors.joining("\n"));
        sendResponse(event, alerts.isEmpty() ? "No alert found for " + ticker : alerts);
    }
}
