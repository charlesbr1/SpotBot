package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.utils.Dates;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.time.ZoneId.SHORT_IDS;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.utils.Dates.DATE_TIME_FORMAT;
import static org.sbot.utils.Dates.NOW_ARGUMENT;

public final class TimezoneCommand extends CommandAdapter {

    private static final String NAME = "timezone";
    static final String DESCRIPTION = "list available timezones or convert a date time to utc, helping with commands that expect a date";
    private static final int RESPONSE_TTL_SECONDS = 180;


    private static final String CHOICE_NOW = NOW_ARGUMENT;
    private static final String CHOICE_LIST = "list";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, "zone", "'now' to get the current utc time, 'list' to get available time zones, or the time zone of your date", true)
                            .setMinLength(1),
                    option(STRING, "date", "a date to convert in UTC, expected format : " + DATE_TIME_FORMAT, false)
                            .setMinLength(DATE_TIME_FORMAT.length()));

    public TimezoneCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String choice = context.args.getMandatoryString("zone");
        LocalDateTime date = context.args.getLocalDateTime(context.locale, "date").orElse(null);
        LOGGER.debug("timezone command - choice : {}, date : {}", choice, date);
        context.noMoreArgs().reply(timezone(context, choice, date), responseTtlSeconds);
    }

    private List<Message> timezone(@NotNull CommandContext context, @NotNull String choice, @Nullable LocalDateTime date) {
        return switch (choice) {
            case CHOICE_NOW -> List.of(now(context));
            case CHOICE_LIST -> list();
            default -> List.of(toUTC(context, choice, date));
        };
    }

    private Message now(@NotNull CommandContext context) {
        return Message.of(embedBuilder(" ", Color.green, "Current UTC date time :\n\n> " + Dates.formatUTC(context.locale, Dates.nowUtc(context.clock()))));
    }

    private List<Message> list() {
        var zoneIds = ZoneId.getAvailableZoneIds();
        zoneIds.addAll(SHORT_IDS.keySet());
        var zones = zoneIds.stream().sorted().toList();
        int splitIndex = zones.size() / 3;

        return List.of(Message.of(embedBuilder(" ", Color.green, "Available time zones :\n\n>>> " +
                "by offset : +HH:mm, -HH:mm,\n" + String.join(", ", zones.subList(0, splitIndex)))),
                Message.of(embedBuilder(" ", Color.green, ">>> " + String.join(", ", zones.subList(splitIndex, 2 * splitIndex)))),
                Message.of(embedBuilder(" ", Color.green, ">>> " + String.join(", ", zones.subList(2 * splitIndex, zones.size())))));
    }

    private Message toUTC(@NotNull CommandContext context, @NotNull String timeZone, @Nullable LocalDateTime date) {
        if(null == date) {
            throw new IllegalArgumentException("Missing date time field");
        }
        ZonedDateTime zonedDateTime = date.atZone(ZoneId.of(timeZone, SHORT_IDS));
        return Message.of(embedBuilder(" ", Color.green, Dates.formatUTC(context.locale, zonedDateTime)));
    }
}