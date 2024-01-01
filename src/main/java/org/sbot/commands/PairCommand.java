package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "ticker_pair", "the ticker or pair to show alerts on", true));

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
        event.getChannel().sendMessageEmbeds(pair(event.getAuthor(), event.getMember(), ticker)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("pair slash command: {}", event.getOptions());
        String ticker = requireNonNull(event.getOption("ticker_pair", OptionMapping::getAsString));
        event.replyEmbeds(pair(event.getUser(), event.getMember(), ticker)).queue();
    }

    private MessageEmbed pair(@NotNull User user, @Nullable Member member, @NotNull String ticker) {

        String alerts = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(user, member))
                .filter(alert -> alert.getSlashPair().contains(ticker))
                .map(Alert::toString)
                .collect(Collectors.joining("\n"));

        String answer = alerts.isEmpty() ? "No alert found for " + ticker : alerts;

        return embedBuilder(NAME, Color.green, answer).build();
    }
}
