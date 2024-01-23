package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.time.ZoneId.SHORT_IDS;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public final class UtcCommand extends CommandAdapter {

    private static final String NAME = "utc";
    static final String DESCRIPTION = "convert a date time into the utc time zone, helping with commands that expect a date in UTC";
    private static final int RESPONSE_TTL_SECONDS = 30;


    private static final String CHOICE_NOW = "now";
    private static final String CHOICE_LIST = "list";

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "zone", "'now' to get the current utc time, 'list' to get available time zones, or the time zone of your date", true)
                            .setMaxLength(4),
                    option(STRING, "date", "a date to convert in UTC, expected format : " + Dates.DATE_TIME_FORMAT, false));

    public UtcCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String choice = context.args.getMandatoryString("zone");
        LocalDateTime date = context.args.getLocalDateTime("date").orElse(null);
        LOGGER.debug("utc command - choice : {}, date : {}", choice, date);
        context.noMoreArgs().reply(responseTtlSeconds, utc(choice, date));
    }

    private EmbedBuilder utc(@NotNull String choice, @Nullable LocalDateTime date) {
        return switch (choice) {
            case CHOICE_NOW -> now();
            case CHOICE_LIST -> list();
            default -> toUTC(choice.toUpperCase(), date);
        };
    }

    private EmbedBuilder now() {
        return embedBuilder(" ", Color.green, "Current UTC date time :\n\n> " + Dates.format(Dates.nowUtc()));
    }

    private EmbedBuilder list() {
        return embedBuilder(" ", Color.green, "Available time zones :\n\n>>> " + SHORT_IDS.entrySet().stream()
                .map(entry -> entry.getKey() + " : " + entry.getValue())
                .collect(joining("\n")));
    }

    private EmbedBuilder toUTC(@NotNull String timeZone, @Nullable LocalDateTime date) {
        if(!SHORT_IDS.containsKey(timeZone)) {
            throw new IllegalArgumentException("Invalid time zone : " + timeZone + "\nuse *utc list* to see the available ones");
        }
        if(null == date) {
            throw new IllegalArgumentException("Missing date time field");
        }
        ZonedDateTime zonedDateTime = date.atZone(ZoneId.of(SHORT_IDS.get(timeZone)));
        return embedBuilder(" ", Color.green, Dates.formatUTC(zonedDateTime));
    }
}