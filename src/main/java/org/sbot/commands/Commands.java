package org.sbot.commands;

import org.sbot.services.discord.CommandListener;

import java.util.List;

public interface Commands {

    String INTERACTION_ID_SEPARATOR = "#";

    // Register new commands here
    List<CommandListener> SPOTBOT_COMMANDS = List.of(
            new SpotBotCommand(),
            new AlertCommand(),
            new ListCommand(),
            new UpdateCommand(),
            new DeleteCommand(),
            new RangeCommand(),
            new TrendCommand(),
            new RemainderCommand(),
            new MigrateCommand(),
            new QuoteCommand(),
            new UpTimeCommand());
}
