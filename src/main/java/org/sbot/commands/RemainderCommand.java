package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.reader.CommandContext;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.List;

public final class RemainderCommand extends CommandAdapter {

    public static final String NAME = "remainder";
    static final String DESCRIPTION = "set a remainder alert to be triggered on provided date";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "date", "the date to trigger the remainder", true),
            new OptionData(OptionType.STRING, "remainder", "a message for this alert", true));

    public RemainderCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        ZonedDateTime date = context.args.getMandatoryDateTime("date");
        String remainder = context.args.getLastArgs("remainder").orElseThrow(() -> new IllegalArgumentException("Missing argument 'remainder'"));
        LOGGER.debug("remainder command - date : {}, remainder {}", date, remainder);
        context.reply(remainder(context, date, remainder));
    }

    private EmbedBuilder remainder(@NotNull CommandContext context, @NotNull ZonedDateTime date, @NotNull String remainder) {
        //TODO
        return embedBuilder(NAME, Color.red, "NOT IMPLEMENTED YET");
    }
}