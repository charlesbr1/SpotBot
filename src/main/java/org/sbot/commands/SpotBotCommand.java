package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.discord.Discord;
import org.sbot.services.dao.AlertsDao;
import org.sbot.utils.Dates;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.alerts.Alert.DEFAULT_REPEAT;
import static org.sbot.alerts.Alert.DEFAULT_REPEAT_DELAY_HOURS;
import static org.sbot.discord.Discord.*;

public final class SpotBotCommand extends CommandAdapter {

    public static final String NAME = "spotbot";
    private static final String DESCRIPTION = "show the documentation, a description of each commands, or some examples of commands usage";
    private static final int RESPONSE_TTL_SECONDS = 600;

    private static final String CHOICE_DOC = "doc";
    private static final String CHOICE_COMMANDS = "commands";
    private static final String CHOICE_EXAMPLES = "examples";
    static final List<OptionData> options = List.of(
            new OptionData(STRING, "choice", "one of 'doc' or 'commands' or 'examples', default to doc if omitted", false)
                    .addChoices(List.of(new Choice(CHOICE_DOC, CHOICE_DOC),
                            new Choice(CHOICE_COMMANDS, CHOICE_COMMANDS),
                            new Choice(CHOICE_EXAMPLES, CHOICE_EXAMPLES))));

    private static final String DOC_FOOTER = "EXPLAIN THE range and trend alerts from picture";

    private static final String ALERTS_PICTURE_FILE = "range2.png";
    private static final String ALERTS_PICTURE_PATH = '/' + ALERTS_PICTURE_FILE;

    private record Command(String name, String description, List<OptionData> options) {}

    private static final List<Command> commands = List.of(
            new Command(NAME, DESCRIPTION, options),
            new Command(ListCommand.NAME, ListCommand.DESCRIPTION, ListCommand.options),
            new Command(OwnerCommand.NAME, OwnerCommand.DESCRIPTION, OwnerCommand.options),
            new Command(PairCommand.NAME, PairCommand.DESCRIPTION, PairCommand.options),
            new Command(RangeCommand.NAME, RangeCommand.DESCRIPTION, RangeCommand.options),
            new Command(TrendCommand.NAME, TrendCommand.DESCRIPTION, TrendCommand.options),
            new Command(RemainderCommand.NAME, RemainderCommand.DESCRIPTION, RemainderCommand.options),
            new Command(RepeatCommand.NAME, RepeatCommand.DESCRIPTION, RepeatCommand.options),
            new Command(RepeatDelayCommand.NAME, RepeatDelayCommand.DESCRIPTION, RepeatDelayCommand.options),
            new Command(MarginCommand.NAME, MarginCommand.DESCRIPTION, MarginCommand.options),
            new Command(MessageCommand.NAME, MessageCommand.DESCRIPTION, MessageCommand.options),
            new Command(DeleteCommand.NAME, DeleteCommand.DESCRIPTION, DeleteCommand.options),
            new Command(TimeZoneCommand.NAME, TimeZoneCommand.DESCRIPTION, TimeZoneCommand.options),
            new Command(UpTimeCommand.NAME, UpTimeCommand.DESCRIPTION, emptyList()));

    private static final String DOC_HEADER = """
            SpotBot is a discord utility for setting alerts when the price of an asset reach a box or cross a trend line, or just for a remainder in the future.
            
            An asset is a pair of two tickers, like ETH/USDT, ADA/BTC, EUR/USD, etc.
            
            A box is a range defined by two prices, with some optionally starting and ending dates (during which the alert is active), thus forming a box.

            A trend is a line defined by two points on a price chart, each having a price and a date or time as coordinates.
            
            A price can use a comma or a dot separator and is expected to be positive : 23.6 or 38,2 are ok.
            
            **A date should be provided as UTC** date and time. Discord can't provide your time zone so you have to think about it !

            The expected date time format is :``` {date-format}```
            For instance now it's {date-now} UTC

            > Use **range**, **trend**, or **remainder** commands to set new alerts, this bot will check every hours for price change then you'll get notified when your asset reach your price !
            
            **margin**

            Both ranges and trend alerts can have a *margin*, this is a value in currency of the watched asset, like 1000$ on the BTC/USD pair.

            The margin is added on both sides of a range or a trend, as to extend them, then a pre alert will notify you when the price reach this zone (if it ever happens).

            Initially a new alert has no margin set, it should be added using the **margin** command.

            Once triggered (either a margin pre alert or an alert) the alert's margin is reset, that is no more margin.
            
            **snooze**

            Range and margin alerts also have two parameters, *repeat* and *repeat-delay*, that can be set using the commands of their respective names.
            * **repeat** : the number of times a triggered alert will be re thrown. (default : {repeat})
            * **repeat-delay** : the time in hours to wait before the alert can be raised again. (default : {repeat-delay} hours)

            Once an alert has been raised, it decreases in number of *repeat* and becomes ignored during *repeat-delay* hours.
            If it's number of repeat reach 0, the alert is disabled and will be deleted one month later, this let time to enable it again, if never.
            
            **remainder**
            
            This third kind of alert let you receive a notification at a specified date in the future, with a message you can prepare in advance. This allows to you to set a remainder for events you don't want to miss.
            A remainder alert is always deleted after it has been raise, as there is no margin or repeat for them.

            
            For all theses alerts, including remainders, the accuracy of updates is hourly, so do not expect a notification in the micro second after the event occurred.
            
            The others commands let you do some searches about current alerts defined, as well as updating or deleting them.
            
            This bot works exclusively into the channel {channel}, you can also use it from your private channel. When an alert occurs, the owner is notified on the channel where he created it.
            
            **The alerts you set using your private channel remains confidential.**

            You may also want to join role {role} to get notified of each alert occurring on the channel {channel}.

            Now type **/** to use a command, or type it prefixed with char **!**
            
            For a description of each available commands use :
            ``` /spotbot commands```
            To get some example usages of these commands, use :
            ``` /spotbot examples```
            """;

    private static final String EXAMPLES_MESSAGE = """
            * *!list alerts*
            * *!owner @someone*
            * *!pair ETH/USDT*
            * *!range binance eth usdt 1800 1900 zone conso 1900 reached, see https://discord.com/channels...*
            * *!trend binance eth usdt 1800 10/03/2019-12:30 1900 03/11/2021-16:00 daily uptrend tested, see https://discord.com/channels...*
            * *!repeat 123 5*
            * *!repeat-delay 123 24*
            * *!margin 123 3*
            * *!delete 123*
            * *!remainder 10/03/2019-12:30 A message to receive at this date*
            """;

    private static final FileUpload alertsPicture = FileUpload.fromData(requireNonNull(SpotBotCommand.class
            .getResourceAsStream(ALERTS_PICTURE_PATH)), ALERTS_PICTURE_FILE);

    public SpotBotCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        String choice = context.args.getString("choice").orElse(CHOICE_DOC);
        LOGGER.debug("spotBot command - choice : {}", choice);
        context.reply(responseTtlSeconds, spotBot(choice, Optional.ofNullable(context.member).map(Member::getGuild).orElse(null)),
                List.of(CHOICE_DOC.equals(choice) ? message -> message.addFiles(alertsPicture) : m -> {}));
    }

    private List<EmbedBuilder> spotBot(@NotNull String choice, @Nullable Guild guild) {
        return switch (choice) {
            case CHOICE_DOC -> doc(guild);
            case CHOICE_COMMANDS -> commands();
            case CHOICE_EXAMPLES -> examples();
            default -> throw new IllegalArgumentException("Invalid argument : " + choice);
        };
    }
    private static List<EmbedBuilder> doc(@Nullable Guild guild) {
        EmbedBuilder builder = embedBuilder(null, Color.green, formattedHeader(guild));
        builder.addBlankField(false);
        builder.setImage("attachment://" + ALERTS_PICTURE_FILE);
        builder.setFooter(DOC_FOOTER);
        return List.of(builder);
    }

    private static String formattedHeader(@Nullable Guild guild) {
        String channel = Optional.ofNullable(guild).flatMap(Discord::getSpotBotChannel)
                .map(Channel::getAsMention).orElse("**#" + DISCORD_BOT_CHANNEL + "** of your discord server");
        String role = Optional.ofNullable(guild).flatMap(Discord::spotBotRole)
                .map(Role::getAsMention).orElse("**@" + DISCORD_BOT_ROLE + "**");
        return DOC_HEADER.replace("{date-format}", Dates.DATE_TIME_FORMAT)
                .replace("{date-now}", Dates.formatUTC(Instant.now().atZone(ZoneOffset.UTC)))
                .replace("{repeat}", ""+DEFAULT_REPEAT)
                .replace("{repeat-delay}", ""+DEFAULT_REPEAT_DELAY_HOURS)
                .replace("{channel}", channel).replace("{role}", role);
    }

    private static String optionsDescription(List<OptionData> options) {
        return options.isEmpty() ? "" :
                (options.size() > 1 ? "\n\nparameters :\n\n" : "\n\nparameter :\n\n") +
                options.stream()
                        .map(option -> "- **" + option.getName() + "** *" + option.getType().toString().toLowerCase() + "* " +
                                (option.isRequired() ? "" : "*(optional)* ") + option.getDescription())
                        .collect(joining("\n"));
    }

    private static List<EmbedBuilder> commands() {
        // split the list because it exceeds max discord message length
        return Stream.of(commands.subList(0, commands.size() / 2), commands.subList(commands.size()/2, commands.size()))
                .map(cmdList -> embedBuilder(null, Color.green, cmdList.stream()
                        .map(cmd -> "** " + cmd.name + "**\n\n" + SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + cmd.description + optionsDescription(cmd.options))
                        .collect(joining("\n\n\n")))).toList();
    }

    private static List<EmbedBuilder> examples() {
        return List.of(embedBuilder("examples", Color.green, EXAMPLES_MESSAGE));
    }
}
