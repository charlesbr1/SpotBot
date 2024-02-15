package org.sbot.services.discord;

import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;

public interface InteractionListener {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    @NotNull
    String name();

    void onInteraction(@NotNull CommandContext context);
}
