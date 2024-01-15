package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static org.sbot.alerts.Alert.DEFAULT_REPEAT_DELAY_HOURS;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class RepeatDelayCommand extends CommandAdapter {

    public static final String NAME = "repeat-delay";
    static final String DESCRIPTION = "update the delay to wait before a next raise of the alert, in hours, 0 will set to default " + DEFAULT_REPEAT_DELAY_HOURS + " hours";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(INTEGER, "alert_id", "id of the alert", true)
                            .setMinValue(0),
                    option(INTEGER, "repeat_delay", "a new delay in hours", true)
                            .setRequiredRange(0, Short.MAX_VALUE));

    public RepeatDelayCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short repeatDelay = requirePositiveShort(context.args.getMandatoryLong("repeat_delay"));
        LOGGER.debug("repeat delay command - alert_id : {}, repeat_delay : {}", alertId, repeatDelay);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, repeatDelay(context.noMoreArgs(), alertId, repeatDelay)));
    }

    private EmbedBuilder repeatDelay(@NotNull CommandContext context, long alertId, short repeatDelay) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, alert -> {
            if(remainder == alert.type()) {
                throw new IllegalArgumentException("You can't set the repeat-delay of a remainder alert");
            }
            alertsDao.updateRepeatDelay(alertId, 0 != repeatDelay ? repeatDelay : DEFAULT_REPEAT_DELAY_HOURS);
            return "Repeat delay of alert " + alertId + " updated to " +
                    (0 != repeatDelay ? repeatDelay : "default " + DEFAULT_REPEAT_DELAY_HOURS) +
                    (repeatDelay > 1 ? " hours" : " hour");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
