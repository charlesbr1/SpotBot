package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.ArgumentValidator.*;

public final class PairCommand extends CommandAdapter {

    private static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";
    private static final int RESPONSE_TTL_SECONDS = 300;

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "ticker_pair", "a ticker or a pair to filter alerts on", true)
                            .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                    option(INTEGER, "offset", "an offset from where to start the search (results are limited to 1000 alerts)", false)
                            .setMinValue(0));

    public PairCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String tickerOrPair = requireTickerPairLength(context.args.getMandatoryString("ticker_pair"));
        long offset = requirePositive(context.args.getLong("offset").orElse(0L));
        LOGGER.debug("pair command - ticker_pair : {}, offset : {}", tickerOrPair, offset);
        context.alertsDao.transactional(() -> context.noMoreArgs().reply(responseTtlSeconds, pair(context, tickerOrPair.toUpperCase(), offset)));
    }
    private List<EmbedBuilder> pair(@NotNull CommandContext context, @NotNull String tickerOrPair, long offset) {
        return context.alertsDao.transactional(() -> {
            long total = isPrivateChannel(context) ?
                    context.alertsDao.countAlertsOfUserAndTickers(context.user.getIdLong(), tickerOrPair) :
                    context.alertsDao.countAlertsOfServerAndTickers(context.serverId(), tickerOrPair);

            List<EmbedBuilder> alertMessages = (isPrivateChannel(context) ?
                    context.alertsDao.getAlertsOfUserAndTickers(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE - 1, tickerOrPair) :
                    context.alertsDao.getAlertsOfServerAndTickers(context.serverId(), offset, MESSAGE_PAGE_SIZE - 1, tickerOrPair))
                    .stream().map(alert -> toMessage(context, alert)).collect(toList());

            return paginatedAlerts(alertMessages, offset, total,
                    () -> "pair " + tickerOrPair + " " + (offset + MESSAGE_PAGE_SIZE - 1),
                    () -> " for ticker or pair : " + tickerOrPair);
        });
    }
}
