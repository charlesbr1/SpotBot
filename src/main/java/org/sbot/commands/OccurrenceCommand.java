package org.sbot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.util.List;

import static java.util.Objects.requireNonNull;

public final class OccurrenceCommand extends CommandAdapter {

    public static final String NAME = "occurrence";
    static final String DESCRIPTION = "update the number of time the alert will be thrown";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "occurrence", "number of time the specified alert will be thrown", true));

    public OccurrenceCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("occurrence command: {}", event.getMessage().getContentRaw());

        long alertId = argumentReader.getMandatoryLong("alert_id");
        long occurrence = argumentReader.getMandatoryLong("occurrence");

        event.getChannel().sendMessageEmbeds(occurrence(event.getAuthor(), event.getMember(), alertId, (short)occurrence)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("occurrence slash command: {}", event.getOptions());

        long alertId = requireNonNull(event.getOption("alert_id", OptionMapping::getAsLong));
        long occurrence = requireNonNull(event.getOption("occurrence", OptionMapping::getAsLong));

        event.replyEmbeds(occurrence(event.getUser(), event.getMember(), alertId, (short)occurrence)).queue();
    }

    private MessageEmbed occurrence(@NotNull User user, @Nullable Member member, long alertId, short occurrence) {
        AnswerColor answerColor = updateAlert(alertId, user, member, alert -> {
            alertStorage.addAlert(alert.withOccurrence(occurrence));
            return user.getAsMention() + " Occurrence of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer()).build();
    }
}
