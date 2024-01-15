package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.chart.TimeFrame;
import org.sbot.commands.reader.CommandContext;
import org.sbot.exchanges.Exchanges;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.time.ZoneId.SHORT_IDS;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.chart.Ticker.getSymbol;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;

public final class QuoteCommand extends CommandAdapter {

    public static final String NAME = "quote";
    static final String DESCRIPTION = "get the last quotation of a pair on the given exchange (1 minute time frame)";
    private static final int RESPONSE_TTL_SECONDS = 300;

    private static final int TIME_ZONE_LENGTH = 3;

    static final SlashCommandData options =
        Commands.slash(NAME, DESCRIPTION).addOptions(
                option(STRING, "exchange", "the exchange, like binance", true)
                        .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Command.Choice(e, e)).collect(toList())),
                option(STRING, "pair", "the pair, like EUR/USDT", true)
                        .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                option(STRING, "timezone", "your current timezone, use utc list to see the available ones", false)
                        .setMinLength(TIME_ZONE_LENGTH).setMaxLength(TIME_ZONE_LENGTH));



    public QuoteCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        String timeZone = context.args.getString("timezone").orElse("");
        LOGGER.debug("quote command - exchange : {}, pair : {}, timezone : {}", exchange, pair, timeZone);
        context.noMoreArgs().reply(responseTtlSeconds, quote(exchange, pair.toUpperCase(), timeZone));
    }

    private EmbedBuilder quote(@NotNull String exchange, @NotNull String pair, @NotNull String timezone) {
        return embedBuilder(" ", Color.green, parseCandlestick(pair, timezone, Exchanges.get(exchange)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported exchange : " + exchange))
                .getCandlesticks(pair, TimeFrame.ONE_MINUTE, 1)));
    }

    @NotNull
    private static String parseCandlestick(@NotNull String pair, @NotNull String timeZone, @NotNull List<Candlestick> candlestick) {
        String zoneId = getZoneId(timeZone);
        String ticker2 = pair.substring(pair.indexOf('/') + 1);
        Function<ZonedDateTime, String> dateFormatter = null == zoneId ? Dates::formatUTC :
                d -> Dates.formatAtZone(d.withZoneSameInstant(ZoneId.of(zoneId)));
        return candlestick.stream()
                .sorted(comparing(Candlestick::closeTime).reversed())
                .map(c ->
                "**[" + pair + "]**\n\n> " +
                c.close().stripTrailingZeros().toPlainString() + ' ' + getSymbol(ticker2) +
                "\n\n" + dateFormatter.apply(c.closeTime()) + (null != zoneId ? " (" + zoneId + ')' : " (UTC)"))
                .findFirst().orElse("No market data found for pair " + pair);
    }

    private static String getZoneId(@NotNull String timeZone) {
        if(!timeZone.isBlank()) {
             return Optional.ofNullable(SHORT_IDS.get(timeZone.toUpperCase()))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid time zone : " + timeZone + "\nuse *utc list* to see the available ones"));
        }
        return null;
    }
}
