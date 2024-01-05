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

public final class MarginCommand extends CommandAdapter {

    public static final String NAME = "margin";
    static final String DESCRIPTION = "set a margin for the alert that will warn once reached, then it should be set again, 0 to disable";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(OptionType.INTEGER, "margin", "new margin in alert ticker2 unit, 0 to disable", true)
                    .setRequiredRange(0, Short.MAX_VALUE));


    public MarginCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short margin = requirePositiveShort(context.args.getMandatoryLong("margin"));
        LOGGER.debug("margin command - alert_id : {}, margin : {}", alertId, margin);
        context.reply(margin(context, alertId, margin));
    }

    private EmbedBuilder margin(@NotNull CommandContext context, long alertId, short margin) {
        AnswerColorSmiley answer = updateAlert(alertId, context, alert -> {
            alertStorage.updateAlert(alert.withMargin(margin));
            return "Margin of alert " + alertId + " updated to " + margin +
                    (margin != 0 ? "" : " (disabled)");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
