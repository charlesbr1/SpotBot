package org.sbot.commands;

import org.sbot.services.discord.CommandListener;

import java.util.List;

public interface Commands {

    // Register new commands here
    List<CommandListener> SPOTBOT_COMMANDS = List.of(
            new SpotBotCommand(),
            new ListCommand(),
            new RangeCommand(),
            new TrendCommand(),
            new RemainderCommand(),
            new UpdateCommand(),
            new MigrateCommand(),
            new DeleteCommand(),
            new QuoteCommand(),
            new UtcCommand(),
            new UpTimeCommand());
}
