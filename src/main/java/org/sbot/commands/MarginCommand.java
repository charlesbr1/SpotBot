package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.math.BigDecimal;
import java.util.List;

import static org.sbot.alerts.Alert.hasMargin;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class MarginCommand extends CommandAdapter {

    public static final String NAME = "margin";
    static final String DESCRIPTION = "set a margin for the alert that will warn once reached, then it should be set again, 0 to disable";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(OptionType.NUMBER, "margin", "a new margin for the alert, in ticker2 unit  (like USD for pair BTC/USD), 0 to disable", true)
                    .setMinValue(0d));


    public MarginCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        BigDecimal margin = requirePositive(context.args.getMandatoryNumber("margin"));
        LOGGER.debug("margin command - alert_id : {}, margin : {}", alertId, margin);
        alertsDao.transactional(() -> context.reply(margin(context, margin, alertId)));
    }

    private EmbedBuilder margin(@NotNull CommandContext context, @NotNull BigDecimal margin, long alertId) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, () -> {
            alertsDao.updateMargin(alertId, margin);
            return "Margin of alert " + alertId + " updated to " + margin +
                    (hasMargin(margin) ? "" : " (disabled)");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
