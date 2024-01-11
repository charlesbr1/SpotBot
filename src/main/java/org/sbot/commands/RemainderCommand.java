package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.RemainderAlert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.utils.ArgumentValidator.requireAlertMessageLength;
import static org.sbot.utils.ArgumentValidator.requireTickerLength;
import static org.sbot.utils.Dates.formatUTC;

public final class RemainderCommand extends CommandAdapter {

    public static final String NAME = "remainder";
    static final String DESCRIPTION = "set a remainder related to a pair, to be triggered in the future, like for an airdrop event";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "ticker1", "the first ticker", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_TICKER_LENGTH),
            new OptionData(STRING, "ticker2", "the second ticker", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_TICKER_LENGTH),
            new OptionData(OptionType.STRING, "date", "a date when to trigger the remainder, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(OptionType.STRING, "message", "a message for this remainder (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));


    public RemainderCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String ticker1 = requireTickerLength(context.args.getMandatoryString("ticker1"));
        String ticker2 = requireTickerLength(context.args.getMandatoryString("ticker2"));
        ZonedDateTime date = context.args.getMandatoryDateTime("date");
        String message = requireAlertMessageLength(context.args.getLastArgs("message")
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("remainder command - ticker1 : {}, ticker2 : {}, date : {}, remainder {}", ticker1, ticker2, date, message);
        alertsDao.transactional(() -> context.reply(RESPONSE_TTL_SECONDS, remainder(context, ticker1, ticker2, date, message)));
    }

    private EmbedBuilder remainder(@NotNull CommandContext context, @NotNull String ticker1, @NotNull String ticker2, @NotNull ZonedDateTime fromDate, @NotNull String message) {
        RemainderAlert remainderAlert = new RemainderAlert(context.user.getIdLong(),
                context.getServerId(), ticker1, ticker2, message, fromDate);

        long alertId = alertsDao.addAlert(remainderAlert);
        String answer = context.user.getAsMention() + " New remainder added with id " + alertId +
                "\n\n* pair : " + remainderAlert.getSlashPair() +
                "\n* date : " + formatUTC(fromDate) +
                "\n* message : " + message;

        return embedBuilder(NAME, Color.green, answer);
    }
}