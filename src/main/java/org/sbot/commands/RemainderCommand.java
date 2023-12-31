package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.List;

import static org.sbot.alerts.Alert.ALERT_MESSAGE_ARG_MAX_LENGTH;
import static org.sbot.utils.ArgumentValidator.requireAlertMessageLength;

public final class RemainderCommand extends CommandAdapter {

    public static final String NAME = "remainder";
    static final String DESCRIPTION = "set a remainder related to a pair, to be triggered in the future, like for an airdrop event";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "date", "a date when to trigger the remainder, UTC expected format : " + Dates.DATE_TIME_FORMAT, true),
            new OptionData(OptionType.STRING, "remainder", "a message for this alert (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH));


    public RemainderCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        ZonedDateTime date = context.args.getMandatoryDateTime("date");
        String remainder = requireAlertMessageLength(context.args.getLastArgs("remainder").orElseThrow(() -> new IllegalArgumentException("Missing argument 'remainder'")));
        LOGGER.debug("remainder command - date : {}, remainder {}", date, remainder);
        alertsDao.transactional(() -> context.reply(remainder(context, date, remainder)));
    }

    private EmbedBuilder remainder(@NotNull CommandContext context, @NotNull ZonedDateTime date, @NotNull String remainder) {
        //TODO
        return embedBuilder(NAME, Color.red, "NOT IMPLEMENTED YET");
    }
}