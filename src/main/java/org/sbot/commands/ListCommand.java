package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.awt.*;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.discord.Discord.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class ListCommand extends CommandAdapter {

    public static final String NAME = "list";
    static final String DESCRIPTION = "list the supported exchanges, or pairs, or the alerts currently set";

    private static final String ALERTS = "alerts";
    private static final String EXCHANGES = "exchanges";
    private static final String PAIRS = "pairs";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "value", "the data to list, one of 'exchanges', 'pairs', 'alerts'", true)
                    .addChoice(ALERTS, ALERTS)
                    .addChoice(EXCHANGES, EXCHANGES)
                    .addChoice(PAIRS, PAIRS),
            new OptionData(INTEGER, "offset", "an optional offset to start the alerts search (results are limited to 1000 alerts)", false)
                    .setMinValue(0));

    public ListCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String value = context.args.getMandatoryString("value");
        long offset = requirePositive(context.args.getLong("offset").orElse(0L));
        LOGGER.debug("list command - value : {}, offset : {}", value, offset);
        alertsDao.transactional(() -> context.reply(list(context, value, offset)));
    }

    private List<EmbedBuilder> list(@NotNull CommandContext context, @NotNull String value, long offset) {
        return switch (value) {
            case ALERTS -> alerts(context, offset);
            case EXCHANGES -> exchanges();
            case PAIRS -> pairs();
            default -> throw new IllegalArgumentException("Invalid argument : " + value);
        };
    }

    private List<EmbedBuilder> alerts(@NotNull CommandContext context, long offset) {
        long total = isPrivateChannel(context) ?
                alertsDao.countAlertsOfUser(context.user.getIdLong()) :
                alertsDao.countAlertsOfServer(context.getServerId());
        List<EmbedBuilder> alertMessages = (PRIVATE_ALERT != context.getServerId() ?
                alertsDao.getAlertsOfUser(context.user.getIdLong(), offset, MESSAGE_PAGE_SIZE + 1) :
                alertsDao.getAlertsOfServer(context.getServerId(), offset, MESSAGE_PAGE_SIZE + 1))
                .stream().map(CommandAdapter::toMessage).collect(toList());

        return paginatedAlerts(alertMessages, offset, total,
                () -> "!list alerts " + (offset + MESSAGE_PAGE_SIZE - 1),
                () -> "list alerts");
    }

    private List<EmbedBuilder> exchanges() {
        return List.of(embedBuilder(EXCHANGES, Color.green,
                MULTI_LINE_BLOCK_QUOTE_MARKDOWN + "* " + String.join("\n* ", SUPPORTED_EXCHANGES)));
    }

    private List<EmbedBuilder> pairs() {
        return List.of(embedBuilder(PAIRS, Color.green, "TODO"));
    }
}