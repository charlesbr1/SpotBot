package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.alerts.Alert.*;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";

    static final List<OptionData> options = List.of(
            new OptionData(USER, "owner", "the owner of alerts to show", true),
            new OptionData(STRING, "ticker_pair", "an optional ticker or pair to filter on", false)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(1 + (2 * ALERT_MAX_TICKER_LENGTH)),
            new OptionData(INTEGER, "offset", "an optional offset to start the search (results are limited to 1000 alerts)", false)
                    .setMinValue(0));

    public OwnerCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long ownerId = context.args.getMandatoryUserId("owner");
        String tickerPair = null;
        Long offset = context.args.getLong("offset").orElse(null);
        if(null == offset) { // if the next arg can't be parsed as a long, may be it's a string
            tickerPair = context.args.getString("ticker_pair")
                    .map(ArgumentValidator::requireTickerPairLength)
                    .map(String::toUpperCase).orElse(null);
            offset = context.args.getLong("offset").orElse(0L);
        }
        var finalOffset = offset;
        var finalTickerPair = tickerPair;
        LOGGER.debug("owner command - owner : {}, ticker_pair : {}, offset : {}", ownerId, finalTickerPair, finalOffset);
        alertsDao.transactional(() -> context.reply(owner(context, finalTickerPair, ownerId, requirePositive(finalOffset))));
    }

    private List<EmbedBuilder> owner(@NotNull CommandContext context, @Nullable String tickerPair, long ownerId, long offset) {
        if(null == context.member && context.user.getIdLong() != ownerId) {
            return List.of(embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel"));
        }

        var split = Optional.ofNullable(tickerPair).map(t -> t.split("/", 2)).orElse(new String[0]);
        String ticker = split.length > 0 ? split[0] : tickerPair;
        String ticker2 = split.length > 1 ? split[1] : null;

        long total;
        List<EmbedBuilder> alertMessages;

        if(isPrivateChannel(context)) {
            total = null != tickerPair ?
                    alertsDao.countAlertsOfUserAndTickers(context.user.getIdLong(), ticker, ticker2) :
                    alertsDao.countAlertsOfUser(context.user.getIdLong());
            alertMessages = (null != tickerPair ?
                    alertsDao.getAlertsOfUserAndTickers(context.getServerId(), offset, MESSAGE_PAGE_SIZE + 1, ticker, ticker2) :
                    alertsDao.getAlertsOfUser(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1))
                    .stream().map(CommandAdapter::toMessage).collect(toList());
        } else {
            total = null != tickerPair ?
                    alertsDao.countAlertsOfServerAndUserAndTickers(context.getServerId(), ownerId, ticker, ticker2) :
                    alertsDao.countAlertsOfServerAndUser(context.getServerId(), ownerId);
            alertMessages = (null != tickerPair ?
                    alertsDao.getAlertsOfServerAndUserAndTickers(context.getServerId(), ownerId, offset, MESSAGE_PAGE_SIZE + 1, ticker, ticker2) :
                    alertsDao.getAlertsOfServerAndUser(context.user.getIdLong(), ownerId, offset, MESSAGE_PAGE_SIZE + 1))
                    .stream().map(CommandAdapter::toMessage).collect(toList());
        }

        return paginatedAlerts(alertMessages, offset, total,
                () -> "!owner <@" + ownerId + '>' +
                        (null != tickerPair ? ' ' + tickerPair : "") + ' ' + (offset + MESSAGE_PAGE_SIZE - 1),
                () -> "user <@" + ownerId + (null != tickerPair ? "> and " + tickerPair : ">"));
    }
}
