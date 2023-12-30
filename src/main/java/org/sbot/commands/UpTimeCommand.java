package org.sbot.commands;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;

public final class UpTimeCommand extends CommandAdapter {

    public static final String NAME = "uptime";
    static final String DESCRIPTION = "returns the time since this bot is up";

    private static final Instant start = Instant.now();

    public UpTimeCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return Collections.emptyList();
    }

    @Override
    public void onEvent(@NotNull ArgumentReader argumentReader, @NotNull MessageReceivedEvent event) {
        LOGGER.debug("uptime command: {}", event.getMessage().getContentRaw());
        event.getChannel().sendMessageEmbeds(uptime()).queue();
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("uptime slash command: {}", event.getOptions());
        event.replyEmbeds(uptime()).queue();
    }

    private MessageEmbed uptime() {
        Duration upTime = Duration.between(start, Instant.now());
        String answer = SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "SpotBot is up since " +
                upTime.toDays() + (upTime.toDays() > 1 ? " days, " : " day, ") +
                (upTime.toHours() % 24) + ((upTime.toHours() % 24)  > 1 ? " hours, " : " hour, ") +
                (upTime.toMinutes() % 60) + ((upTime.toMinutes() % 60) > 1 ? " minutes, " : " minute, ") +
                (upTime.toSeconds() % 60) + ((upTime.toSeconds() % 60) > 1 ? " seconds" : " second");

        return embedBuilder(NAME, Color.green, answer).build();
    }
}
