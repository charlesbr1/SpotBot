package org.sbot.discord;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;

public interface CommandListener {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    @NotNull
    String name();

    @NotNull
    SlashCommandData options();

    void onCommand(@NotNull CommandContext context);
}
