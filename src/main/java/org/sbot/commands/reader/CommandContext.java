package org.sbot.commands.reader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.discord.Discord;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;

public final class CommandContext {

    public final @NotNull String name;
    public final @NotNull MessageChannel channel;
    public final @NotNull User user;
    public final @Nullable Member member;
    public final @NotNull ArgumentReader args;

    // mutable value set to null once used, slash commands needs one reply using event.reply(..) then next ones on the channel...
    private @Nullable SlashCommandInteractionEvent event;
    private final @Nullable Message message;

    public CommandContext(@NotNull SlashCommandInteractionEvent event) {
        this.event = event;
        this.message = null;
        this.name = event.getName();
        this.channel = event.getChannel();
        this.user = event.getUser();
        this.member = event.getMember();
        this.args = new SlashArgumentReader(event);
    }

    public CommandContext(@NotNull MessageReceivedEvent event) {
        this.event = null;
        this.message = event.getMessage();
        this.channel = event.getChannel();
        this.user = event.getAuthor();
        this.member = event.getMember();
        this.args = new StringArgumentReader(event.getMessage().getContentRaw().strip());
        this.name = args.getString("")
                .filter(command -> command.startsWith("!"))
                .map(command -> command.replaceFirst("!", ""))
                .orElse("");
    }

    public long getServerId() {
        return null != member ? member.getGuild().getIdLong() : PRIVATE_ALERT;
    }

    public void reply(@NotNull EmbedBuilder message) {
        reply(message, emptyList());
    }
    public void reply(@NotNull EmbedBuilder message, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        reply(List.of(message), messageSetup);
    }

    public void reply(List<EmbedBuilder> messages) {
        reply(messages, emptyList());
    }

    public void reply(List<EmbedBuilder> messages, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        if(null != event) {
            Discord.sendMessages(messages, slashReply(event::replyEmbeds, channel::sendMessageEmbeds), requireNonNull(messageSetup));
        } else {
            if(null != message) {
                messageSetup = Stream.concat( // add a 'reply to' to the message
                        Stream.of(message -> ((MessageCreateAction)message).setMessageReference(this.message)),
                        messageSetup.stream()).toList();
            }
            Discord.sendMessages(messages, channel::sendMessageEmbeds, requireNonNull(messageSetup));
        }
    }

    private <T, R> Function<T, R> slashReply(@NotNull Function<T, R> replyEmbeds, @NotNull Function<T, R> sendMessageEmbeds) {
        return message -> {
            if (null != event) { // on slash commands, event.replyEmbeds must be used to reply once
                event = null;    // then one should use channel.sendMessageEmbeds for next replies (if needed)
                return replyEmbeds.apply(message);
            }
            return sendMessageEmbeds.apply(message);
        };
    }
}
