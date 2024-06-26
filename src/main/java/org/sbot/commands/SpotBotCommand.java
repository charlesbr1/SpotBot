package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.Message.File;
import org.sbot.services.discord.Discord;
import org.sbot.utils.Dates;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.commands.Commands.SPOTBOT_COMMANDS;
import static org.sbot.entities.alerts.Alert.DEFAULT_REPEAT;
import static org.sbot.entities.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.services.AlertsWatcher.DONE_ALERTS_DELAY_WEEKS;
import static org.sbot.services.discord.CommandListener.optionsDescription;
import static org.sbot.utils.PartitionSpliterator.split;

public final class SpotBotCommand extends CommandAdapter {

    static final String NAME = "spotbot";
    private static final String DESCRIPTION = "show the general documentation or a description of each commands";
    private static final int RESPONSE_TTL_SECONDS = 600;

    static final String CHOICE_DOC = "doc";
    static final String CHOICE_COMMANDS = "commands";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, SELECTION_ARGUMENT, "which help to show : 'doc' or 'commands', default to 'doc' if omitted", false)
                            .addChoices(List.of(new Choice(CHOICE_DOC, CHOICE_DOC),
                                    new Choice(CHOICE_COMMANDS, CHOICE_COMMANDS))));

    record Arguments(String selection) {}

    private static final String DOC_FOOTER = "EXPLAIN THE range and trend alerts from picture";

    private static final String ALERTS_PICTURE_FILE = "range2.png";
    private static final String ALERTS_PICTURE_PATH = '/' + ALERTS_PICTURE_FILE;

    private static final String DOC_CONTENT = """
            SpotBot is a discord utility for setting alerts when the price of an asset reach a box or cross a trend line, or just for a remainder in the future.
            
            An asset is a pair of two tickers, like ETH/USDT, ADA/BTC, EUR/USD, etc.
            
            **range alert**

            A range alert is defined by two prices, with some optionally starting and ending dates (during which the alert is active), thus forming a box. It can be raise when the price enter this box.

            **trend alert**

            A trend is a line defined by two points on a price chart, each having a price and a date as coordinates. An alert can be raise when the price cross this line.
            
            **remainder**
            
            This third kind of alert let you receive a notification at a specified date in the future, with a message you prepare in advance. This allows to you to set a remainder for events you don't want to miss.


            > Use **range**, **trend**, or **remainder** commands to set new alerts, this bot will check every {check-period} minutes for a price change then you'll get notified when your asset reach your price !
            
            
            A price can use a comma or a dot separator and must be positive : 23.6 or 38,2 are ok.
            
            **dates**

            Any date time should be provided as '{timezone}' timezone unless you set your default timezone. Discord do not provide your actual timezone so you have to think about it !

            The expected date time format is :``` {date-format}```
            * you can optionally specify a timezone
            * a range alert accepts 'null' as a date time, as its dates are optionals
            * you can also use 'now' shortcut to get the actual date time, with + or - hours : now+1.5 means in 1h30
            * using command line and not slash command, you should use a dash '-' instead of a space between the date and time or zone : {cmd-line-date-format}
            
            For instance now it's {date-now} {timezone}
            and now+2 is {date-now+2} {timezone}
            
            List available timezone and locales using **list** command, then set your default timezone and locale (for day months order) using **update**.

            **margin**

            Both ranges and trend alerts can have a *margin*, its a value in currency of the watched asset, like 1000$ on the BTC/USD pair.

            The margin is added on both sides of a range or a trend, as to extend them, then a pre alert will notify you when the price reach this zone (if it ever happens).

            Initially a new alert has no margin set, it may be set using the **update** command.

            Once raised (either a margin pre alert or an alert) the alert's margin is disabled.
            
            **snooze**

            Range and margin alerts also have two parameters, *repeat* and *snooze*, they can be set using the commands of their respective names.
            * **repeat** : the number of times an alert will be raise (default : {repeat})
            * **snooze** : the time in hours to wait before the alert can be raise again (default : {snooze} hours)

            Once an alert was raised, it decreases in number of *repeat* and becomes ignored during *snooze* hours.
            If there is no more repeat, the alert is disabled and will be deleted {done-delay-weeks} later, this let you time to enable it again, if needed.

            
            For all theses alerts the accuracy of updates is {check-period} minutes, so do not expect a notification in the second after the event occurred.
            
            The others commands let you do some searches about current alerts defined, as well as updating or deleting them.
            
            You can also use this bot on your private channel and be notified there.
            
            **The alerts you set on your private channel remains confidential.**

            On a guild, this bot works exclusively into the channel {channel}, you may want to join role {role} to get notified of each alert occurring.
            Administrators and members having role {admin-role} can update, migrate or delete member alerts, you'll get notified if this happens.
            """;

    static final String END_DOC_CONTENT = """
    
    Now type **/** to use a command, or type it prefixed with {spotBot}
    
    For a description of each available commands :
    ``` /spotbot commands```
    """;

    private static final byte[] alertsPicture;

    static {
        try(var file = requireNonNull(SpotBotCommand.class.getResourceAsStream(ALERTS_PICTURE_PATH), "Missing file " + ALERTS_PICTURE_PATH)) {
            alertsPicture = file.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load help picture file");
        }
    }

    public SpotBotCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var arguments = arguments(context);
        LOGGER.debug("{} command - user {}, server {}, arguments {}", NAME, context.userId, context.serverId(), arguments);
        context.reply(spotBot(arguments.selection, context), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        var arguments = new Arguments(context.args.getString(SELECTION_ARGUMENT).orElse(CHOICE_DOC));
        context.noMoreArgs();
        return arguments;
    }

    private List<Message> spotBot(@NotNull String selection, @NotNull CommandContext context) {
        return switch (selection) {
            case CHOICE_DOC -> List.of(doc(context));
            case CHOICE_COMMANDS -> commands();
            default -> throw new IllegalArgumentException("Invalid argument : " + selection);
        };
    }
    private static Message doc(@NotNull CommandContext context) {
        Object server = switch (context.clientType) {
            case DISCORD -> Optional.ofNullable(context.discordMember).map(Member::getGuild).orElse(null);
        };
        String selfMention = switch (context.clientType) {
            case DISCORD -> context.discord().spotBotUserMention();
        };
        EmbedBuilder builder = embedBuilder(null, OK_COLOR, formattedContent(context, server, selfMention));
        builder.addBlankField(false);
        builder.setImage("attachment://" + ALERTS_PICTURE_FILE);
        builder.setFooter(DOC_FOOTER);
        return Message.of(builder, File.of(ALERTS_PICTURE_FILE, alertsPicture));
    }

    private static String formattedContent(@NotNull CommandContext context, @Nullable Object server, @NotNull String selfMention) {
        String channel = switch (context.clientType) {
            case DISCORD -> Discord.spotBotChannel((Guild) server, context.serverSettings.spotBotChannel())
                    .map(Channel::getAsMention).orElse(MarkdownUtil.bold('#' + context.serverSettings.spotBotChannel()));
        };
        String role = switch (context.clientType) {
            case DISCORD -> Discord.spotBotRole((Guild) server, context.serverSettings.spotBotRole())
                    .map(Role::getAsMention).orElse(MarkdownUtil.bold('@' + context.serverSettings.spotBotRole()));
        };
        String adminRole = switch (context.clientType) {
            case DISCORD -> Discord.spotBotRole((Guild) server, context.serverSettings.spotBotAdminRole())
                    .map(Role::getAsMention).orElse(MarkdownUtil.bold('@' + context.serverSettings.spotBotAdminRole()));
        };
        var doc = DOC_CONTENT.replace("{check-period}", "" + context.parameters().checkPeriodMin())
                .replace("{date-format}", Dates.LocalePatterns.getOrDefault(context.locale, Dates.DATE_TIME_FORMAT))
                .replace("{cmd-line-date-format}", Dates.LocalePatterns.getOrDefault(context.locale, Dates.DATE_TIME_FORMAT).replaceFirst(" ", "-").replaceFirst(" ", "-"))
                .replace("{timezone}", context.timezone.getId())
                .replace("{date-now}", Dates.format(context.locale, Dates.parse(Locale.UK, context.timezone, context.clock(), "now")).replace('-', ' '))
                .replace("{date-now+2}", Dates.format(context.locale, Dates.parse(Locale.UK, context.timezone, context.clock(), "now+2")).replace('-', ' '))
                .replace("{repeat}", "" + DEFAULT_REPEAT)
                .replace("{snooze}", "" + DEFAULT_SNOOZE_HOURS)
                .replace("{done-delay-weeks}", DONE_ALERTS_DELAY_WEEKS + (DONE_ALERTS_DELAY_WEEKS > 1 ? " weeks" : " week"))
                .replace("{channel}", channel)
                .replace("{role}", role)
                .replace("{admin-role}", adminRole);
        var endDoc = END_DOC_CONTENT.replace("{spotBot}", selfMention);
        return doc.length() + endDoc.length() <= MessageEmbed.DESCRIPTION_MAX_LENGTH ?
                doc + endDoc : doc;
    }

    private static List<Message> commands() {
        record Command(String name, String description, SlashCommandData options) {}
        var commands = SPOTBOT_COMMANDS.stream()
                .map(command -> new Command(command.name(), command.description() ,command.options()));

        // split the list because it exceeds max discord message length
        return split(5, commands)
                .map(cmdList -> Message.of(embedBuilder(null, OK_COLOR, cmdList.stream()
                        .map(cmd -> ' ' + MarkdownUtil.bold(cmd.name) + "\n\n" + MarkdownUtil.quote(cmd.description +
                                Optional.ofNullable(optionsDescription(cmd.options, true)).map("\n"::concat).orElse("")))
                        .collect(joining("\n\n\n"))))).toList();
    }
}
