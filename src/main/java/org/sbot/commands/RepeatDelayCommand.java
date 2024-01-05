package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;

import java.util.List;

import static org.sbot.alerts.Alert.DEFAULT_REPEAT_DELAY_HOURS;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class RepeatDelayCommand extends CommandAdapter {

    public static final String NAME = "repeat-delay";
    static final String DESCRIPTION = "the delay to wait before a next repeat of the alert, in hours, 0 will set to default " + DEFAULT_REPEAT_DELAY_HOURS + " hours";

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
        AnswerColorSmiley answer = updateAlert(alertId, context, alert -> {
            alertStorage.updateAlert(alert.withRepeatDelay(0 != delay ? delay : DEFAULT_REPEAT_DELAY_HOURS));
            return "Repeat delay of alert " + alertId + " updated to " +
                    (0 != delay ? delay : "default " + DEFAULT_REPEAT_DELAY_HOURS) +
                    (delay > 1 ? " hours" : " hour");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
