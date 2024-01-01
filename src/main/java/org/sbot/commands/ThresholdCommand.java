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

public final class ThresholdCommand extends CommandAdapter {
    //TODO add support in Alert
    public static final String NAME = "threshold";
    static final String DESCRIPTION = "update the threshold of the given alert, which will be pre triggered when price reach it";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "threshold", "new threshold in %", true));


    public ThresholdCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("threshold command: {}", event.getMessage().getContentRaw());

        long alertId = requirePositive(argumentReader.getMandatoryLong("alert_id"));
        short threshold = requirePositiveShort(argumentReader.getMandatoryLong("threshold"));

        event.getChannel().sendMessageEmbeds(threshold(event.getAuthor(), event.getMember(), alertId, threshold)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("threshold slash command: {}", event.getOptions());

        long alertId = requirePositive(requireNonNull(event.getOption("alert_id", OptionMapping::getAsLong)));
        short threshold = requirePositiveShort(requireNonNull(event.getOption("threshold", OptionMapping::getAsLong)));

        event.replyEmbeds(threshold(event.getUser(), event.getMember(), alertId, threshold)).queue();
    }

    private MessageEmbed threshold(@NotNull User user, @Nullable Member member, long alertId, short threshold) {
        AnswerColor answerColor = updateAlert(alertId, user, member, alert -> {
            alertStorage.addAlert(alert.withThreshold(threshold));
            return user.getAsMention() + " Threshold of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer()).build();
    }
}
