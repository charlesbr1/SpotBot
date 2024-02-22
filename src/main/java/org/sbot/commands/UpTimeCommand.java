package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.utils.Dates;

import java.awt.Color;
import java.time.ZonedDateTime;

public final class UpTimeCommand extends CommandAdapter {

    private static final String NAME = "uptime";
    static final String DESCRIPTION = "returns the time since this bot is up (no slash command for this one)";
    private static final int RESPONSE_TTL_SECONDS = 10;

    private static final SlashCommandData options = Commands.slash(NAME, DESCRIPTION);
    private static final ZonedDateTime start = ZonedDateTime.now();

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
        context.noMoreArgs().reply(uptime(), responseTtlSeconds);
    }
    private Message uptime() {
        String answer = MarkdownUtil.quote("SpotBot started " + Dates.formatDiscordRelative(start));
        return Message.of(embedBuilder(NAME, Color.green, answer));
    }
}
