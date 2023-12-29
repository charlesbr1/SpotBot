package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "ticker-pair", "the ticker or pair to show alerts on", true));

    public PairCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("pair command: {}", event.getMessage().getContentRaw());

        String ticker = argumentReader.getMandatoryString("ticker or pair").toUpperCase();
        pair(event.getChannel()::sendMessage, ticker);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("pair slash command: {}", event.getOptions());
        String ticker = requireNonNull(event.getOption("ticker-pair", OptionMapping::getAsString));
        pair(sender(event), ticker);
    }

    private void pair(@NotNull MessageSender sender, @NotNull String ticker) {
        String alerts = alertStorage.getAlerts()
                .filter(alert -> alert.getReadablePair().contains(ticker))
                .map(Alert::toString)
                .collect(Collectors.joining("\n"));
        sendResponse(sender, alerts.isEmpty() ? "No alert found for " + ticker : alerts);
    }
}
