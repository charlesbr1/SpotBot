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

public final class RepeatDelayCommand extends CommandAdapter {

    public static final String NAME = "repeat-delay";
    static final String DESCRIPTION = "update the delay between two repeats of the specified alert";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "repeat_delay", "new delay in hours", true));

    public RepeatDelayCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("repeat delay command: {}", event.getMessage().getContentRaw());

        long alertId = requirePositive(argumentReader.getMandatoryLong("alert_id"));
        short delay = requirePositiveShort(argumentReader.getMandatoryLong("repeat delay"));

        event.getChannel().sendMessageEmbeds(delay(event.getAuthor(), event.getMember(), alertId, delay)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("repeat delay slash command: {}", event.getOptions());

        long alertId = requirePositive(requireNonNull(event.getOption("alert_id", OptionMapping::getAsLong)));
        short delay = requirePositiveShort(requireNonNull(event.getOption("repeat_delay", OptionMapping::getAsLong)));

        event.replyEmbeds(delay(event.getUser(), event.getMember(), alertId, (short)delay)).queue();
    }

    private MessageEmbed delay(@NotNull User user, @Nullable Member member, long alertId, short delay) {
        AnswerColor answerColor = updateAlert(alertId, user, member, alert -> {
                    alertStorage.addAlert(alert.withRepeatDelay(delay));
                    return user.getAsMention() + " Delay of alert " + alertId + " updated";
                });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer()).build();
    }
}
