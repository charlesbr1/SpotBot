package org.sbot.commands.reader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.*;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.discord.Discord.asyncOrdered;

public final class CommandContext {

    private static final Logger LOGGER = LogManager.getLogger(CommandContext.class);

    private static final int MAX_MESSAGE_EMBEDS = 10;

    public final @NotNull String name;
    public final @NotNull MessageChannel channel;
    public final @NotNull User user;
    public final @Nullable Member member;
    public final @NotNull ArgumentReader args;

    // mutable value set to null once used
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


    @SafeVarargs
    public final void reply(@NotNull EmbedBuilder message, Consumer<MessageCreateRequest<?>>... options) {
        toRestAction(List.of(message), options).queue();
    }

    @SafeVarargs
    public final void reply(List<EmbedBuilder> messages, Consumer<MessageCreateRequest<?>>... options) {
        // Discord limit to 10 embeds by message
        asyncOrdered(IntStream.range(0, messages.size()).boxed()
                .collect(groupingBy(index -> index / MAX_MESSAGE_EMBEDS, // split this stream in lists of 10 messages
                        mapping(messages::get, toList())))
                .entrySet().stream().sorted(comparingByKey())
                .map(entry -> toRestAction(entry.getValue(), options)));
    }

    @SafeVarargs
    private RestAction<?> toRestAction(List<EmbedBuilder> messages, Consumer<MessageCreateRequest<?>>... settings) {
        List<MessageEmbed> embeds = messages.stream()
                .peek(message -> LOGGER.debug("Reply : {}", message.getDescriptionBuilder()))
                .map(EmbedBuilder::build)
                .toList(); // list of 10 messages

        var messageRequest = null != event ? event.replyEmbeds(embeds) :
                channel.sendMessageEmbeds(embeds).setMessageReference(this.message);
        event = null; // on slash commands, event.replyEmbeds must be used to answer and the first time only

        Optional.ofNullable(settings).stream().flatMap(Stream::of)
                .filter(Objects::nonNull)
                .forEach(setting -> setting.accept(messageRequest));
        return messageRequest;
    }
}
