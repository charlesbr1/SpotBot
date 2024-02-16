package org.sbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.SlashArgumentReader;
import org.sbot.entities.alerts.Alert.Type;

import static org.sbot.entities.alerts.Alert.Type.*;

public class AlertCommand extends CommandAdapter {

    private static final String NAME = "alert";
    static final String DESCRIPTION = "create a new alert (range, trend or remainder)";
    private static final int RESPONSE_TTL_SECONDS = 60;

    private static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData(RangeCommand.NAME, RangeCommand.DESCRIPTION).addOptions(RangeCommand.optionList),
                    new SubcommandData(TrendCommand.NAME, TrendCommand.DESCRIPTION).addOptions(TrendCommand.optionList),
                    new SubcommandData(RemainderCommand.NAME, RemainderCommand.DESCRIPTION).addOptions(RemainderCommand.optionList));

    public AlertCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        Type type = getType(context);
        LOGGER.debug("alert command - type : {}", type);
        (switch (type) {
            case range -> new RangeCommand();
            case trend -> new TrendCommand();
            case remainder -> new RemainderCommand();
        }).onCommand(context);
    }

    private Type getType(@NotNull CommandContext context) {
        if(context.args instanceof SlashArgumentReader reader) {
            return switch (reader.getSubcommandName()) {
                case RangeCommand.NAME -> range;
                case TrendCommand.NAME -> trend;
                default -> remainder;
            };
        }
        return Type.valueOf(context.args.getMandatoryString("type"));
    }
}
