package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.utils.ArgumentValidator.*;

public final class MessageCommand extends CommandAdapter {

    public static final String NAME = "message";
    static final String DESCRIPTION = "update the message to shown when the alert is raised, add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(INTEGER, "alert_id", "id of the alert", true)
                            .setMinValue(0),
                    option(STRING, "message", "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                            .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));

    public MessageCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        String message = requireAlertMessageLength(context.args.getLastArgs("message").orElse(""));
        LOGGER.debug("message command - alert_id : {}, message : {}", alertId, message);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, message(context, message, alertId)));
    }

    private EmbedBuilder message(@NotNull CommandContext context, String message, long alertId) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, alert -> {
            alertsDao.updateMessage(alertId, message);
            return "Message of alert " + alertId + " updated to *" + message + "*" +
                    (remainder != alert.type() ? alertMessageTips(message, alertId) : "");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
