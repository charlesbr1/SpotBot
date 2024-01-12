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

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.ArgumentValidator.*;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";
    private static final int RESPONSE_TTL_SECONDS = 300;

    static final List<OptionData> options = List.of(
            new OptionData(USER, "user", "the owner of alerts to show", true),
            new OptionData(STRING, "ticker_pair", "a ticker or pair to filter on", false)
                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
            new OptionData(INTEGER, "offset", "an offset from where to start the search (results are limited to 1000 alerts)", false)
                    .setMinValue(0));

    public OwnerCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long ownerId = context.args.getMandatoryUserId("user");
        String tickerOrPair = null;
        Long offset = context.args.getLong("offset").orElse(null);
        if(null == offset) { // if the next arg can't be parsed as a long, may be it's a string
            tickerOrPair = context.args.getString("ticker_pair")
                    .map(ArgumentValidator::requireTickerPairLength)
                    .map(String::toUpperCase).orElse(null);
            offset = context.args.getLong("offset").orElse(0L);
        }
        var finalOffset = offset;
        var finalTickerOrPair = tickerOrPair;
        LOGGER.debug("owner command - user : {}, ticker_pair : {}, offset : {}", ownerId, finalTickerOrPair, finalOffset);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, owner(context, finalTickerOrPair, ownerId, requirePositive(finalOffset))));
    }

    private List<EmbedBuilder> owner(@NotNull CommandContext context, @Nullable String tickerOrPair, long ownerId, long offset) {
        if(null == context.member && context.user.getIdLong() != ownerId) {
            return List.of(embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel"));
        }
        return alertsDao.transactional(() -> {
            long total;
            List<EmbedBuilder> alertMessages;

            if (isPrivateChannel(context)) {
                total = null != tickerOrPair ?
                        alertsDao.countAlertsOfUserAndTickers(context.user.getIdLong(), tickerOrPair) :
                        alertsDao.countAlertsOfUser(context.user.getIdLong());
                alertMessages = (null != tickerOrPair ?
                        alertsDao.getAlertsOfUserAndTickers(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1, tickerOrPair) :
                        alertsDao.getAlertsOfUser(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1))
                        .stream().map(CommandAdapter::toMessage).collect(toList());
            } else {
                total = null != tickerOrPair ?
                        alertsDao.countAlertsOfServerAndUserAndTickers(context.serverId(), ownerId, tickerOrPair) :
                        alertsDao.countAlertsOfServerAndUser(context.serverId(), ownerId);
                alertMessages = (null != tickerOrPair ?
                        alertsDao.getAlertsOfServerAndUserAndTickers(context.serverId(), ownerId, offset, MESSAGE_PAGE_SIZE + 1, tickerOrPair) :
                        alertsDao.getAlertsOfServerAndUser(context.serverId(), ownerId, offset, MESSAGE_PAGE_SIZE + 1))
                        .stream().map(CommandAdapter::toMessage).collect(toList());
            }

            return paginatedAlerts(alertMessages, offset, total,
                    () -> "!owner <@" + ownerId + '>' +
                            (null != tickerOrPair ? ' ' + tickerOrPair : "") + ' ' + (offset + MESSAGE_PAGE_SIZE - 1),
                    () -> " for user <@" + ownerId + (null != tickerOrPair ? "> and " + tickerOrPair : ">"));
        });
    }
}
