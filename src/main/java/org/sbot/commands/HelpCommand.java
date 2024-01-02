package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.sbot.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;

public final class HelpCommand extends CommandAdapter {

    public static final String NAME = "help";
    private static final String DESCRIPTION = "print this help";

    private static final String HELP_HEADER = """
            SpotBot utils for setting alerts when the price of an asset reach a box or cross a trend line.

            Use **range** or **trend** commands to set new alerts, this bot will check every hours for price change and notify when an alert occurs.
            
            Type **/** to enter a command, or type it prefixed with char **!**, example :
            *!help*, *!list alerts*, *!pair ETH/USDT*, *!delete 1*,
            *!range binance eth usdt 1800 1900 zone conso 1 reached*""";
    private static final String HELP_FOOTER = "EXPLAIN THE range and trend alerts from picture";

    private static final String ALERTS_PICTURE_FILE = "range2.png";
    private static final String ALERTS_PICTURE_PATH = '/' + ALERTS_PICTURE_FILE;

    private record Command(String name, String description, List<OptionData> options) {}

    private static final List<Command> commands = List.of(
            new Command(NAME, DESCRIPTION, emptyList()),
            new Command(ListCommand.NAME, ListCommand.DESCRIPTION, ListCommand.options),
            new Command(OwnerCommand.NAME, OwnerCommand.DESCRIPTION, OwnerCommand.options),
            new Command(PairCommand.NAME, PairCommand.DESCRIPTION, PairCommand.options),
            new Command(RangeCommand.NAME, RangeCommand.DESCRIPTION, RangeCommand.options),
            new Command(TrendCommand.NAME, TrendCommand.DESCRIPTION, TrendCommand.options),
            new Command(RepeatDelayCommand.NAME, RepeatDelayCommand.DESCRIPTION, RepeatDelayCommand.options),
            new Command(RepeatCommand.NAME, RepeatCommand.DESCRIPTION, RepeatCommand.options),
            new Command(ThresholdCommand.NAME, ThresholdCommand.DESCRIPTION, ThresholdCommand.options),
            new Command(DeleteCommand.NAME, DeleteCommand.DESCRIPTION, DeleteCommand.options),
            new Command(UpTimeCommand.NAME, UpTimeCommand.DESCRIPTION, emptyList()));

    private static final FileUpload alertsPicture = FileUpload.fromData(requireNonNull(HelpCommand.class
            .getResourceAsStream(ALERTS_PICTURE_PATH)), ALERTS_PICTURE_FILE);

    public HelpCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, emptyList());
    }

    @Override
    public void onCommand(@NotNull org.sbot.commands.reader.Command command) {
        LOGGER.debug("help command");
        command.reply(help());// TODO.addFiles(alertsPicture).queue();
    }

    private static EmbedBuilder help() {
        EmbedBuilder builder = embedBuilder(null, Color.green, HELP_HEADER);

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

    private static String optionsDescription(List<OptionData> options) {
        return options.isEmpty() ? "" :
                (options.size() > 1 ? "\n\n__parameters :__\n\n" : "\n\n__parameter :__\n\n") +
                options.stream()
                        .map(option -> "- **" + option.getName() + "** *" + option.getType().toString().toLowerCase() + "* " +
                                (option.isRequired() ? "" : "*(optional)* ") + option.getDescription())
                        .collect(Collectors.joining("\n"));
    }
}
