package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "owner", "the owner of alerts to show", true),
            new OptionData(STRING, "ticker-pair", "an optional ticker or pair to filter on", false));

    public OwnerCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("owner command: {}", event.getMessage().getContentRaw());
        String owner = argumentReader.getMandatoryString("owner");
        String pair = argumentReader.getNextString().filter(not(String::isBlank)).map(String::toUpperCase).orElse(null);
        owner(event.getChannel()::sendMessage, owner, pair);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("owner slash command: {}", event.getOptions());
        String owner = requireNonNull(event.getOption("owner", OptionMapping::getAsString));
        String pair = event.getOption("ticker-pair", OptionMapping::getAsString);
        owner(sender(event), owner, pair);
    }

    private void owner(@NotNull MessageSender sender, @NotNull String owner, @Nullable String pair) {
        Predicate<Alert> check = null != pair ?
                alert -> alert.owner.equals(owner) && alert.getReadablePair().contains(pair) :
                alert -> alert.owner.equals(owner);

        String alerts = alertStorage.getAlerts()
                .filter(check).map(Alert::toString)
                .collect(Collectors.joining("\n"));

        sendResponse(sender, alerts.isEmpty() ?
                "No alert found for " + owner + (null != pair ? " and " + pair : "") : alerts);
    }
}
