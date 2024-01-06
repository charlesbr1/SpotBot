package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.discord.Discord;
import org.sbot.services.Alerts;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.sbot.discord.Discord.*;

public final class SpotBotCommand extends CommandAdapter {

    public static final String NAME = "spotbot";
    private static final String DESCRIPTION = "about this bot";

    private static final String HELP_HEADER = """
            SpotBot is a tools for setting alertsWatcher when the price of an asset reach a box or cross a trend line.
            
            An asset is a pair of two tickers, like ETH/USDT, ADA/BTC, EUR/USD, etc.
            
            A box is a range defined by two price.

            A trend is a line defined by two points on a graph price, each having a price and a date as coordinates.

            Use **range** or **trend** commands to set new alertsWatcher, this bot will check every hours for price change then notify you when an alert occurs.
            
            The others commands let you do some searches about current alertsWatcher sets, as well as updating or deleting them.
            
            This bot works exclusively on channel {channel}, you can also use it from your private channel.
            
            When an alertsWatcher occurs, the owner that set it is notified in the #spot channel, or in private channel if he created it here.
            
            You may also want to join role {role} to get notified of each alert occurring on the channel {channel}
            
            The alertsWatcher you set using your private channel remains privates and are not visible by the others.

            
            Type **/** to enter a command, or type it prefixed with char **!**

            examples :

            * *!list alertsWatcher*
            * *!owner @someone*
            * *!pair ETH/USDT*
            * *!range binance eth usdt 1800 1900 zone conso 1900 reached, see https://discord.com/channels...*
            * *!trend binance eth usdt 1800 10/03/2019-12:30 1900 03/11/2021-16:00 daily uptrend tested, see https://discord.com/channels...*
            * *!repeat 123 5*
            * *!repeat-delay 123 24*
            * *!margin 123 3*
            * *!delete 123*
            * *!remainder 10/03/2019-12:30 A message to receive at this date*""";

    private static final String HELP_FOOTER = "EXPLAIN THE range and trend alertsWatcher from picture";

    private static final String ALERTS_PICTURE_FILE = "range2.png";
    private static final String ALERTS_PICTURE_PATH = '/' + ALERTS_PICTURE_FILE;

    private record Command(String name, String description, List<OptionData> options) {}

    private static final List<Command> commands = List.of(
            new Command(ListCommand.NAME, ListCommand.DESCRIPTION, ListCommand.options),
            new Command(OwnerCommand.NAME, OwnerCommand.DESCRIPTION, OwnerCommand.options),
            new Command(PairCommand.NAME, PairCommand.DESCRIPTION, PairCommand.options),
            new Command(RangeCommand.NAME, RangeCommand.DESCRIPTION, RangeCommand.options),
            new Command(TrendCommand.NAME, TrendCommand.DESCRIPTION, TrendCommand.options),
            new Command(RepeatCommand.NAME, RepeatCommand.DESCRIPTION, RepeatCommand.options),
            new Command(RepeatDelayCommand.NAME, RepeatDelayCommand.DESCRIPTION, RepeatDelayCommand.options),
            new Command(MarginCommand.NAME, MarginCommand.DESCRIPTION, MarginCommand.options),
            new Command(MessageCommand.NAME, MessageCommand.DESCRIPTION, MessageCommand.options),
            new Command(DeleteCommand.NAME, DeleteCommand.DESCRIPTION, DeleteCommand.options),
            new Command(RemainderCommand.NAME, RemainderCommand.DESCRIPTION, RemainderCommand.options),
            new Command(UpTimeCommand.NAME, UpTimeCommand.DESCRIPTION, emptyList()));

    private static final FileUpload alertsPicture = FileUpload.fromData(requireNonNull(SpotBotCommand.class
            .getResourceAsStream(ALERTS_PICTURE_PATH)), ALERTS_PICTURE_FILE);

    public SpotBotCommand(@NotNull Alerts alerts) {
        super(alerts, NAME, DESCRIPTION, emptyList());
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        LOGGER.debug("help command");
        context.reply(help(Optional.ofNullable(context.member).map(Member::getGuild).orElse(null)),
                List.of(message -> message.addFiles(alertsPicture)));
    }

    private static EmbedBuilder help(@Nullable Guild guild) {
        EmbedBuilder builder = embedBuilder(null, Color.green, formattedHeader(guild));

        commands.forEach(command -> {
            builder.addBlankField(false);
            String description = SINGLE_LINE_BLOCK_QUOTE_MARKDOWN +
                    command.description + optionsDescription(command.options);
            builder.addField(command.name, description, false);
        });
        builder.addBlankField(false);
        builder.setImage("attachment://" + ALERTS_PICTURE_FILE);
        builder.setFooter(HELP_FOOTER);
        return builder;
    }

    private static String formattedHeader(@Nullable Guild guild) {
        String channel = Optional.ofNullable(guild).flatMap(Discord::getSpotBotChannel)
                .map(Channel::getAsMention).orElse('#' + DISCORD_BOT_CHANNEL);
        String role = Optional.ofNullable(guild).flatMap(Discord::spotBotRole)
                .map(Role::getAsMention).orElse('@' + DISCORD_BOT_ROLE);
        return HELP_HEADER.replace("{channel}", channel).replace("{role}", role);
    }

    private static String optionsDescription(List<OptionData> options) {
        return options.isEmpty() ? "" :
                (options.size() > 1 ? "\n\nparameters :\n\n" : "\n\nparameter :\n\n") +
                options.stream()
                        .map(option -> "- **" + option.getName() + "** *" + option.getType().toString().toLowerCase() + "* " +
                                (option.isRequired() ? "" : "*(optional)* ") + option.getDescription())
                        .collect(Collectors.joining("\n"));
    }
}
