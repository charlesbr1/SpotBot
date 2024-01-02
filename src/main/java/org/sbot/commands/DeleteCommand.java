package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;

import java.util.List;

import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String DESCRIPTION = "delete an alert (only the alert owner or an admin is allowed to do it)";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert to delete", true).setMinValue(0));

    public DeleteCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        long alertId = requirePositive(command.args.getMandatoryLong("alert_id"));
        LOGGER.debug("delete command - alert_id : {}", alertId);
        command.reply(delete(command, alertId));
    }

    private EmbedBuilder delete(@NotNull Command command, long alertId) {
        AnswerColor answerColor = updateAlert(alertId, command, alert -> {
            alertStorage.deleteAlert(alertId);
            return command.user.getAsMention() + " Alert " + alertId + " deleted";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer());
    }
}