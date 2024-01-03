package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;

import java.util.List;

import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class RepeatCommand extends CommandAdapter {

    public static final String NAME = "repeat";
    static final String DESCRIPTION = "update the number of time the alert will be rethrown";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(OptionType.INTEGER, "repeat", "number of time the specified alert will be rethrown", true)
                    .setRequiredRange(0, Short.MAX_VALUE));

    public RepeatCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short repeat = requirePositiveShort(context.args.getMandatoryLong("repeat"));
        LOGGER.debug("repeat command - alert_id : {}, repeat : {}", alertId, repeat);
        context.reply(repeat(context, alertId, repeat));
    }

    private EmbedBuilder repeat(@NotNull CommandContext context, long alertId, short repeat) {
        AnswerColor answerColor = updateAlert(alertId, context, alert -> {
            alertStorage.addAlert(alert.withRepeat(repeat));
            return context.user.getAsMention() + " Repeat of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer());
    }
}
