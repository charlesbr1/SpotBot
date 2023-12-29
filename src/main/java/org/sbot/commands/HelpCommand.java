package org.sbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.storage.AlertStorage;
import org.sbot.utils.ArgumentReader;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.sbot.discord.Discord.MESSAGE_SECTION_DELIMITER;

public final class HelpCommand extends CommandAdapter {

    public static final String NAME = "help";
    private static final String DESCRIPTION = "print this help";

    private static final String HELP_HEADER = "SpotBot utils for setting alerts when the price of an asset reach a box or cross a trend line.\n" +
            "Use 'range' or 'trend' commands to set new alerts, this bot will check every hours for price change and notify when an alert occurs.";
    private static final String HELP_FOOTER = "Use '/' to enter a command or type it directly prefixed with char '!'," +
            " like '!help' or '!list alerts' or '!range binance eth usdt 1800 1900 zone conso 1 reached";

    private record Command(String name, String description, List<OptionData> options) {}

    private static final List<Command> commands = List.of(
            new Command(NAME, DESCRIPTION, emptyList()),
            new Command(DelayCommand.NAME, DelayCommand.DESCRIPTION, DelayCommand.options),
            new Command(DeleteCommand.NAME, DeleteCommand.DESCRIPTION, DeleteCommand.options),
            new Command(ListCommand.NAME, ListCommand.DESCRIPTION, ListCommand.options),
            new Command(OccurrenceCommand.NAME, OccurrenceCommand.DESCRIPTION, OccurrenceCommand.options),
            new Command(OwnerCommand.NAME, OwnerCommand.DESCRIPTION, OwnerCommand.options),
            new Command(PairCommand.NAME, PairCommand.DESCRIPTION, PairCommand.options),
            new Command(RangeCommand.NAME, RangeCommand.DESCRIPTION, RangeCommand.options),
            new Command(ThresholdCommand.NAME, ThresholdCommand.DESCRIPTION, ThresholdCommand.options),
            new Command(TrendCommand.NAME, TrendCommand.DESCRIPTION, TrendCommand.options),
            new Command(UpTimeCommand.NAME, UpTimeCommand.DESCRIPTION, emptyList()));
    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<OptionData> options() {
        return emptyList();
    }

    public HelpCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME);
    }

    @Override
    public void onEvent(@NotNull ArgumentReader argumentReader, @NotNull MessageReceivedEvent event) {
        LOGGER.debug("help command: {}", event.getMessage().getContentRaw());
        help(event.getChannel()::sendMessage);
    }

    @Override
    public void onEvent(@NotNull SlashCommandInteractionEvent event) {
        LOGGER.debug("help slash command: {}", event.getOptions());
        help(sender(event));
    }

    private void help(@NotNull MessageSender sender) {
        sendResponse(sender, HELP_HEADER + MESSAGE_SECTION_DELIMITER + collectCommandsDescription() +
                MESSAGE_SECTION_DELIMITER + HELP_FOOTER);
    }

    @NotNull
    private static String collectCommandsDescription() {
        return commands.stream().map(command -> {
            String description = formatLength(8, command.name + " : ") + command.description;
            if(!command.options.isEmpty()) {
                description += (command.options.size() > 1 ? "\n\tparameters :\n" : "\n\tparameter :\n") +
                        command.options.stream()
                        .map(option -> "\t\t" + formatLength(12, option.getName() + ' ') + formatLength(8, option.getType().toString().toLowerCase()) +
                                (option.isRequired() ? "" : "(optional) ") + option.getDescription())
                        .collect(Collectors.joining("\n"));
            }
            return description;
        }).collect(Collectors.joining(MESSAGE_SECTION_DELIMITER));
    }

    @NotNull
    private static String formatLength(int length, @NotNull String value) {
        return value + " ".repeat(Math.max(0, length - value.length()));
    }
}
