package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class QuoteCommand extends CommandAdapter {

    public static final String NAME = "quote";
    static final String DESCRIPTION = "get a quotation for given exchange pair and time frame";
    private static final int RESPONSE_TTL_SECONDS = 30;
//TODO
static final SlashCommandData options =
        Commands.slash(NAME, DESCRIPTION).addOptions(
                option(STRING, "pair", "a new margin for the alert, in ticker2 unit  (like USD for pair BTC/USD), 0 to disable", true));


    public QuoteCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }
//TODO
    @Override
    public void onCommand(@NotNull CommandContext context) {
        String pair = context.args.getMandatoryString("pair");
        LOGGER.debug("quote command - pair : {}", pair);
        context.reply(responseTtlSeconds, quote(context.noMoreArgs(), pair));
    }

    private EmbedBuilder quote(@NotNull CommandContext context, @NotNull String pair) {
        return null;
    }
}
