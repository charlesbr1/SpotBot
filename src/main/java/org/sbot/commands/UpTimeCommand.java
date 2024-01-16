package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;

import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;

public final class UpTimeCommand extends CommandAdapter {

    private static final String NAME = "uptime";
    static final String DESCRIPTION = "returns the time since this bot is up (no slash command for this one)";
    private static final int RESPONSE_TTL_SECONDS = 10;

    static final SlashCommandData options = Commands.slash(NAME, DESCRIPTION);
    private static final Instant start = Instant.now();

    public UpTimeCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public boolean isSlashCommand() {
        return false; // only a text command
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        LOGGER.debug("uptime command");
        context.noMoreArgs().reply(responseTtlSeconds, uptime());
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
