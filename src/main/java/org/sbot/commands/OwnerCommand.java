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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";

    public OwnerCommand(AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return List.of(new OptionData(STRING, "owner", "the owner of alerts to show", true),
                new OptionData(STRING, "ticker", "an optional ticker or pair to filter on", false));
    }

    @Override
    public void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event) {
        LOGGER.debug("owner command: {}", event.getMessage().getContentRaw());
        String owner = argumentReader.getMandatoryString("owner");
        String pair = argumentReader.getNextString().map(String::toUpperCase).orElse(null);
        owner(event, owner, pair);
    }

    @Override
    public void onEvent(SlashCommandInteractionEvent event) {
        LOGGER.debug("owner slash command: {}", event.getOptions());
        String owner = event.getOption("owner", OptionMapping::getAsString);
        String pair = event.getOption("pair", OptionMapping::getAsString);
        owner(event, owner, pair);
    }

    private void owner(GenericEvent event, String owner, String pair) {
        Predicate<Alert> check = null != pair ?
                alert -> alert.owner.equals(owner) && alert.getReadablePair().contains(pair) :
                alert -> alert.owner.equals(owner);

        String alerts = alertStorage.getAlerts()
                .filter(check).map(Alert::toString)
                .collect(Collectors.joining("\n"));

        sendResponse(event, alerts.isEmpty() ?
                "No alert found for " + owner + (null != pair ? " and " + pair : "") : alerts);
    }
}
