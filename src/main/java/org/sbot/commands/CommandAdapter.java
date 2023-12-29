package org.sbot.commands;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.discord.Discord;
import org.sbot.discord.Discord.MessageSender;
import org.sbot.discord.DiscordCommand;
import org.sbot.storage.AlertStorage;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public abstract class CommandAdapter implements DiscordCommand {

    protected static final Logger LOGGER = LogManager.getLogger(CommandAdapter.class);

    protected final AlertStorage alertStorage;
    private final String name;

    protected CommandAdapter(@NotNull AlertStorage alertStorage, @NotNull String name) {
        this.alertStorage = requireNonNull(alertStorage);
        this.name = requireNonNull(name);
    }

    @Override
    public String name() {
        return name;
    }

    protected final void sendResponse(@NotNull MessageSender sender, @NotNull String message) {
        Discord.sendMessage(sender, message);
    }


    protected static MessageSender sender(SlashCommandInteractionEvent event) {
        final MessageChannel messageChannel = event.getMessageChannel();
        return new MessageSender() {

            // when replying to a SlashCommandInteractionEvent, first answer should be done
            // via event.reply(message), next ones (if needed) by event.getChannel().sendMessage().
            private MessageSender firstCall = event::reply;

            @Override
            public RestAction<?> sendMessage(@NotNull String message) {
                try {
                    return null != firstCall ? firstCall.sendMessage(message) : messageChannel.sendMessage(message);
                } finally {
                    firstCall = null;
                }
            }
        };
    }

    protected final Consumer<String> asyncErrorHandler(@NotNull MessageChannel messageChannel, @NotNull String author, long alertId) {
        requireNonNull(messageChannel);
        requireNonNull(author);
        return error -> sendResponse(messageChannel::sendMessage, author + ' ' + error + alertId);
    }
}
