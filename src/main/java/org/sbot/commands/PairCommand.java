package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.*;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.ArgumentValidator.requireTickerPairLength;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "ticker_pair", "the ticker or pair to show alerts on", true)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(1 + (2 * ALERT_MAX_TICKER_LENGTH)),
            new OptionData(INTEGER, "offset", "an optional offset to start the search (results are limited to 1000 alerts)", false)
                    .setMinValue(0));

    public PairCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String tickerPair = requireTickerPairLength(context.args.getMandatoryString("ticker_pair"));
        long offset = requirePositive(context.args.getLong("offset").orElse(0L));
        LOGGER.debug("pair command - ticker_pair : {}, offset : {}", tickerPair, offset);
        alertsDao.transactional(() -> context.reply(pair(context, tickerPair.toUpperCase(), offset)));
    }
    private List<EmbedBuilder> pair(@NotNull CommandContext context, @NotNull String tickerPair, long offset) {

        var split = tickerPair.split("/", 2);
        String ticker = split.length > 0 ? split[0] : tickerPair;
        String ticker2 = split.length > 1 ? split[1] : null;

        long total = isPrivateChannel(context) ?
                alertsDao.countAlertsOfUserAndTickers(context.user.getIdLong(), ticker, ticker2) :
                alertsDao.countAlertsOfServerAndTickers(context.getServerId(), ticker, ticker2);

        List<EmbedBuilder> alertMessages = (isPrivateChannel(context) ?
                alertsDao.getAlertsOfUserAndTickers(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE - 1, ticker, ticker2) :
                alertsDao.getAlertsOfServerAndTickers(context.getServerId(), offset, MESSAGE_PAGE_SIZE - 1, ticker, ticker2))
                .stream().map(CommandAdapter::toMessage).collect(toList());

        return paginatedAlerts(alertMessages, offset, total,
                () -> "!pair " + tickerPair + ' ' + (offset + MESSAGE_PAGE_SIZE - 1),
                () -> "pair " + tickerPair);
    }
}
