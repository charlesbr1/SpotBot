package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.commands.reader.Command;
import org.sbot.storage.AlertStorage;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;

public final class ListCommand extends CommandAdapter {

    public static final String NAME = "list";
    static final String DESCRIPTION = "list the supported exchanges, or pairs, or the alerts currently set";

    static final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, "value", "the data to list, one of 'exchanges', 'pair', or 'alerts'", true)
                    .addChoice("exchanges", "exchanges")
                    .addChoice("pair", "pair")
                    .addChoice("alerts", "alerts"));

    public ListCommand(@NotNull AlertStorage alertStorage) {
        super(alertStorage, NAME, DESCRIPTION, options);
    }

    @Override
    public void onCommand(@NotNull Command command) {
        LOGGER.debug("list command");
        String value = command.args.getMandatoryString("value");
        command.reply(list(value));
    }

    private List<EmbedBuilder> list(@NotNull String value) {
        return switch (value) {
            case "pair" -> {
                var res = new ArrayList<EmbedBuilder>();
                String vall = "1234567890".repeat(30);
                Color color = Color.lightGray;
                for(int i = 0; i < 233; i++) {
                    color = color.darker();
                    res.add(embedBuilder("test" + i, color, i + vall));
                }
                yield res;
            }
            case "alerts" -> { // TODO list alert on channel or exchange
                String messages = alertStorage.getAlerts().map(Alert::toString).collect(Collectors.joining(""));
                yield List.of(embedBuilder(NAME, Color.green, String.join(messages.isEmpty() ? "No record found" : messages)));
            }
            case "exchanges" -> List.of(embedBuilder(NAME, Color.green, String.join("\n", SUPPORTED_EXCHANGES)));
            default -> throw new IllegalArgumentException("Unexpected value : " + value);
        };
    }
}
