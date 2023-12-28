package org.sbot.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.sbot.utils.ArgumentReader;
import java.util.List;

public interface DiscordCommand {

    // returns the command this listener will be notified upon discord events, like 'list' or 'delete'
    String name();
    String description();
    List<OptionData> options();



    void onEvent(ArgumentReader argumentReader, MessageReceivedEvent event);
    void onEvent(SlashCommandInteractionEvent event);
}
