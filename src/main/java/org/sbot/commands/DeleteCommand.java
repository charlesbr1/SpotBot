package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.Alerts;

import java.util.List;

import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class DeleteCommand extends CommandAdapter {

    public static final String NAME = "delete";
    static final String DESCRIPTION = "delete an alert (only the alert owner or an admin is allowed to do it)";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert to delete", true).setMinValue(0));

    public DeleteCommand(@NotNull Alerts alerts) {
        super(alerts, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        LOGGER.debug("delete command - alert_id : {}", alertId);
        context.reply(delete(context, alertId));
    }

    private EmbedBuilder delete(@NotNull CommandContext context, long alertId) {
        AnswerColorSmiley answer = updateAlert(alertId, context, alert -> {
            alerts.deleteAlert(alertId);
            return "Alert " + alertId + " deleted";
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}