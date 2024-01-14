package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.math.BigDecimal;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.NUMBER;
import static org.sbot.alerts.Alert.Type.remainder;
import static org.sbot.alerts.Alert.hasMargin;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class MarginCommand extends CommandAdapter {

    public static final String NAME = "margin";
    static final String DESCRIPTION = "set a margin for the alert that will warn once reached, then it should be set again, 0 to disable";
    private static final int RESPONSE_TTL_SECONDS = 30;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(INTEGER, "alert_id", "id of the alert", true)
                            .setMinValue(0),
                    option(NUMBER, "margin", "a new margin for the alert, in ticker2 unit (like USD for pair BTC/USD), 0 to disable", true)
                            .setMinValue(0d));


    public MarginCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        BigDecimal margin = requirePositive(context.args.getMandatoryNumber("margin"));
        LOGGER.debug("margin command - alert_id : {}, margin : {}", alertId, margin);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, margin(context.noMoreArgs(), margin, alertId)));
    }

    private EmbedBuilder margin(@NotNull CommandContext context, @NotNull BigDecimal margin, long alertId) {
        AnswerColorSmiley answer = securedAlertUpdate(alertId, context, type -> {
            if(remainder == type) {
                throw new IllegalArgumentException("You can't set the margin of a remainder alert");
            }
            alertsDao.updateMargin(alertId, margin);
            return "Margin of alert " + alertId + " updated to " + margin +
                    (hasMargin(margin) ? "" : " (disabled)");
        });
        return embedBuilder(answer.smiley() + ' ' + context.user.getEffectiveName(), answer.color(), answer.answer());
    }
}
