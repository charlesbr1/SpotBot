package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.ArgumentValidator;

import java.awt.*;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.discord.Discord.MESSAGE_PAGE_SIZE;
import static org.sbot.discord.Discord.MULTI_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;

public final class ListCommand extends CommandAdapter {

    public static final String NAME = "list";
    static final String DESCRIPTION = "list the supported exchanges, or pairs, or the alerts currently set";
    private static final int RESPONSE_TTL_SECONDS = 300;

    private static final String CHOICE_ALERTS = "alerts";
    private static final String CHOICE_EXCHANGES = "exchanges";
    private static final String CHOICE_PAIRS = "pairs";

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "choice", "the data to display, one of 'exchanges' or 'pairs' or 'alerts', default to alerts if omitted", false)
                            .addChoice(CHOICE_ALERTS, CHOICE_ALERTS)
                            .addChoice(CHOICE_EXCHANGES, CHOICE_EXCHANGES)
                            .addChoice(CHOICE_PAIRS, CHOICE_PAIRS),
                    option(INTEGER, "offset", "an offset from where to start the search (results are limited to 1000 alerts)", false)
                            .setMinValue(0));

    public ListCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Long offset = context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(null);
        String choice = context.args.getString("choice").orElse(CHOICE_ALERTS);

        if(null == offset) { // if the previous arg was not be parsed as a long, may be it was a string first
            offset = context.args.getLong("offset").map(ArgumentValidator::requirePositive).orElse(0L);
        }
        long finalOffset = offset;
        LOGGER.debug("list command - choice : {}, offset : {}", choice, offset);
        alertsDao.transactional(() -> context.noMoreArgs().reply(responseTtlSeconds, list(context, choice, finalOffset)));
    }

    private List<EmbedBuilder> list(@NotNull CommandContext context, @NotNull String choice, long offset) {
        return switch (choice) {
            case CHOICE_ALERTS -> alerts(context, offset);
            case CHOICE_EXCHANGES -> exchanges();
            case CHOICE_PAIRS -> pairs();
            default -> throw new IllegalArgumentException("Invalid argument : " + choice);
        };
    }

    private List<EmbedBuilder> alerts(@NotNull CommandContext context, long offset) {
        return alertsDao.transactional(() -> {
            long total = isPrivateChannel(context) ?
                    alertsDao.countAlertsOfUser(context.user.getIdLong()) :
                    alertsDao.countAlertsOfServer(context.serverId());
            List<EmbedBuilder> alertMessages = (isPrivateChannel(context) ?
                    alertsDao.getAlertsOfUser(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1) :
                    alertsDao.getAlertsOfServer(context.serverId(), offset, MESSAGE_PAGE_SIZE + 1))
                    .stream().map(alert -> toMessage(context, alert)).collect(toList());

            return paginatedAlerts(alertMessages, offset, total,
                    () -> "list alerts " + (offset + MESSAGE_PAGE_SIZE - 1),
                    () -> "");
        });
    }

    private List<EmbedBuilder> exchanges() {
        return List.of(embedBuilder(CHOICE_EXCHANGES, Color.green,
                MULTI_LINE_BLOCK_QUOTE_MARKDOWN + "* " + String.join("\n* ", SUPPORTED_EXCHANGES)));
    }

    private List<EmbedBuilder> pairs() {//TODO Exchanges.get(exchange).getAvailablePairs()
        return List.of(embedBuilder(CHOICE_PAIRS, Color.green, "TODO"));
    }
}