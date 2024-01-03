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
    public void onCommand(@NotNull Command command) {
        long alertId = requirePositive(command.args.getMandatoryLong("alert_id"));
        short threshold = requirePositiveShort(command.args.getMandatoryLong("threshold"));
        LOGGER.debug("threshold command - alert_id : {}, threshold : {}", alertId, threshold);
        command.reply(threshold(command, alertId, threshold));
    }

    private EmbedBuilder threshold(@NotNull Command command, long alertId, short threshold) {
        AnswerColor answerColor = updateAlert(alertId, command, alert -> {
            alertStorage.addAlert(alert.withThreshold(threshold));
            return command.user.getAsMention() + " Threshold of alert " + alertId + " updated";
        });
        return embedBuilder(NAME, answerColor.color(), answerColor.answer());
    }
}
