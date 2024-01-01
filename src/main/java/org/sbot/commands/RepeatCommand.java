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
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class RepeatCommand extends CommandAdapter {

    public static final String NAME = "repeat";
    static final String DESCRIPTION = "update the number of time the alert will be rethrown";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "repeat", "number of time the specified alert will be rethrown", true));

    public RepeatCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("repeat command: {}", event.getMessage().getContentRaw());

        long alertId = requirePositive(argumentReader.getMandatoryLong("alert_id"));
        short repeat = requirePositiveShort(argumentReader.getMandatoryLong("repeat"));

        event.getChannel().sendMessageEmbeds(occurrence(event.getAuthor(), event.getMember(), alertId, repeat)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("repeat slash command: {}", event.getOptions());

        long alertId = requirePositive(requireNonNull(event.getOption("alert_id", OptionMapping::getAsLong)));
        short repeat = requirePositiveShort(requireNonNull(event.getOption("occurrence", OptionMapping::getAsLong)));

        event.replyEmbeds(occurrence(event.getUser(), event.getMember(), alertId, repeat)).queue();
    }

    private MessageEmbed occurrence(@NotNull User user, @Nullable Member member, long alertId, short repeat) {
        AnswerColor answerColor = updateAlert(alertId, user, member, alert -> {
            alertStorage.addAlert(alert.withRepeat(repeat));
            return user.getAsMention() + " Occurrence of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer()).build();
    }
}
