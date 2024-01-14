package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.Alert.hasRepeat;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requirePositiveShort;

public final class RepeatCommand extends CommandAdapter {

    public static final String NAME = "repeat";
    static final String DESCRIPTION = "update the number of time the alert will be raise, 0 to disable";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(INTEGER, "alert_id", "id of the alert", true)
                            .setMinValue(0),
                    option(INTEGER, "repeat", "number of time the specified alert will be raise again", true)
                            .setRequiredRange(0, Short.MAX_VALUE));

    public RepeatCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        short repeat = requirePositiveShort(context.args.getMandatoryLong("repeat"));
        LOGGER.debug("repeat command - alert_id : {}, repeat : {}", alertId, repeat);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, repeat(context.noMoreArgs(), alertId, repeat)));
    }

    private EmbedBuilder repeat(@NotNull CommandContext context, long alertId, short repeat) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, type -> {
            if(remainder == type) {
                throw new IllegalArgumentException("You can't set the repeat of a remainder alert");
            }
            alertsDao.updateRepeat(alertId, repeat);
            return "Repeat of alert " + alertId + " updated to " + repeat +
                    (!hasRepeat(repeat) ? " (disabled)" : "");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
