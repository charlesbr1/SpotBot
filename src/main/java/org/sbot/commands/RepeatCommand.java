package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;

import java.util.List;

import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class RepeatCommand extends CommandAdapter {

    public static final String NAME = "repeat";
    static final String DESCRIPTION = "update the number of time the alert will be rethrown";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "alert_id", "id of the alert", true),
            new OptionData(OptionType.INTEGER, "repeat", "number of time the specified alert will be rethrown", true));

    public RepeatCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        LOGGER.debug("repeat command");
        long alertId = requirePositive(command.args.getMandatoryLong("alert_id"));
        short repeat = requirePositiveShort(command.args.getMandatoryLong("repeat"));
        command.reply(repeat(command, alertId, repeat));
    }

    private EmbedBuilder repeat(@NotNull Command command, long alertId, short repeat) {
        AnswerColor answerColor = updateAlert(alertId, command, alert -> {
            alertStorage.addAlert(alert.withRepeat(repeat));
            return command.user.getAsMention() + " Occurrence of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer());
    }
}
