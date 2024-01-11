package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;

import static java.util.Collections.emptyList;
import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;

public final class UpTimeCommand extends CommandAdapter {

    public static final String NAME = "uptime";
    static final String DESCRIPTION = "returns the time since this bot is up";
    private static final int RESPONSE_TTL_SECONDS = 10;

    private static final Instant start = Instant.now();

    public UpTimeCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, emptyList());
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        LOGGER.debug("uptime command");
        context.reply(RESPONSE_TTL_SECONDS, uptime());
    }
    private EmbedBuilder uptime() {
        Duration upTime = Duration.between(start, Instant.now());
        String answer = SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "SpotBot is up since " +
                upTime.toDays() + (upTime.toDays() > 1 ? " days, " : " day, ") +
                (upTime.toHours() % 24) + ((upTime.toHours() % 24)  > 1 ? " hours, " : " hour, ") +
                (upTime.toMinutes() % 60) + ((upTime.toMinutes() % 60) > 1 ? " minutes, " : " minute, ") +
                (upTime.toSeconds() % 60) + ((upTime.toSeconds() % 60) > 1 ? " seconds" : " second");

        return embedBuilder(NAME, Color.green, answer);
    }
}
