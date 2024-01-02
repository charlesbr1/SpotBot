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

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static net.dv8tion.jda.api.interactions.commands.OptionType.USER;

public final class OwnerCommand extends CommandAdapter {

    public static final String NAME = "owner";
    static final String DESCRIPTION = "show the alerts defined by the given user on a ticker or a pair";

    static final List<OptionData> options = List.of(
            new OptionData(USER, "owner", "the owner of alerts to show", true),
            new OptionData(STRING, "ticker_pair", "an optional ticker or pair to filter on", false));

    public OwnerCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        LOGGER.debug("owner command");
        long ownerId = command.args.getMandatoryUserId("owner");
        String pair = command.args.getString("ticker_pair").orElse(null);
        command.reply(owner(command, ownerId, pair));
    }

    private EmbedBuilder owner(@NotNull Command command, long ownerId, @Nullable String pair) {
        if(null == command.member && command.user.getIdLong() != ownerId) {
            return embedBuilder(NAME, Color.red, "You are not allowed to see alerts of members in a private channel");
        }

        Predicate<Alert> ownerAndPair = null != pair ?
                alert -> alert.userId == ownerId && alert.getSlashPair().contains(pair) :
                alert -> alert.userId == ownerId;

        String alerts = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(command))
                .filter(ownerAndPair).map(Alert::toString)
                .collect(Collectors.joining("\n"));

        String answer = alerts.isEmpty() ?
                "No alert found for user <@" + ownerId + '>' + (null != pair ? " and " + pair : "") : alerts;

        return embedBuilder(NAME, Color.green, answer);
    }
}
