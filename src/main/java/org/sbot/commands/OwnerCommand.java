package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
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
    public void onCommand(@NotNull Command command) {
        long ownerId = command.args.getMandatoryUserId("owner");
        String tickerPair = null;
        Long offset = command.args.getLong("offset").orElse(null);
        if(null == offset) { // if next arg can't be parsed as a long, it's may be a string
            tickerPair = command.args.getString("ticker_pair").orElse(null);
            offset = command.args.getLong("offset").orElse(0L);
        }
        LOGGER.debug("owner command - owner : {}, ticker_pair : {}, offset : {}", ownerId, tickerPair, offset);
        command.reply(owner(command, tickerPair, ownerId, requirePositive(offset)));
    }

    private EmbedBuilder owner(@NotNull Command command, @Nullable String tickerPair, long ownerId, long offset) {
        if(null == command.member && command.user.getIdLong() != ownerId) {
            return embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel");
        }

        Predicate<Alert> ownerAndPair = null != tickerPair ?
                alert -> alert.userId == ownerId && alert.getSlashPair().contains(tickerPair) :
                alert -> alert.userId == ownerId;

        String alerts = alertStorage.getAlerts().skip(offset) //TODO skip in dao call
                .filter(serverOrPrivateFilter(command))
                .filter(ownerAndPair).map(Alert::toString)
                .collect(Collectors.joining("\n"));

        String answer = alerts.isEmpty() ?
                "No alert found for user <@" + ownerId + '>' + (null != tickerPair ? " and " + tickerPair : "") : alerts;

        return embedBuilder(NAME, Color.green, answer);
    }
}
