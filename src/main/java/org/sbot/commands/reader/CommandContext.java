package org.sbot.commands.reader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.discord.Discord;
import org.sbot.services.dao.AlertsDao;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.discord.Discord.MAX_MESSAGE_EMBEDS;

public final class CommandContext {

    public final @NotNull Discord discord;
    public final @NotNull AlertsDao alertsDao;

    public final @NotNull String name;
    public final @NotNull MessageChannel channel;
    public final @NotNull User user;
    public final @Nullable Member member;
    public final @NotNull ArgumentReader args;

    // mutable value set to null once used, slash commands needs one reply using event.reply(..) then next ones on the channel...
    private @Nullable SlashCommandInteractionEvent event;
    private final @Nullable Message message;

    public CommandContext(@NotNull Discord discord, @NotNull AlertsDao alertsDao, @NotNull SlashCommandInteractionEvent event) {
        this.discord = requireNonNull(discord);
        this.alertsDao = requireNonNull(alertsDao);
        this.event = event;
        this.message = null;
        this.channel = event.getChannel();
        this.user = event.getUser();
        this.member = event.getMember();
        this.args = new SlashArgumentReader(event);
        this.name = event.getName();
    }

    public CommandContext(@NotNull Discord discord, @NotNull AlertsDao alertsDao, @NotNull MessageReceivedEvent event, @NotNull String command) {
        this.discord = requireNonNull(discord);
        this.alertsDao = requireNonNull(alertsDao);
        this.event = null;
        this.message = event.getMessage();
        this.channel = event.getChannel();
        this.user = event.getAuthor();
        this.member = event.getMember();
        this.args = new StringArgumentReader(command);
        this.name = args.getString("").orElse("");
    }

    public long serverId() {
        return null != member ? member.getGuild().getIdLong() : PRIVATE_ALERT;
    }

    public CommandContext noMoreArgs() {
        if(args.getLastArgs("").filter(not(String::isBlank)).isPresent()) {
            throw new IllegalArgumentException("Too many arguments provided");
        }
        return this;
    }

    public void reply(int ttlSeconds, @NotNull EmbedBuilder message) {
        reply(ttlSeconds, message, emptyList());
    }
    public void reply(int ttlSeconds, @NotNull EmbedBuilder message, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        reply(ttlSeconds, List.of(message), messageSetup);
    }

    public void reply(int ttlSeconds, List<EmbedBuilder> messages) {
        reply(ttlSeconds, messages, emptyList());
    }

    public void reply(int ttlSeconds, List<EmbedBuilder> messages, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        if(null != event) { // slash commands, allows to make a reply only visible to the user, it only works for 1 message reply (with max 10 embeds)
            Discord.sendMessages(ttlSeconds, messages, slashReply(event::replyEmbeds, channel::sendMessageEmbeds),
                    messages.size() <= MAX_MESSAGE_EMBEDS ? setSlashEphemeralReply(messageSetup) : messageSetup);
        } else { // classic string commands, add a 'reply to' to the message
            Discord.sendMessages(ttlSeconds, messages, channel::sendMessageEmbeds,
                    null != message ? setupMessageReplyTo(messageSetup) : messageSetup);
        }
    }

    @NotNull
    private List<Consumer<MessageCreateRequest<?>>> setSlashEphemeralReply(@NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        return Stream.concat(Stream.of(message -> {
                    if (message instanceof ReplyCallbackAction) {
                        ((ReplyCallbackAction) message).setEphemeral(true);
                    }
                }),
                messageSetup.stream()).toList();
    }

    @NotNull
    private List<Consumer<MessageCreateRequest<?>>> setupMessageReplyTo(@NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        return Stream.concat(
                Stream.of(message -> ((MessageCreateAction) message).setMessageReference(this.message)),
                messageSetup.stream()).toList();
    }

    private <T, R> Function<T, R> slashReply(@NotNull Function<T, R> replyEmbeds, @NotNull Function<T, R> sendMessageEmbeds) {
        return message -> {      // on slash commands, event::replyEmbeds must be used to reply once
            if (null != event) { // then one should use channel::sendMessageEmbeds for next replies (if there is many messages as a reply)
                event = null;    // as those replies are queued by the sender thread, 'event' does not need to be a volatile here
                return replyEmbeds.apply(message);
            }
            return sendMessageEmbeds.apply(message);
        };
    }
}
