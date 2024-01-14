package org.sbot.discord;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;

import java.util.List;

public interface CommandListener {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    @NotNull
    String name();
    @NotNull
    String description();

    record OptionDataNode(@NotNull OptionData optionData, @NotNull String fullDescription, @NotNull List<OptionDataNode> subCommands) {}

    @NotNull
    //TODO List<OptionDataNode> handle subCommands
    List<OptionData> options();

    default CommandData asCommandData() {
        return Commands.slash(name(), description()).addOptions(options());
    }

    void onCommand(@NotNull CommandContext context);
}
