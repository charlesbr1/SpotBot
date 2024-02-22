package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.RemainderAlert;
import org.sbot.utils.Dates;

import java.time.ZonedDateTime;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.entities.alerts.Alert.NEW_ALERT_ID;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMAT;

public final class RemainderCommand extends CommandAdapter {

    static final String NAME = "remainder";
    static final String DESCRIPTION = "set a remainder related to a pair, to be raised in the future, like for an airdrop event";
    private static final int RESPONSE_TTL_SECONDS = 180;

    static final List<OptionData> optionList = List.of(
            option(STRING, PAIR_ARGUMENT, "the pair, like EUR/USDT", true)
                    .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
            option(STRING, MESSAGE_ARGUMENT, "a message for this remainder (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", true)
                    .setMaxLength(ALERT_MESSAGE_ARG_MAX_LENGTH),
            option(STRING, DATE_ARGUMENT, "a future date when to trigger the remainder, UTC expected format : " + Dates.DATE_TIME_FORMAT, true)
                    .setMinLength(DATE_TIME_FORMAT.length()));

    private static final SlashCommandData options = Commands.slash(NAME, DESCRIPTION).addOptions(optionList);

    public RemainderCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String pair = requirePairFormat(context.args.getMandatoryString(PAIR_ARGUMENT).toUpperCase());
        var reversed = context.args.reversed();
        ZonedDateTime now = Dates.nowUtc(context.clock());
        ZonedDateTime date = requireInFuture(now, reversed.getMandatoryDateTime(context.locale, context.timezone, context.clock(), DATE_ARGUMENT));
        String message = requireAlertMessageMaxLength(reversed.getLastArgs(MESSAGE_ARGUMENT)
                .orElseThrow(() -> new IllegalArgumentException("Please add a message to your alert !")));

        LOGGER.debug("remainder command - pair : {}, date : {}, remainder {}", pair, date, message);
        context.reply(remainder(context, now, pair, date, message), responseTtlSeconds);
    }

    private Message remainder(@NotNull CommandContext context, @NotNull ZonedDateTime now, @NotNull String pair, @NotNull ZonedDateTime fromDate, @NotNull String message) {
        RemainderAlert remainderAlert = new RemainderAlert(NEW_ALERT_ID, context.user.getIdLong(),
                context.serverId(), now, // creation date
                fromDate, // listening date
                pair, message, fromDate);
        return saveAlert(context, now, remainderAlert);
    }
}