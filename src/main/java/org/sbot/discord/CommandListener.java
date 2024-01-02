package org.sbot.discord;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.Command;

import java.util.List;

public interface CommandListener {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    @NotNull
    String name();
    @NotNull
    String description();
    @NotNull
    List<OptionData> options();


    void onCommand(@NotNull Command command);
}
