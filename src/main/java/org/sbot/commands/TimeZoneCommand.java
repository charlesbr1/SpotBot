package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.time.ZoneId.SHORT_IDS;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;

public final class TimeZoneCommand extends CommandAdapter {

    public static final String NAME = "timezone";
    static final String DESCRIPTION = "convert a date time into the utc time zone, this can help for commands that expect a date in UTC";


    private static final String CHOICE_NOW = "now";
    private static final String CHOICE_LIST = "list";
    static final List<OptionData> options = List.of(
            new OptionData(STRING, "zone", "'now' to get the current utc time, 'list' to get available time zones, or the time zone of your date", true)
                    .setMaxLength(4),
            new OptionData(STRING, "date", "a date to convert in UTC, expected format : " + Dates.DATE_TIME_FORMAT, false));

    public TimeZoneCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String choice = context.args.getMandatoryString("choice");
        String date = context.args.getString("date").orElse(null);
        LOGGER.debug("timezone command - choice : {}, date : {}", choice, date);
        context.reply(timezone(choice, date));
    }

    private EmbedBuilder timezone(@NotNull String choice, @Nullable String date) {
        return switch (choice) {
            case CHOICE_NOW -> now();
            case CHOICE_LIST -> list();
            default -> toUTC(choice, date);
        };
    }

    private EmbedBuilder now() {
        return embedBuilder(NAME, Color.green, "Current UTC time :\n\n> " + Dates.formatUTC(ZonedDateTime.now()));
    }

    private EmbedBuilder list() {
        return embedBuilder(NAME, Color.green, "Available time zones :\n\n>>> " + SHORT_IDS.entrySet().stream()
                .map(entry -> entry.getKey() + " : " + entry.getValue())
                .collect(joining("\n")));
    }

    private EmbedBuilder toUTC(@NotNull String timeZone, @Nullable String date) {
        if(!SHORT_IDS.containsKey(timeZone)) {
            throw new IllegalArgumentException("Invalid time zone : " + timeZone + "\nuse *!utc list* to see the available ones");
        }
        if(null == date) {
            throw new IllegalArgumentException("Missing date field");
        }
        ZonedDateTime utcDate = LocalDateTime.parse(date, DATE_TIME_FORMATTER).atZone(ZoneId.of(SHORT_IDS.get(timeZone)));
        return embedBuilder(NAME, Color.green, Dates.formatUTC(utcDate));
    }
}