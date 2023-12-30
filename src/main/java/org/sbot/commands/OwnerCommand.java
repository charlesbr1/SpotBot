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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static net.dv8tion.jda.api.interactions.commands.OptionType.USER;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";

    static final List<OptionData> options = List.of(
            new OptionData(USER, "owner", "the owner of alerts to show", true),
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
        long ownerId = argumentReader.getMandatoryUserId("owner");
        String pair = argumentReader.getNextString().filter(not(String::isBlank)).map(String::toUpperCase).orElse(null);
        event.getChannel().sendMessageEmbeds(owner(event.getAuthor(), event.getMember(), ownerId, pair)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("owner slash command: {}", event.getOptions());
        User owner = requireNonNull(event.getOption("owner", OptionMapping::getAsUser));
        String pair = event.getOption("ticker-pair", OptionMapping::getAsString);
        event.replyEmbeds(owner(event.getUser(), event.getMember(), owner.getIdLong(), pair)).queue();
    }

    private MessageEmbed owner(@NotNull User user, @Nullable Member member, long ownerId, @Nullable String pair) {
        if(null == member && user.getIdLong() != ownerId) {
            return embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel").build();
        }

        Predicate<Alert> ownerAndPair = null != pair ?
                alert -> alert.userId == ownerId && alert.getReadablePair().contains(pair) :
                alert -> alert.userId == ownerId;

        String alerts = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(user, member))
                .filter(ownerAndPair).map(Alert::toString)
                .collect(Collectors.joining("\n"));

        String answer = alerts.isEmpty() ?
                "No alert found for user <@" + ownerId + '>' + (null != pair ? " and " + pair : "") : alerts;

        return embedBuilder(NAME, Color.green, answer).build();
    }
}
