package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.getEffectiveName;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class PairCommand extends CommandAdapter {

    public static final String NAME = "pair";
    static final String DESCRIPTION = "show the alerts defined on the given ticker or pair";

    static final List<OptionData> options = List.of(
            new OptionData(STRING, "ticker_pair", "the ticker or pair to show alerts on", true),
            new OptionData(INTEGER, "offset", "an optional offset to start the search (results are limited to 1000 alerts)", false)
                    .setMinValue(0));

    public PairCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String tickerPair = context.args.getMandatoryString("ticker_pair");
        long offset = requirePositive(context.args.getLong("offset").orElse(0L));
        LOGGER.debug("pair command - ticker_pair : {}, offset : {}", tickerPair, offset);
        context.reply(pair(context, tickerPair.toUpperCase(), offset));
    }
    private List<EmbedBuilder> pair(@NotNull CommandContext context, @NotNull String tickerPair, long offset) {

        long total = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(context))
                .filter(alert -> alert.getSlashPair().contains(tickerPair))
                .count(); //TODO

        List<EmbedBuilder> alerts = alertStorage.getAlerts()
                .filter(serverOrPrivateFilter(context))
                .filter(alert -> alert.getSlashPair().contains(tickerPair))
                .skip(offset) //TODO skip in dao call
                .limit(MESSAGE_PAGE_SIZE + 1)
                .map(alert -> toMessage(alert, getEffectiveName(context.channel.getJDA(), alert.userId).orElse("unknown")))
                .collect(toList());

        return adaptSize(alerts, offset, total,
                () -> "!pair " + tickerPair + ' ' + (offset + MESSAGE_PAGE_SIZE - 1),
                () -> "pair " + tickerPair);
    }
}
