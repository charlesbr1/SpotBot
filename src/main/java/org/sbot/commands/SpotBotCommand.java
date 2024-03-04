package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static org.sbot.commands.Commands.SPOTBOT_COMMANDS;
import static org.sbot.entities.alerts.Alert.DEFAULT_REPEAT;
import static org.sbot.entities.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.services.discord.Discord.*;
import static org.sbot.utils.Dates.nowUtc;
import static org.sbot.utils.PartitionSpliterator.split;

public final class SpotBotCommand extends CommandAdapter {

    private static final String NAME = "spotbot";
    private static final String DESCRIPTION = "show the general documentation or a description of each commands";
    private static final int RESPONSE_TTL_SECONDS = 600;

    private static final String CHOICE_DOC = "doc";
    private static final String CHOICE_COMMANDS = "commands";

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addOptions(
                    option(STRING, SELECTION_ARGUMENT, "one of 'doc' or 'commands' or 'examples', default to doc if omitted", false)
                            .addChoices(List.of(new Choice(CHOICE_DOC, CHOICE_DOC),
                                    new Choice(CHOICE_COMMANDS, CHOICE_COMMANDS))));

    private record Arguments(String selection) {}

    private static final String DOC_FOOTER = "EXPLAIN THE range and trend alerts from picture";

    private static final String ALERTS_PICTURE_FILE = "range2.png";
    private static final String ALERTS_PICTURE_PATH = '/' + ALERTS_PICTURE_FILE;

    private static final String DOC_HEADER = """
            SpotBot is a discord utility for setting alerts when the price of an asset reach a box or cross a trend line, or just for a remainder in the future.
            
            An asset is a pair of two tickers, like ETH/USDT, ADA/BTC, EUR/USD, etc.
            
            **range alert**

            A range alert is defined by two prices, with some optionally starting and ending dates (during which the alert is active), thus forming a box. It will raise when the price enter this box.

            **trend alert**

            A trend is a line defined by two points on a price chart, each having a price and a date as coordinates. It will raise when the price cross this line.
            
            **remainder**
            
            This third kind of alert let you receive a notification at a specified date in the future, with a message you can prepare in advance. This allows to you to set a remainder for events you don't want to miss.


            > Use **range**, **trend**, or **remainder** commands to set new alerts, this bot will check every hours for price change then you'll get notified when your asset reach your price !
            
            
            A price can use a comma or a dot separator and is expected to be positive : 23.6 or 38,2 are ok.
            
            **Any date should be provided as UTC** date and time. Discord can't provide your time zone so you have to think about it !

            The expected date time format is :``` {date-format}```
            For instance now it's {date-now} UTC

            **margin**

            Both ranges and trend alerts can have a *margin*, this is a value in currency of the watched asset, like 1000$ on the BTC/USD pair.

            The margin is added on both sides of a range or a trend, as to extend them, then a pre alert will notify you when the price reach this zone (if it ever happens).

            Initially a new alert has no margin set, it should be added using the **margin** command.

            Once raised (either a margin pre alert or an alert) the alert's margin is reset, that is no more margin.
            
            **snooze**

            Range and margin alerts also have two parameters, *repeat* and *snooze*, that can be set using the commands of their respective names.
            * **repeat** : the number of times an alert will be raise (default : {repeat})
            * **snooze** : the time in hours to wait before the alert can be raise again (default : {snooze} hours)

            Once an alert is raised, it decreases in number of *repeat* and becomes ignored during *snooze* hours.
            If it's number of repeat reach 0, the alert is disabled and will be deleted one month later, this let you time to enable it again, if never.
            A remainder alert is always deleted after it has been raised, as there is no margin or repeat for them.

            
            For all theses alerts, including remainders, the accuracy of updates is hourly, so do not expect a notification in the micro second after the event occurred.
            
            The others commands let you do some searches about current alerts defined, as well as updating or deleting them.
            
            This bot works exclusively into the channel {channel}, you can also use it from your private channel. When an alert occurs, the owner is notified on the channel where he created it.
            
            **The alerts you set using your private channel remains confidential.**

            You may also want to join role {role} to get notified of each alert occurring on the channel {channel}.

            Now type **/** to use a command, or type it prefixed with my name {spotBot}
            
            For a description of each available commands use :
            ``` /spotbot commands```
            To get some example usages of these commands, use :
            ``` /spotbot examples```
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
        LOGGER.debug("{} command - user {}, server {}, arguments {}", NAME, context.user.getIdLong(), context.serverId(), arguments);
        context.noMoreArgs().reply(spotBot(arguments.selection, context), responseTtlSeconds);
    }

    static Arguments arguments(@NotNull CommandContext context) {
        return new Arguments(context.args.getString(SELECTION_ARGUMENT).orElse(CHOICE_DOC));
    }

    private List<Message> spotBot(@NotNull String selection, @NotNull CommandContext context) {
        return switch (selection) {
            case CHOICE_DOC -> List.of(doc(context));
            case CHOICE_COMMANDS -> commands();
            default -> throw new IllegalArgumentException("Invalid argument : " + selection);
        };
    }
    private static Message doc(@NotNull CommandContext context) {
        Guild guild = Optional.ofNullable(context.member).map(Member::getGuild).orElse(null);
        String selfMention = context.discord().spotBotUserMention();
        EmbedBuilder builder = embedBuilder(null, OK_COLOR, formattedHeader(context, guild, selfMention));
        builder.addBlankField(false);
        builder.setImage("attachment://" + ALERTS_PICTURE_FILE);
        builder.setFooter(DOC_FOOTER);
        return Message.of(builder, File.of(alertsPicture, ALERTS_PICTURE_FILE));
    }

    private static String formattedHeader(@NotNull CommandContext context, @Nullable Guild guild, @NotNull String selfMention) {
        String channel = Optional.ofNullable(guild).flatMap(Discord::getSpotBotChannel)
                .map(Channel::getAsMention).orElse("**#" + DISCORD_BOT_CHANNEL + "** of your discord server");
        String role = Optional.ofNullable(guild).flatMap(Discord::spotBotRole)
                .map(Role::getAsMention).orElse("**@" + DISCORD_BOT_ROLE + "**");
        return DOC_HEADER.replace("{date-format}", Dates.DATE_TIME_FORMAT)
                .replace("{date-now}", Dates.formatUTC(context.locale, nowUtc(context.clock())))
                .replace("{repeat}", ""+DEFAULT_REPEAT)
                .replace("{snooze}", "" + DEFAULT_SNOOZE_HOURS)
                .replace("{channel}", channel).replace("{role}", role)
                .replace("{spotBot}", selfMention);
    }

    private static String commandDescription(SlashCommandData commandData) {
        if(!commandData.getSubcommands().isEmpty()) {
            return commandData.getSubcommands().stream()
                    .map(command -> "\n\n**" + command.getName() + "** : *" + command.getDescription() + "*\n\n" + optionsDescription(command.getOptions(), false))
                    .collect(joining("\n"));
        } else if(!commandData.getOptions().isEmpty()) {
            return optionsDescription(commandData.getOptions(), true);
        }
        return "";
    }

    private static String optionsDescription(List<OptionData> options, boolean header) {
        return (header ? options.size() > 1 ? "\n\n*parameters :*\n\n" : "\n\n*parameter :*\n\n" : "") +
                options.stream()
                        .map(option -> "- **" + option.getName() + "** *" + option.getType().toString().toLowerCase() + "* " +
                                (option.isRequired() ? "" : "*(optional)* ") + option.getDescription())
                        .collect(joining("\n"));
    }

    private static List<Message> commands() {
        record Command(String name, String description, SlashCommandData options) {}
        var commands = SPOTBOT_COMMANDS.stream()
                .map(command -> new Command(command.name(), command.description() ,command.options()));

        // split the list because it exceeds max discord message length
        return split(5, commands)
                .map(cmdList -> Message.of(embedBuilder(null, OK_COLOR, cmdList.stream()
                        .map(cmd -> "** " + cmd.name + "**\n\n" + MarkdownUtil.quote(cmd.description + commandDescription(cmd.options)))
                        .collect(joining("\n\n\n"))))).toList();
    }
}
