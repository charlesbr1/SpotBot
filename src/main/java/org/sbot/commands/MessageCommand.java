package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.ALERT_MESSAGE_ARG_MAX_LENGTH;
import static org.sbot.utils.ArgumentValidator.*;

public final class MessageCommand extends CommandAdapter {

    public static final String NAME = "message";
    static final String DESCRIPTION = "update the message to shown when the alert is triggered **Add a link to your AT !** (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(STRING, "message", "a message to shown when the alert is triggered : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));

    public MessageCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        String message = requireAlertMessageLength(context.args.getLastArgs("message").orElse(""));
        LOGGER.debug("message command - alert_id : {}, message : {}", alertId, message);
        alertsDao.transactional(() -> context.reply(message(context, message, alertId)));
    }

    private EmbedBuilder message(@NotNull CommandContext context, String message, long alertId) {
        AnswerColorSmiley answer = updateAlert(alertId, context, () -> {
            alertsDao.updateMessage(alertId, message);
            return "Message of alert " + alertId + " updated to *" + message + "*" +
                    alertMessageTips(message, alertId);
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
