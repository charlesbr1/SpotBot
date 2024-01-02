package org.sbot.commands.reader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.sbot.alerts.Alert.PRIVATE_ALERT;

public final class Command {

    private static final Logger LOGGER = LogManager.getLogger(Command.class);

    public final @NotNull String name;
    public final @NotNull MessageChannel channel;
    public final @NotNull User user;
    public final @Nullable Member member;
    public final @NotNull ArgumentReader args;

    public @Nullable SlashCommandInteractionEvent event;

    public Command(@NotNull SlashCommandInteractionEvent event) {
        this.event = event;
        this.name = event.getName();
        this.channel = event.getChannel();
        this.user = event.getUser();
        this.member = event.getMember();
        this.args = new SlashArgumentReader(event);
    }

    public Command(@NotNull MessageReceivedEvent event) {
        this.event = null;
        this.channel = event.getChannel();
        this.user = event.getAuthor();
        this.member = event.getMember();
        this.args = new StringArgumentReader(event.getMessage().getContentRaw().strip());
        this.name = args.getString("")
                .filter(command -> command.startsWith("!"))
                .map(command -> command.replaceFirst("!", ""))
                .orElse("\0");
    }

    public long getServerId() {
        return null != member ? member.getGuild().getIdLong() : PRIVATE_ALERT;
    }

    public void reply(EmbedBuilder message) {
        LOGGER.debug("Reply : {}", message.getDescriptionBuilder());
        if(null != event) {
            event.replyEmbeds(message.build()).queue();
            event = null;
        } else {
            channel.sendMessageEmbeds(message.build()).queue();
        }
    }
}
