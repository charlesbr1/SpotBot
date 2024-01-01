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

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String DESCRIPTION = "delete an alert (only the alert owner or an admin is allowed to do it)";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert to delete", true));

    public DeleteCommand(@NotNull AlertStorage alertStorage) {
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
        LOGGER.debug("delete command: {}", event.getMessage().getContentRaw());
        long alertId = requirePositive(argumentReader.getMandatoryLong("alert_id"));
        event.getChannel().sendMessageEmbeds(delete(event.getAuthor(), event.getMember(), alertId)).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("delete slash command: {}", event.getOptions());
        long alertId = requirePositive(requireNonNull(event.getOption("alert_id", OptionMapping::getAsLong)));
        event.replyEmbeds(delete(event.getUser(), event.getMember(), alertId)).queue();
    }

    private MessageEmbed delete(@NotNull User user, @Nullable Member member, long alertId) {
        AnswerColor answerColor = updateAlert(alertId, user, member, alert -> {
            alertStorage.deleteAlert(alertId);
            return user.getAsMention() + " Alert " + alertId + " deleted";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer()).build();
    }
}