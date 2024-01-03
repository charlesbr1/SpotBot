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

public final class ThresholdCommand extends CommandAdapter {
    //TODO add support in Alert
    public static final String NAME = "threshold";
    static final String DESCRIPTION = "update the threshold of the given alert, which will be pre triggered when price reach it";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(OptionType.INTEGER, "threshold", "new threshold in %", true)
                    .setRequiredRange(0, Short.MAX_VALUE));


    public ThresholdCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short threshold = requirePositiveShort(context.args.getMandatoryLong("threshold"));
        LOGGER.debug("threshold command - alert_id : {}, threshold : {}", alertId, threshold);
        context.reply(threshold(context, alertId, threshold));
    }

    private EmbedBuilder threshold(@NotNull CommandContext context, long alertId, short threshold) {
        AnswerColor answerColor = updateAlert(alertId, context, alert -> {
            alertStorage.addAlert(alert.withThreshold(threshold));
            return context.user.getAsMention() + " Threshold of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer());
    }
}
