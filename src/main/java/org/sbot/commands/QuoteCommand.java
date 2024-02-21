package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.TimeFrame;
import org.sbot.exchanges.Exchanges;
import org.sbot.utils.Dates;

import java.awt.*;
import java.util.List;

import static java.util.Comparator.comparing;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.entities.chart.Ticker.formatPrice;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.utils.ArgumentValidator.*;

public final class QuoteCommand extends CommandAdapter {

    private static final String NAME = "quote";
    static final String DESCRIPTION = "get the last quotation of a pair on the given exchange (1 minute time frame)";
    private static final int RESPONSE_TTL_SECONDS = 300;

    private static final SlashCommandData options =
        Commands.slash(NAME, DESCRIPTION).addOptions(
                option(STRING, "exchange", "the exchange, like binance", true)
                        .addChoices(SUPPORTED_EXCHANGES.stream().map(e -> new Command.Choice(e, e)).toList()),
                option(STRING, "pair", "the pair, like EUR/USDT", true)
                        .setMinLength(ALERT_MIN_PAIR_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH));


    public QuoteCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String exchange = requireSupportedExchange(context.args.getMandatoryString("exchange"));
        String pair = requirePairFormat(context.args.getMandatoryString("pair").toUpperCase());
        LOGGER.debug("quote command - exchange : {}, pair : {}", exchange, pair);
        context.noMoreArgs().reply(quote(exchange, pair.toUpperCase()), responseTtlSeconds);
    }

    private Message quote(@NotNull String exchange, @NotNull String pair) {
        return Message.of(embedBuilder(" ", Color.green, parseCandlestick(pair, Exchanges.get(exchange)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported exchange : " + exchange))
                .getCandlesticks(pair, TimeFrame.ONE_MINUTE, 1))));
    }

    @NotNull
    private static String parseCandlestick(@NotNull String pair, @NotNull List<Candlestick> candlestick) {
        String ticker2 = pair.substring(pair.indexOf('/') + 1);
        return candlestick.stream()
                .sorted(comparing(Candlestick::closeTime).reversed())
                .map(c ->
                "**[" + pair + "]**\n\n> " + formatPrice(c.close(), ticker2) +
                "\n\n" + Dates.formatDiscordRelative(c.closeTime()))
                .findFirst().orElse("No market data found for pair " + pair);
    }
}
