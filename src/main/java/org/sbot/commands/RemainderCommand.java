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
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatUTC;

public final class RemainderCommand extends CommandAdapter {

    public static final String NAME = "remainder";
    static final String DESCRIPTION = "set a remainder related to a pair, to be triggered in the future, like for an airdrop event";
    private static final int RESPONSE_TTL_SECONDS = 60;

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "pair", "the pair, like EUR/USD", true)
                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
            new OptionData(OptionType.STRING, "date", "a future date when to trigger the remainder, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(OptionType.STRING, "message", "a message for this remainder (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));


    public RemainderCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        ZonedDateTime date = requireInFuture(context.args.getMandatoryDateTime("date"));
        String message = requireAlertMessageLength(context.args.getLastArgs("message")
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("remainder command - pair : {}, date : {}, remainder {}", pair, date, message);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, remainder(context, pair, date, message)));
    }

    private EmbedBuilder remainder(@NotNull CommandContext context, @NotNull String pair, @NotNull ZonedDateTime fromDate, @NotNull String message) {
        RemainderAlert remainderAlert = new RemainderAlert(context.user.getIdLong(),
                context.getServerId(), pair, message, fromDate);

        long alertId = alertsDao.addAlert(remainderAlert);
        String answer = context.user.getAsMention() + " New remainder added with id " + alertId +
                "\n\n* pair : " + remainderAlert.pair +
                "\n* date : " + formatUTC(fromDate) +
                "\n* message : " + message;

        return embedBuilder(NAME, Color.green, answer);
    }
}