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

public final class RepeatDelayCommand extends CommandAdapter {

    public static final String NAME = "repeat-delay";
    static final String DESCRIPTION = "update the delay between two repeats of the alert";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(OptionType.INTEGER, "repeat_delay", "new delay in hours", true)
                    .setRequiredRange(0, Short.MAX_VALUE));

    public RepeatDelayCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short delay = requirePositiveShort(context.args.getMandatoryLong("repeat_delay"));
        LOGGER.debug("repeat delay command - alert_id : {}, repeat_delay : {}", alertId, delay);
        context.reply(repeatDelay(context, alertId, delay));
    }

    private EmbedBuilder repeatDelay(@NotNull CommandContext context, long alertId, short delay) {
        AnswerColor answerColor = updateAlert(alertId, context, alert -> {
                    alertStorage.addAlert(alert.withRepeatDelay(delay));
                    return context.user.getAsMention() + " Delay of alert " + alertId + " updated";
                });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer());
    }
}
