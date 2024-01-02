package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "ticker_pair", "the ticker or pair to show alerts on", true));

    public PairCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        LOGGER.debug("pair command");
        String ticker = command.args.getMandatoryString("ticker_pair");
        command.reply(pair(command, ticker));
    }
    private EmbedBuilder pair(@NotNull Command command, @NotNull String ticker) {

        String alerts = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(command))
                .filter(alert -> alert.getSlashPair().contains(ticker))
                .map(Alert::toString)
                .collect(Collectors.joining("\n"));

        String answer = alerts.isEmpty() ? "No alert found for " + ticker : alerts;

        return embedBuilder(NAME, Color.green, answer);
    }
}
