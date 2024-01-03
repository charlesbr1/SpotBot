package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";

    static final List<OptionData> options = List.of(
            new OptionData(USER, "owner", "the owner of alerts to show", true),
            new OptionData(STRING, "ticker_pair", "an optional ticker or pair to filter on", false),
            new OptionData(INTEGER, "offset", "an optional offset to start the search (results are limited to 1000 alerts)", false)
                    .setMinValue(0));

    public OwnerCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long ownerId = context.args.getMandatoryUserId("owner");
        String tickerPair = null;
        Long offset = context.args.getLong("offset").orElse(null);
        if(null == offset) { // if next arg can't be parsed as a long, it's may be a string
            tickerPair = context.args.getString("ticker_pair").map(String::toUpperCase).orElse(null);
            offset = context.args.getLong("offset").orElse(0L);
        }
        LOGGER.debug("owner command - owner : {}, ticker_pair : {}, offset : {}", ownerId, tickerPair, offset);
        context.reply(owner(context, tickerPair, ownerId, requirePositive(offset)));
    }

    private List<EmbedBuilder> owner(@NotNull CommandContext context, @Nullable String tickerPair, long ownerId, long offset) {
        if(null == context.member && context.user.getIdLong() != ownerId) {
            return List.of(embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel"));
        }

        Predicate<Alert> ownerAndPair = null != tickerPair ?
                alert -> alert.userId == ownerId && alert.getSlashPair().contains(tickerPair) :
                alert -> alert.userId == ownerId;

        long total = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(context))
                .filter(ownerAndPair).count(); //TODO

        List<EmbedBuilder> alerts = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(context))
                .filter(ownerAndPair)
                .skip(offset) //TODO skip in dao call
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(CommandAdapter::toMessage)
                .collect(toList());

        return paginatedAlerts(alerts, offset, total,
                () -> "!owner <@" + ownerId + '>' +
                        (null != tickerPair ? ' ' + tickerPair : "") + ' ' + (offset + MESSAGE_PAGE_SIZE - 1),
                () -> "user <@" + ownerId + (null != tickerPair ? "> and " + tickerPair : ">"));
    }
}
