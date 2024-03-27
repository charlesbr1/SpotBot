package org.sbot.services.discord;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;

import java.util.List;

import static java.util.stream.Collectors.joining;

public interface CommandListener {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    @NotNull
    String name();

    @NotNull
    String description();

    @NotNull
    SlashCommandData options();

    default boolean isSlashCommand() {
        return true;
    }

    void onCommand(@NotNull CommandContext context);

    @Nullable
    static String optionsDescription(@NotNull SlashCommandData slashCommand, boolean withSubCommandName) {
        if(!slashCommand.getSubcommands().isEmpty()) {
            return slashCommand.getSubcommands().stream()
                    .map(command -> (withSubCommandName ? "\n" + MarkdownUtil.bold(command.getName()) + " : " : "> ") +
                            MarkdownUtil.italics(command.getDescription()) +
                            (command.getOptions().isEmpty() ? "\n\n(no parameter)" : "\n\n" + optionsDescription(command.getOptions(), false)))
                    .collect(joining("\n\n"));
        } else if(!slashCommand.getOptions().isEmpty()) {
            return (withSubCommandName ? "\n" : "") + optionsDescription(slashCommand.getOptions(), true);
        }
        return null;
    }

    private static String optionsDescription(List<OptionData> options, boolean header) {
        return (header ? options.size() > 1 ? "*parameters :*\n\n" : "*parameter :*\n\n" : "") +
                options.stream()
                        .map(option -> "- " + MarkdownUtil.bold(option.getName()) + " (" +
                                MarkdownUtil.italics(option.getType().toString().toLowerCase() + (option.isRequired() ? "" : ", optional")) + ") " +
                                option.getDescription())
                        .collect(joining("\n"));
    }
}
