package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static org.sbot.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class SnoozeCommand extends CommandAdapter {

    private static final String NAME = "snooze";
    static final String DESCRIPTION = "update the delay to wait between two raises of the alert, in hours, 0 will set to default " + DEFAULT_SNOOZE_HOURS + " hours";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(INTEGER, "alert_id", "id of the alert", true)
                            .setMinValue(0),
                    option(INTEGER, "snooze", "a new delay in hours", true)
                            .setRequiredRange(0, Short.MAX_VALUE));

    public SnoozeCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short snooze = requirePositiveShort(context.args.getMandatoryLong("snooze"));
        LOGGER.debug("snooze command - alert_id : {}, snooze : {}", alertId, snooze);
        context.alertsDao.transactional(() -> context.noMoreArgs().reply(responseTtlSeconds, snooze(context, alertId, snooze)));
    }

    private EmbedBuilder snooze(@NotNull CommandContext context, long alertId, short snooze) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, alert -> {
            if(remainder == alert.type()) {
                throw new IllegalArgumentException("You can't set the snooze of a remainder alert");
            }
            context.alertsDao.updateSnooze(alertId, 0 != snooze ? snooze : DEFAULT_SNOOZE_HOURS);
            return "Repeat delay of alert " + alertId + " updated to " +
                    (0 != snooze ? snooze : "default " + DEFAULT_SNOOZE_HOURS) +
                    (snooze > 1 ? " hours" : " hour");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
