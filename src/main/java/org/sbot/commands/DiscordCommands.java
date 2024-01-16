package org.sbot.commands;

import org.sbot.discord.CommandListener;

import java.util.List;

public interface DiscordCommands {

    // Register new commands here
    List<CommandListener> DISCORD_COMMANDS = List.of(
            new SpotBotCommand(),
            new RangeCommand(),
            new TrendCommand(),
            new RemainderCommand(),
            new DeleteCommand(),
            new ListCommand(),
            new OwnerCommand(),
            new PairCommand(),
            new RepeatCommand(),
            new SnoozeCommand(),
            new MarginCommand(),
            new MessageCommand(),
            new MigrateCommand(),
            new QuoteCommand(),
            new UtcCommand(),
            new UpTimeCommand());
}
