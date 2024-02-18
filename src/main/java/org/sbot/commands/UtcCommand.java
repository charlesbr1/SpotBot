package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

import static java.time.ZoneId.SHORT_IDS;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.utils.Dates.DATE_TIME_FORMAT;
import static org.sbot.utils.Dates.NOW_ARGUMENT;

public final class UtcCommand extends CommandAdapter {

    private static final String NAME = "utc";
    static final String DESCRIPTION = "convert a date time into the utc time zone, helping with commands that expect a date in UTC";
    private static final int RESPONSE_TTL_SECONDS = 180;


    private static final String CHOICE_NOW = NOW_ARGUMENT;
    private static final String CHOICE_LIST = "list";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "zone", "'now' to get the current utc time, 'list' to get available time zones, or the time zone of your date", true)
                            .setMinLength(1),
                    option(STRING, "date", "a date to convert in UTC, expected format : " + DATE_TIME_FORMAT, false)
                            .setMinLength(DATE_TIME_FORMAT.length()));

    public UtcCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String choice = context.args.getMandatoryString("zone");
        LocalDateTime date = context.args.getLocalDateTime("date").orElse(null);
        LOGGER.debug("utc command - choice : {}, date : {}", choice, date);
        context.noMoreArgs().reply(utc(context, choice, date), responseTtlSeconds);
    }

    private List<Message> utc(@NotNull CommandContext context, @NotNull String choice, @Nullable LocalDateTime date) {
        return switch (choice) {
            case CHOICE_NOW -> List.of(now(context.clock()));
            case CHOICE_LIST -> list();
            default -> List.of(toUTC(choice, date));
        };
    }

    private Message now(@NotNull Clock clock) {
        return Message.of(embedBuilder(" ", Color.green, "Current UTC date time :\n\n> " + Dates.format(Dates.nowUtc(clock))));
    }

    private List<Message> list() {
        var zoneIds = ZoneId.getAvailableZoneIds();
        zoneIds.addAll(SHORT_IDS.keySet());
        var zones = zoneIds.stream().sorted().toList();
        Function<List<String>, String> toString = list -> String.join(", ", list);
        int splitIndex = zones.size() / 3;

        return List.of(Message.of(embedBuilder(" ", Color.green, "Available time zones :\n\n>>> " +
                "+HH:mm, -HH:mm,\n" + toString.apply(zones.subList(0, splitIndex)))),
                Message.of(embedBuilder(" ", Color.green, ">>> " + toString.apply(zones.subList(splitIndex, 2 * splitIndex)))),
                Message.of(embedBuilder(" ", Color.green, ">>> " + toString.apply(zones.subList(2 * splitIndex, zones.size())))));
    }

    private Message toUTC(@NotNull String timeZone, @Nullable LocalDateTime date) {
        if(null == date) {
            throw new IllegalArgumentException("Missing date time field");
        }
        ZonedDateTime zonedDateTime = date.atZone(ZoneId.of(timeZone, SHORT_IDS));
        return Message.of(embedBuilder(" ", Color.green, Dates.formatUTC(zonedDateTime)));
    }
}